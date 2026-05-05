#!/usr/bin/env python3
import argparse
import json
from datetime import datetime
from pathlib import Path

PLATFORMS = {
    "jvmBenchmark.json": "JVM",
    "linuxX64Benchmark.json": "Linux x64 Native",
    "wasmJsBenchmark.json": "Wasm/JS",
}

CORPUS_BYTES = {
    "simpleText": 100,
    "text": 1_200_000,
    "model3D": 2_400,
    "Rainier.bmp": 5_900_000,
    "Maltese.bmp": 15_700_000,
    "Sunrise.bmp": 49_900_000,
    "compressed_MVT.pbf": 142_800,
}

OPERATION_ORDER = {
    "rawDeflateCompression": 0,
    "rawDeflateDecompression": 1,
}

CORPUS_ORDER = {name: index for index, name in enumerate(CORPUS_BYTES)}
PLATFORM_ORDER = {name: index for index, name in enumerate(PLATFORMS.values())}
STALE_REPORT_SECONDS = 30 * 60


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate Markdown benchmark comparison tables from kotlinx-benchmark JSON reports."
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
        help="Specific timestamp report directory to read (for example .../main/2026-05-05T09.30.07.928468415).",
    )
    parser.add_argument(
        "--metadata",
        type=Path,
        default=Path("kflate/performance/benchmark-metadata.jsonl"),
        help="JSONL file written by benchmark setup with corpus sizes. Exactly this file is used.",
    )
    parser.add_argument(
        "--allow-missing-sizes",
        action="store_true",
        help="Write JSON and Markdown even when compressed-size metadata is missing.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Markdown file that will receive the generated tables.",
    )
    parser.add_argument(
        "--json-output",
        type=Path,
        default=None,
        help="JSON file that will receive the generated metric summary.",
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
    file_count = sum(1 for file_name in PLATFORMS if (report_dir / file_name).exists())
    return file_count, report_dir.stat().st_mtime


def select_report_dir(report_root, requested_run_dir):
    if requested_run_dir is not None:
        return requested_run_dir

    candidates = report_directories(report_root)
    if not candidates:
        return None

    # Prefer runs with more available platform reports, then newer timestamp folders.
    return max(candidates, key=report_dir_score)


def library_name(benchmark):
    if ".CompressionBenchmarks." in benchmark:
        return "KFlate"
    if ".KompressBaselineBenchmarks." in benchmark:
        return "Kompress"
    return None


def read_size_metadata(metadata):
    sizes = {}
    if not metadata.exists():
        raise SystemExit(
            f"Metadata file not found: '{metadata}'.\n"
            "Run benchmarks first so benchmark setup writes metadata, or pass --metadata <path>."
        )

    for line in metadata.read_text().splitlines():
        if not line.strip():
            continue

        values = json.loads(line)
        platform = values.get("platform")
        library = values.get("library")
        corpus = values.get("corpus")
        compressed_size = values.get("compressedSizeBytes")

        if platform is None or library is None or corpus is None or compressed_size is None:
            continue

        sizes[(platform, library, corpus)] = int(compressed_size)

    return sizes


def report_files(report_dir):
    return {
        platform: (report_dir / file_name)
        for file_name, platform in PLATFORMS.items()
        if (report_dir / file_name).exists()
    }


def stale_report_platforms(report_dir):
    files = report_files(report_dir)
    if not files:
        return set()

    latest_mtime = max(path.stat().st_mtime for path in files.values())
    stale = set()
    for platform, path in files.items():
        if latest_mtime - path.stat().st_mtime > STALE_REPORT_SECONDS:
            stale.add(platform)
    return stale


def read_scores(report_dir, ignored_platforms):
    scores = {}
    for platform, report in report_files(report_dir).items():
        if platform in ignored_platforms:
            continue

        for entry in json.loads(report.read_text()):
            benchmark = entry["benchmark"]
            library = library_name(benchmark)
            operation = benchmark.rsplit(".", 1)[-1]
            corpus = entry.get("params", {}).get("corpus")

            if library is None or corpus not in CORPUS_BYTES or operation not in OPERATION_ORDER:
                continue

            key = (platform, corpus, operation, library)
            scores[key] = entry["primaryMetric"]["score"]

    return scores


def result_order(scores, operation):
    keys = {
        (platform, corpus)
        for platform, corpus, row_operation, _ in scores
        if row_operation == operation
    }
    return sorted(
        keys,
        key=lambda key: (
            PLATFORM_ORDER[key[0]],
            CORPUS_ORDER[key[1]],
        ),
    )


def score(scores, platform, corpus, operation, library):
    return scores.get((platform, corpus, operation, library))


def compressed_size(sizes, platform, library, corpus):
    return sizes.get((platform, library, corpus))


def fmt_bytes(value):
    return "-" if value is None else f"{value:,}"


def fmt_millis(seconds):
    return "-" if seconds is None else f"{seconds * 1000:.3f}"


def millis(seconds):
    return None if seconds is None else seconds * 1000


def metric_summary(scores, sizes):
    compression = []
    for platform, corpus in result_order(scores, "rawDeflateCompression"):
        compression.append(
            {
                "platform": platform,
                "corpus": corpus,
                "originalSizeBytes": CORPUS_BYTES[corpus],
                "kflateCompressedSizeBytes": compressed_size(sizes, platform, "KFlate", corpus),
                "kompressCompressedSizeBytes": compressed_size(sizes, platform, "Kompress", corpus),
                "kflateAvgMs": millis(score(scores, platform, corpus, "rawDeflateCompression", "KFlate")),
                "kompressAvgMs": millis(score(scores, platform, corpus, "rawDeflateCompression", "Kompress")),
            }
        )

    decompression = []
    for platform, corpus in result_order(scores, "rawDeflateDecompression"):
        decompression.append(
            {
                "platform": platform,
                "corpus": corpus,
                "kflateAvgMs": millis(score(scores, platform, corpus, "rawDeflateDecompression", "KFlate")),
                "kompressAvgMs": millis(score(scores, platform, corpus, "rawDeflateDecompression", "Kompress")),
            }
        )

    return {
        "compression": compression,
        "decompression": decompression,
    }


def missing_size_rows(summary):
    missing = []
    for row in summary["compression"]:
        if row["kflateCompressedSizeBytes"] is None or row["kompressCompressedSizeBytes"] is None:
            missing.append(f"{row['platform']} / {row['corpus']}")
    return missing


def compression_table_lines(summary):
    lines = [
        "## Compression",
        "",
        "| Platform | Corpus | Original size | KFlate compressed size | Kompress compressed size | KFlate avg ms | Kompress avg ms |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]

    for row in summary["compression"]:
        lines.append(
            f"| {row['platform']} | `{row['corpus']}` | {fmt_bytes(row['originalSizeBytes'])} | "
            f"{fmt_bytes(row['kflateCompressedSizeBytes'])} | "
            f"{fmt_bytes(row['kompressCompressedSizeBytes'])} | "
            f"{fmt_millis_from_value(row['kflateAvgMs'])} | "
            f"{fmt_millis_from_value(row['kompressAvgMs'])} |"
        )

    return lines


def fmt_millis_from_value(value):
    return "-" if value is None else f"{value:.3f}"


def decompression_table_lines(summary):
    lines = [
        "## Decompression",
        "",
        "| Platform | Corpus | KFlate avg ms | Kompress avg ms |",
        "| --- | --- | --- | --- |",
    ]

    for row in summary["decompression"]:
        lines.append(
            f"| {row['platform']} | `{row['corpus']}` | "
            f"{fmt_millis_from_value(row['kflateAvgMs'])} | "
            f"{fmt_millis_from_value(row['kompressAvgMs'])} |"
        )

    return lines


def report_lines(summary):
    lines = compression_table_lines(summary)
    lines.append("")
    lines.extend(decompression_table_lines(summary))
    return lines


def write_report(summary, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(report_lines(summary)) + "\n")


def write_json(summary, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n")


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
    ignored_platforms = set()
    if args.run_dir is None:
        ignored_platforms = stale_report_platforms(report_dir)
    summary = metric_summary(read_scores(report_dir, ignored_platforms), read_size_metadata(args.metadata))
    missing_sizes = missing_size_rows(summary)
    if missing_sizes and not args.allow_missing_sizes:
        missing = "\n  - ".join(missing_sizes)
        raise SystemExit(
            "Compressed-size metadata is missing for:\n"
            f"  - {missing}\n\n"
            "Run benchmarks first so benchmark setup writes metadata:\n"
            "  mkdir -p performance\n"
            "  rm -f performance/benchmark-metadata.jsonl\n"
            "  ./gradlew :kflate:benchmarkAll\n"
            "  python3 scripts/benchmark_comparison.py\n\n"
            "Use --allow-missing-sizes only for partial/debug reports."
        )
    write_report(summary, output)
    write_json(summary, json_output)
    print(f"Using benchmark report directory: {report_dir}")
    if ignored_platforms:
        print(
            "Ignoring stale platform reports in auto mode: "
            + ", ".join(sorted(ignored_platforms))
            + f" (older than {STALE_REPORT_SECONDS // 60} minutes from latest report file)"
        )
    print(f"Wrote benchmark comparison tables to {output}")
    print(f"Wrote benchmark metric JSON to {json_output}")


if __name__ == "__main__":
    main()
