#!/usr/bin/env python3
import argparse
import json
import math
from datetime import datetime
from pathlib import Path

PLATFORMS = {
    "jvmBenchmark.json": "JVM",
    "linuxX64Benchmark.json": "Linux x64 Native",
    "wasmJsBenchmark.json": "Wasm/JS",
}

CORPORA = [
    "simpleText",
    "text",
    "model3D",
    "Rainier.bmp",
    "Maltese.bmp",
    "Sunrise.bmp",
    "compressed_MVT.pbf",
]

OPERATIONS = {
    "rawDeflateCompression": ("compression", None),
    "rawDeflateDecompressionFromKFlate": ("decompression", "KFlate"),
    "rawDeflateDecompressionFromKompress": ("decompression", "Kompress"),
}

CORPUS_ORDER = {name: index for index, name in enumerate(CORPORA)}
PLATFORM_ORDER = {name: index for index, name in enumerate(PLATFORMS.values())}
PRODUCER_ORDER = {"KFlate": 0, "Kompress": 1}


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate benchmark comparisons with uncertainty from kotlinx-benchmark JSON reports."
    )
    parser.add_argument(
        "--report-root",
        type=Path,
        default=Path("kflate/build/reports/benchmarks/main"),
        help="Directory containing timestamped benchmark report folders.",
    )
    parser.add_argument(
        "--run-dir",
        type=Path,
        default=None,
        help="Specific timestamp report directory to read.",
    )
    parser.add_argument(
        "--metadata",
        type=Path,
        default=Path("kflate/performance/benchmark-metadata.jsonl"),
        help="JSONL file written by benchmark setup with corpus and compressed sizes.",
    )
    parser.add_argument(
        "--allow-missing-sizes",
        action="store_true",
        help="Write reports even when compressed-size metadata is missing.",
    )
    parser.add_argument(
        "--allow-partial",
        action="store_true",
        help="Write reports from a subset of platforms or benchmark rows.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Markdown output path.",
    )
    parser.add_argument(
        "--json-output",
        type=Path,
        default=None,
        help="JSON output path.",
    )
    return parser.parse_args()


def default_output_path():
    timestamp = datetime.now().strftime("%Y-%m-%dT%H-%M-%S")
    return Path("performance") / f"benchmark-comparison-{timestamp}.md"


def report_directories(report_root):
    if not report_root.exists():
        return []
    return [path for path in report_root.iterdir() if path.is_dir()]


def report_dir_score(report_dir):
    return report_dir.stat().st_mtime


def select_report_dir(report_root, requested_run_dir):
    if requested_run_dir is not None:
        return requested_run_dir

    candidates = report_directories(report_root)
    if not candidates:
        return None
    return max(candidates, key=report_dir_score)


def library_name(benchmark):
    if ".CompressionBenchmarks." in benchmark:
        return "KFlate"
    if ".KompressBaselineBenchmarks." in benchmark:
        return "Kompress"
    return None


def read_metadata(metadata):
    values_by_key = {}
    if not metadata.exists():
        raise SystemExit(
            f"Metadata file not found: '{metadata}'.\n"
            "Run benchmarks first so benchmark setup writes metadata, or pass --metadata <path>."
        )

    for line_number, line in enumerate(metadata.read_text().splitlines(), start=1):
        if not line.strip():
            continue
        values = json.loads(line)
        platform = values.get("platform")
        library = values.get("library")
        corpus = values.get("corpus")
        original_size = values.get("originalSizeBytes")
        compressed_size = values.get("compressedSizeBytes")
        if None in (platform, library, corpus, original_size, compressed_size):
            raise SystemExit(f"Incomplete benchmark metadata at {metadata}:{line_number}")

        key = (platform, library, corpus)
        row = {
            "originalSizeBytes": int(original_size),
            "compressedSizeBytes": int(compressed_size),
        }
        previous = values_by_key.get(key)
        if previous is not None and previous != row:
            raise SystemExit(f"Conflicting benchmark metadata for {' / '.join(key)}")
        values_by_key[key] = row

    return values_by_key


def report_files(report_dir):
    return {
        platform: report_dir / file_name
        for file_name, platform in PLATFORMS.items()
        if (report_dir / file_name).exists()
    }


def finite_number(value):
    return isinstance(value, (int, float)) and math.isfinite(value)


def milliseconds(value):
    return value * 1000 if finite_number(value) else None


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower_index = math.floor(position)
    upper_index = math.ceil(position)
    if lower_index == upper_index:
        return ordered[lower_index]
    weight = position - lower_index
    return ordered[lower_index] * (1 - weight) + ordered[upper_index] * weight


def metric_from_entry(entry):
    metric = entry["primaryMetric"]
    score_unit = metric.get("scoreUnit")
    if score_unit not in ("s/op", "sec/op"):
        raise SystemExit(f"Unsupported benchmark score unit: {score_unit!r}")
    confidence = metric.get("scoreConfidence", [])
    percentiles = metric.get("scorePercentiles", {})
    raw_data = [
        [milliseconds(value) for value in fork if finite_number(value)]
        for fork in metric.get("rawData", [])
    ]
    raw_data = [fork for fork in raw_data if fork]
    samples = [value for fork in raw_data for value in fork]
    p50 = milliseconds(percentiles.get("50.0"))
    p95 = milliseconds(percentiles.get("95.0"))
    return {
        "sourceUnit": score_unit,
        "averageMs": milliseconds(metric.get("score")),
        "errorMs": milliseconds(metric.get("scoreError")),
        "confidenceIntervalMs": [
            milliseconds(confidence[0]) if len(confidence) > 0 else None,
            milliseconds(confidence[1]) if len(confidence) > 1 else None,
        ],
        "p50Ms": p50 if p50 is not None else percentile(samples, 0.50),
        "p95Ms": p95 if p95 is not None else percentile(samples, 0.95),
        "forkCount": len(raw_data),
        "sampleCount": sum(len(fork) for fork in raw_data),
        "rawDataMs": raw_data,
    }


def environment_from_entry(entry):
    excluded = {
        "benchmark",
        "mode",
        "params",
        "primaryMetric",
        "secondaryMetrics",
        "threads",
    }
    return {key: value for key, value in entry.items() if key not in excluded}


def read_results(report_dir):
    metrics = {}
    environments = {}
    for platform, report in report_files(report_dir).items():
        for entry in json.loads(report.read_text()):
            benchmark = entry["benchmark"]
            library = library_name(benchmark)
            operation = benchmark.rsplit(".", 1)[-1]
            corpus = entry.get("params", {}).get("corpus")
            if library is None or corpus not in CORPUS_ORDER or operation not in OPERATIONS:
                continue

            key = (platform, corpus, operation, library)
            if key in metrics:
                raise SystemExit(f"Duplicate benchmark result for {' / '.join(key)}")
            metrics[key] = metric_from_entry(entry)
            environment = environment_from_entry(entry)
            previous_environment = environments.setdefault(platform, environment)
            if previous_environment != environment:
                raise SystemExit(f"Benchmark environment changes within the {platform} report")

    return metrics, environments


def original_size(metadata, platform, corpus):
    sizes = {
        row["originalSizeBytes"]
        for (row_platform, _, row_corpus), row in metadata.items()
        if row_platform == platform and row_corpus == corpus
    }
    if not sizes:
        return None
    if len(sizes) != 1:
        raise SystemExit(f"Original corpus size differs between libraries for {platform} / {corpus}")
    return sizes.pop()


def compressed_size(metadata, platform, library, corpus):
    row = metadata.get((platform, library, corpus))
    return None if row is None else row["compressedSizeBytes"]


def compression_ratio(compressed, original):
    if compressed is None or original in (None, 0):
        return None
    return compressed / original


def paired_rows(metrics, operation):
    return sorted(
        {
            (platform, corpus)
            for platform, corpus, row_operation, _ in metrics
            if row_operation == operation
        },
        key=lambda key: (PLATFORM_ORDER[key[0]], CORPUS_ORDER[key[1]]),
    )


def metric(metrics, platform, corpus, operation, library):
    return metrics.get((platform, corpus, operation, library))


def metric_summary(metrics, metadata, environments, report_dir):
    compression = []
    for platform, corpus in paired_rows(metrics, "rawDeflateCompression"):
        size = original_size(metadata, platform, corpus)
        kflate_size = compressed_size(metadata, platform, "KFlate", corpus)
        kompress_size = compressed_size(metadata, platform, "Kompress", corpus)
        compression.append(
            {
                "platform": platform,
                "corpus": corpus,
                "originalSizeBytes": size,
                "kflateCompressedSizeBytes": kflate_size,
                "kompressCompressedSizeBytes": kompress_size,
                "kflateCompressionRatio": compression_ratio(kflate_size, size),
                "kompressCompressionRatio": compression_ratio(kompress_size, size),
                "kflate": metric(metrics, platform, corpus, "rawDeflateCompression", "KFlate"),
                "kompress": metric(metrics, platform, corpus, "rawDeflateCompression", "Kompress"),
            }
        )

    decompression = []
    for operation, (_, producer) in OPERATIONS.items():
        if producer is None:
            continue
        for platform, corpus in paired_rows(metrics, operation):
            decompression.append(
                {
                    "platform": platform,
                    "corpus": corpus,
                    "producer": producer,
                    "originalSizeBytes": original_size(metadata, platform, corpus),
                    "kflate": metric(metrics, platform, corpus, operation, "KFlate"),
                    "kompress": metric(metrics, platform, corpus, operation, "Kompress"),
                }
            )
    decompression.sort(
        key=lambda row: (
            PLATFORM_ORDER[row["platform"]],
            CORPUS_ORDER[row["corpus"]],
            PRODUCER_ORDER[row["producer"]],
        )
    )

    return {
        "runDirectory": str(report_dir),
        "comparisonRule": "Compare KFlate and Kompress only within the same platform, corpus, operation, and producer.",
        "environment": environments,
        "compression": compression,
        "decompression": decompression,
    }


def missing_result_rows(metrics, platforms):
    missing = []
    for platform in platforms:
        for corpus in CORPORA:
            for operation in OPERATIONS:
                for library in ("KFlate", "Kompress"):
                    if (platform, corpus, operation, library) not in metrics:
                        missing.append(f"{platform} / {corpus} / {operation} / {library}")
    return missing


def missing_size_rows(summary):
    return [
        f"{row['platform']} / {row['corpus']}"
        for row in summary["compression"]
        if row["originalSizeBytes"] is None
        or row["kflateCompressedSizeBytes"] is None
        or row["kompressCompressedSizeBytes"] is None
    ]


def fmt_number(value, digits=3):
    return "-" if not finite_number(value) else f"{value:.{digits}f}"


def fmt_bytes(value):
    return "-" if value is None else f"{value:,}"


def fmt_ratio(value):
    return "-" if not finite_number(value) else f"{value * 100:.2f}%"


def fmt_average(metric_value):
    if metric_value is None:
        return "-"
    average = fmt_number(metric_value["averageMs"])
    error = metric_value["errorMs"]
    return average if not finite_number(error) else f"{average} ± {error:.3f}"


def fmt_confidence(metric_value):
    if metric_value is None:
        return "-"
    lower, upper = metric_value["confidenceIntervalMs"]
    if not finite_number(lower) or not finite_number(upper):
        return "-"
    return f"[{lower:.3f}, {upper:.3f}]"


def fmt_percentiles(metric_value):
    if metric_value is None:
        return "-"
    return f"{fmt_number(metric_value['p50Ms'])} / {fmt_number(metric_value['p95Ms'])}"


def compression_table_lines(summary):
    lines = [
        "## Compression",
        "",
        "| Platform | Corpus | Original bytes | KFlate bytes | Kompress bytes | KFlate ratio | Kompress ratio | KFlate avg ± error ms | Kompress avg ± error ms | KFlate p50 / p95 ms | Kompress p50 / p95 ms | KFlate confidence interval ms | Kompress confidence interval ms |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in summary["compression"]:
        lines.append(
            f"| {row['platform']} | `{row['corpus']}` | {fmt_bytes(row['originalSizeBytes'])} | "
            f"{fmt_bytes(row['kflateCompressedSizeBytes'])} | {fmt_bytes(row['kompressCompressedSizeBytes'])} | "
            f"{fmt_ratio(row['kflateCompressionRatio'])} | {fmt_ratio(row['kompressCompressionRatio'])} | "
            f"{fmt_average(row['kflate'])} | {fmt_average(row['kompress'])} | "
            f"{fmt_percentiles(row['kflate'])} | {fmt_percentiles(row['kompress'])} | "
            f"{fmt_confidence(row['kflate'])} | {fmt_confidence(row['kompress'])} |"
        )
    return lines


def decompression_table_lines(summary):
    lines = [
        "## Decompression",
        "",
        "| Platform | Corpus | Stream producer | KFlate avg ± error ms | Kompress avg ± error ms | KFlate p50 / p95 ms | Kompress p50 / p95 ms | KFlate confidence interval ms | Kompress confidence interval ms |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in summary["decompression"]:
        lines.append(
            f"| {row['platform']} | `{row['corpus']}` | {row['producer']} | "
            f"{fmt_average(row['kflate'])} | {fmt_average(row['kompress'])} | "
            f"{fmt_percentiles(row['kflate'])} | {fmt_percentiles(row['kompress'])} | "
            f"{fmt_confidence(row['kflate'])} | {fmt_confidence(row['kompress'])} |"
        )
    return lines


def report_lines(summary):
    lines = [
        "# Benchmark comparison",
        "",
        "Compare KFlate and Kompress only within the same platform, corpus, operation, and stream producer.",
        "Absolute times across JVM, Native, and Wasm are not comparable because their runtimes and baseline backends differ.",
        "",
    ]
    lines.extend(compression_table_lines(summary))
    lines.append("")
    lines.extend(decompression_table_lines(summary))
    return lines


def write_report(summary, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(report_lines(summary)) + "\n")


def write_json(summary, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2, allow_nan=False) + "\n")


def main():
    args = parse_args()
    output = args.output or default_output_path()
    json_output = args.json_output or output.with_suffix(".json")
    report_dir = select_report_dir(args.report_root, args.run_dir)
    if report_dir is None or not report_dir.exists():
        raise SystemExit(
            f"No benchmark run directory found under '{args.report_root}'. "
            "Run benchmarks first with ./gradlew :kflate:benchmarkAll."
        )

    files = report_files(report_dir)
    available_platforms = set(files)
    missing_platforms = set(PLATFORMS.values()) - available_platforms
    if missing_platforms and not args.allow_partial:
        raise SystemExit(
            "Benchmark reports are missing for: "
            + ", ".join(sorted(missing_platforms))
            + ". Pass --allow-partial only for local investigation."
        )

    metrics, environments = read_results(report_dir)
    missing_results = missing_result_rows(metrics, available_platforms)
    if missing_results and not args.allow_partial:
        raise SystemExit(
            "Benchmark result rows are missing:\n  - "
            + "\n  - ".join(missing_results)
            + "\nPass --allow-partial only for local investigation."
        )

    summary = metric_summary(metrics, read_metadata(args.metadata), environments, report_dir)
    missing_sizes = missing_size_rows(summary)
    if missing_sizes and not args.allow_missing_sizes:
        raise SystemExit(
            "Corpus or compressed-size metadata is missing for:\n  - "
            + "\n  - ".join(missing_sizes)
            + "\nRun the matching benchmarks again or pass --allow-missing-sizes for local investigation."
        )

    write_report(summary, output)
    write_json(summary, json_output)
    print(f"Using benchmark report directory: {report_dir}")
    print(f"Wrote benchmark comparison tables to {output}")
    print(f"Wrote benchmark metric JSON to {json_output}")


if __name__ == "__main__":
    main()
