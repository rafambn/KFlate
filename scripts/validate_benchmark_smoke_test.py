import json
import tempfile
import unittest
from pathlib import Path

from validate_benchmark_smoke import validate_report, validate_run


def benchmark_entry(benchmark, score=0.001, raw_data=None):
    return {
        "benchmark": f"com.rafambn.kflate.benchmark.{benchmark}",
        "params": {"corpus": "simpleText"},
        "primaryMetric": {
            "score": score,
            "scoreUnit": "s/op",
            "rawData": [[score]] if raw_data is None else raw_data,
        },
    }


def complete_report():
    return [
        benchmark_entry(f"{benchmark_class}.{operation}")
        for benchmark_class in ("CompressionBenchmarks", "KompressBaselineBenchmarks")
        for operation in (
            "rawDeflateCompression",
            "rawDeflateDecompressionFromKFlate",
            "rawDeflateDecompressionFromKompress",
        )
    ]


class ValidateBenchmarkSmokeTest(unittest.TestCase):
    def test_complete_finite_report_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            report.write_text(json.dumps(complete_report()))

            self.assertEqual([], validate_report(report))

    def test_missing_and_nonfinite_results_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            entries = complete_report()
            entries.pop()
            entries[0]["primaryMetric"] = {"score": "NaN", "rawData": []}
            report.write_text(json.dumps(entries))

            failures = validate_report(report)

            self.assertTrue(any("missing row" in failure for failure in failures))
            self.assertTrue(any("sample-free metric" in failure for failure in failures))

    def test_all_platform_reports_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            report_dir = Path(directory)
            (report_dir / "jvmBenchmark.json").write_text(json.dumps(complete_report()))

            failures = validate_run(report_dir)

            self.assertEqual(2, len(failures))
            self.assertTrue(all("missing report" in failure for failure in failures))


if __name__ == "__main__":
    unittest.main()
