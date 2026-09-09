import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from benchmark_comparison import (
    decompression_table_lines,
    metric_from_entry,
    metric_summary,
    read_metadata,
    resolve_commit,
    select_report_dir,
)


class BenchmarkComparisonTest(unittest.TestCase):
    def test_latest_publication_requires_complete_results(self):
        script = Path(__file__).with_name("benchmark_comparison.py").resolve()
        archive = script.parent.parent / "performance/pr30-linux-e5992df"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            command = [
                sys.executable, str(script), "--run-dir", str(archive / "raw"),
                "--metadata", str(archive / "benchmark-metadata.jsonl"),
                "--output", str(root / "comparison.md"), "--publish-latest",
                "--benchmark-commit", "e5992dfce45cac68322edfa581807c82ee9373a1",
                "--run-id", "2026-09-06T20.57.38.873936619",
            ]
            result = subprocess.run(command, cwd=root, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            latest = root / "performance/latest.json"
            published = latest.read_bytes()
            data = json.loads(published)
            self.assertEqual(21, len(data["compression"]))
            self.assertEqual(42, len(data["decompression"]))
            self.assertEqual("e5992dfce45cac68322edfa581807c82ee9373a1", data["benchmarkCommit"])
            self.assertEqual("2026-09-06T20.57.38.873936619", data["runId"])
            head = subprocess.check_output(
                ["git", "-C", str(script.parent.parent), "rev-parse", "HEAD"], text=True,
            ).strip()
            abbreviated = command.copy()
            abbreviated[abbreviated.index("--benchmark-commit") + 1] = head[:12]
            result = subprocess.run(abbreviated, cwd=root, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(head, json.loads(latest.read_text())["benchmarkCommit"])
            published = latest.read_bytes()
            for option, value in (("--benchmark-commit", "not-a-commit"), ("--run-id", "raw")):
                invalid = command.copy()
                invalid[invalid.index(option) + 1] = value
                with self.subTest(option=option):
                    result = subprocess.run(invalid, cwd=root, capture_output=True, text=True)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(published, latest.read_bytes())
            for flag in ("--allow-partial", "--allow-missing-sizes"):
                with self.subTest(flag=flag):
                    result = subprocess.run(command + [flag], cwd=root, capture_output=True, text=True)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(published, latest.read_bytes())

    def test_single_library_report_requires_complete_selected_matrix(self):
        script = Path(__file__).with_name("benchmark_comparison.py").resolve()
        archive = script.parent.parent / "performance/pr30-linux-e5992df"
        for library, benchmark_class in (("kflate", "CompressionBenchmarks"), ("kompress", "KompressBaselineBenchmarks")):
            with self.subTest(library=library), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                reports = root / "raw"
                reports.mkdir()
                for source in (archive / "raw").glob("*Benchmark.json"):
                    entries = [entry for entry in json.loads(source.read_text())
                               if f".{benchmark_class}." in entry["benchmark"]]
                    (reports / source.name).write_text(json.dumps(entries))
                metadata = root / "metadata.jsonl"
                metadata.write_text("\n".join(line for line in (archive / "benchmark-metadata.jsonl").read_text().splitlines()
                                               if json.loads(line)["library"].lower() == library))
                command = [sys.executable, str(script), "--run-dir", str(reports),
                           "--metadata", str(metadata), "--library", library,
                           "--output", str(root / "comparison.md")]
                result = subprocess.run(command, cwd=root, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                data = json.loads((root / "comparison.json").read_text())
                other = "kompress" if library == "kflate" else "kflate"
                self.assertTrue(all(row[library] is not None and row[other] is None for row in data["compression"]))
                self.assertFalse((root / "performance/latest.json").exists())
                result = subprocess.run(command + ["--publish-latest"], cwd=root, capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                report = reports / "jvmBenchmark.json"
                entries = json.loads(report.read_text())
                report.write_text(json.dumps(entries[1:]))
                result = subprocess.run(command, cwd=root, capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("missing", result.stderr)

    def test_archived_full_commit_does_not_require_local_history(self):
        from unittest.mock import patch
        commit = "a" * 40
        with patch("benchmark_comparison.subprocess.check_output") as git:
            self.assertEqual(commit, resolve_commit(commit))
        git.assert_not_called()

    def test_invalid_scores_are_rejected(self):
        for score in (None, True, "0.1", 0, -1, float("nan"), float("inf"), -float("inf"), 1e308):
            with self.subTest(score=score), self.assertRaisesRegex(SystemExit, "Invalid benchmark score"):
                metric_from_entry({"primaryMetric": {
                    "score": score, "scoreUnit": "s/op", "rawData": [[0.01]],
                }})

    def test_invalid_samples_are_not_silently_dropped(self):
        for sample in (None, True, "0.1", 0, -1, float("nan"), float("inf"), -float("inf"), 1e308):
            with self.subTest(sample=sample), self.assertRaisesRegex(
                SystemExit, "Invalid benchmark sample.*example.*fork 1, sample 1"
            ):
                metric_from_entry({"benchmark": "example", "primaryMetric": {
                    "score": 0.01, "scoreUnit": "s/op", "rawData": [[0.01], [0.01, sample]],
                }})

    def test_missing_or_malformed_forks_are_rejected(self):
        for samples in (None, [], [[]], [[0.01], []], [None], [0.01], "samples"):
            with self.subTest(samples=samples), self.assertRaisesRegex(SystemExit, "Missing benchmark samples"):
                metric_from_entry({"primaryMetric": {
                    "score": 0.01, "scoreUnit": "s/op", "rawData": samples,
                }})

    def test_metric_preserves_uncertainty_and_raw_samples(self):
        metric = metric_from_entry(
            {
                "primaryMetric": {
                    "score": 0.012,
                    "scoreUnit": "s/op",
                    "scoreError": 0.001,
                    "scoreConfidence": [0.010, 0.014],
                    "scorePercentiles": {"50.0": 0.011, "95.0": 0.013},
                    "rawData": [[0.010, 0.012], [0.011, 0.013]],
                }
            }
        )

        self.assertEqual(12.0, metric["averageMs"])
        self.assertEqual("s/op", metric["sourceUnit"])
        self.assertEqual(1.0, metric["errorMs"])
        self.assertEqual([10.0, 14.0], metric["confidenceIntervalMs"])
        self.assertEqual(11.0, metric["p50Ms"])
        self.assertEqual(13.0, metric["p95Ms"])
        self.assertEqual(2, metric["forkCount"])
        self.assertEqual(4, metric["sampleCount"])
        self.assertEqual([[10.0, 12.0], [11.0, 13.0]], metric["rawDataMs"])

    def test_metric_calculates_missing_percentiles_from_raw_samples(self):
        metric = metric_from_entry(
            {
                "primaryMetric": {
                    "score": 0.0025,
                    "scoreUnit": "sec/op",
                    "scoreError": 0.001,
                    "scoreConfidence": [0.001, 0.004],
                    "rawData": [[0.001, 0.002, 0.003, 0.004]],
                }
            }
        )

        self.assertEqual(2.5, metric["p50Ms"])
        self.assertAlmostEqual(3.85, metric["p95Ms"])

    def test_summary_keeps_stream_producers_separate(self):
        sample = {
            "sourceUnit": "s/op",
            "averageMs": 1.0,
            "errorMs": 0.1,
            "confidenceIntervalMs": [0.8, 1.2],
            "p50Ms": 0.9,
            "p95Ms": 1.1,
            "forkCount": 3,
            "sampleCount": 45,
            "rawDataMs": [[1.0]],
        }
        metrics = {}
        for operation in (
            "rawDeflateCompression",
            "rawDeflateDecompressionFromKFlate",
            "rawDeflateDecompressionFromKompress",
        ):
            for library in ("KFlate", "Kompress"):
                metrics[("JVM", "text", operation, library)] = sample
        metadata = {
            ("JVM", "KFlate", "text"): {
                "originalSizeBytes": 1_232_923,
                "compressedSizeBytes": 505_000,
            },
            ("JVM", "Kompress", "text"): {
                "originalSizeBytes": 1_232_923,
                "compressedSizeBytes": 504_000,
            },
        }

        summary = metric_summary(metrics, metadata, {"JVM": {"forks": 3}}, Path("run"))

        self.assertEqual(1_232_923, summary["compression"][0]["originalSizeBytes"])
        self.assertEqual(["KFlate", "Kompress"], [row["producer"] for row in summary["decompression"]])
        markdown = "\n".join(decompression_table_lines(summary))
        self.assertIn("| KFlate |", markdown)
        self.assertIn("| Kompress |", markdown)

    def test_conflicting_metadata_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "metadata.jsonl"
            rows = [
                {
                    "platform": "JVM",
                    "library": "KFlate",
                    "corpus": "text",
                    "originalSizeBytes": 10,
                    "compressedSizeBytes": 5,
                },
                {
                    "platform": "JVM",
                    "library": "KFlate",
                    "corpus": "text",
                    "originalSizeBytes": 10,
                    "compressedSizeBytes": 6,
                },
            ]
            path.write_text("\n".join(json.dumps(row) for row in rows))

            with self.assertRaises(SystemExit):
                read_metadata(path)

    def test_newest_report_directory_is_selected_even_when_an_older_run_has_more_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            older = root / "older-complete"
            newer = root / "newer-partial"
            older.mkdir()
            newer.mkdir()
            for report in (
                "jvmBenchmark.json",
                "linuxX64Benchmark.json",
                "wasmJsBenchmark.json",
            ):
                (older / report).write_text("[]")
            (newer / "jvmBenchmark.json").write_text("[]")
            os.utime(older, (1, 1))
            os.utime(newer, (2, 2))

            self.assertEqual(newer, select_report_dir(root, None))


if __name__ == "__main__":
    unittest.main()
