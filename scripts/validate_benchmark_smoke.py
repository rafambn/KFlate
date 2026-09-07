#!/usr/bin/env python3
import argparse
import json
import math
from pathlib import Path

from benchmark_comparison import OPERATIONS, PLATFORMS, library_name, select_report_dir

LIBRARIES = ("KFlate", "Kompress")
SMOKE_CORPUS = "simpleText"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Validate that every smoke benchmark ran and produced usable measurements."
    )
    parser.add_argument(
        "--report-root",
        type=Path,
        default=Path("kflate/build/reports/benchmarks/smoke"),
        help="Directory containing timestamped smoke report folders.",
    )
    parser.add_argument(
        "--run-dir",
        type=Path,
        default=None,
        help="Specific timestamp report directory to validate.",
    )
    return parser.parse_args()


def expected_rows():
    return {
        (operation, library)
        for operation in OPERATIONS
        for library in LIBRARIES
    }


def validate_metric(metric):
    score = metric.get("score")
    raw_data = metric.get("rawData")
    samples = [sample for fork in raw_data or [] for sample in fork]
    return (
        isinstance(score, (int, float))
        and math.isfinite(score)
        and score > 0
        and bool(samples)
        and all(
            isinstance(sample, (int, float)) and math.isfinite(sample) and sample > 0
            for sample in samples
        )
    )


def validate_report(report):
    found = set()
    failures = []
    for entry in json.loads(report.read_text()):
        benchmark = entry.get("benchmark", "")
        operation = benchmark.rsplit(".", 1)[-1]
        library = library_name(benchmark)
        corpus = entry.get("params", {}).get("corpus")
        key = (operation, library)
        if corpus != SMOKE_CORPUS or key not in expected_rows():
            continue
        if key in found:
            failures.append(f"duplicate row: {operation} / {library}")
        found.add(key)
        if not validate_metric(entry.get("primaryMetric", {})):
            failures.append(f"nonfinite, nonpositive, or sample-free metric: {operation} / {library}")

    missing = expected_rows() - found
    failures.extend(f"missing row: {operation} / {library}" for operation, library in sorted(missing))
    return failures


def validate_run(report_dir):
    failures = []
    for file_name, platform in PLATFORMS.items():
        report = report_dir / file_name
        if not report.exists():
            failures.append(f"{platform}: missing report {file_name}")
            continue
        failures.extend(f"{platform}: {failure}" for failure in validate_report(report))
    return failures


def main():
    args = parse_args()
    report_dir = select_report_dir(args.report_root, args.run_dir)
    if report_dir is None or not report_dir.exists():
        raise SystemExit(f"No smoke benchmark run directory found under '{args.report_root}'.")

    failures = validate_run(report_dir)
    if failures:
        raise SystemExit(
            f"Smoke benchmark validation failed for '{report_dir}':\n  - "
            + "\n  - ".join(failures)
        )
    print(f"Validated all smoke benchmark rows in {report_dir}")


if __name__ == "__main__":
    main()
