import json
import os
import tempfile
import unittest
from pathlib import Path

from benchmark_comparison import (
    decompression_table_lines,
    metric_from_entry,
    metric_summary,
    read_metadata,
    select_report_dir,
)


class BenchmarkComparisonTest(unittest.TestCase):
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
