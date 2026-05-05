# Benchmarking

KFlate keeps correctness tests separate from performance benchmarks.

## Run Benchmarks

Compile every configured benchmark without running the full timing suite:

```bash
./gradlew :kflate:assembleBenchmarks
```

Run every configured benchmark target:

```bash
./gradlew :kflate:benchmarkAll
```

`benchmarkAll` now also runs `python3 scripts/benchmark_comparison.py` automatically after the three platform benchmarks finish.
Before generating the comparison, `benchmarkAll` consolidates metadata from JVM/Native and Wasm paths into `kflate/performance/benchmark-metadata.jsonl`.

Run one target at a time:

```bash
./gradlew :kflate:jvmBenchmarkBenchmark
./gradlew :kflate:linuxX64BenchmarkBenchmark
./gradlew :kflate:wasmJsBenchmarkBenchmark
```

Wasm benchmarks use the Kotlin Wasm yarn lock. If Gradle reports that the lock file changed, update it before rerunning:

```bash
./gradlew kotlinWasmUpgradeYarnLock
```

Generated JSON reports are written under:

```text
kflate/build/reports/benchmarks/main/<timestamp>/
```

## How The Suite Is Built

The benchmark suite uses `kotlinx-benchmark` with average-time mode and JSON output. The same common benchmark source runs on JVM, Linux x64 Native, and Wasm/JS.
Main benchmark config uses 3 forks, 8 warmup iterations, and 15 measurement iterations (1 second each).

KFlate benchmark class:

```text
com.rafambn.kflate.benchmark.CompressionBenchmarks
```

Kompress baseline class:

```text
com.rafambn.kflate.benchmark.KompressBaselineBenchmarks
```

Kompress is used as the cross-platform baseline because its implementations map to the platform libraries being compared:

| Platform | Kompress backend |
| --- | --- |
| JVM | `java.util.zip` deflate/inflate |
| Linux x64 Native | platform `zlib` |
| Wasm/JS | npm `fflate` |

This benchmark suite currently times RAW DEFLATE only. KFlate supports GZIP and ZLIB in the library API, but those formats are intentionally excluded from the active benchmark methods.

Benchmarks use deterministic generated fixtures from `BenchmarkCorpus`. They intentionally do not load local files. This keeps the same data shape available on JVM, Native, and Wasm without absolute paths, resource-packaging differences, or missing-file behavior.

## Comparable Matrix

Use the same platform, corpus, operation, and format when comparing KFlate with Kompress.

| Format | Operation | KFlate benchmark | Kompress baseline | Comparable on JVM, Linux Native, Wasm |
| --- | --- | --- | --- | --- |
| RAW DEFLATE | Compression | `CompressionBenchmarks.rawDeflateCompression` | `KompressBaselineBenchmarks.rawDeflateCompression` | Yes |
| RAW DEFLATE | Decompression | `CompressionBenchmarks.rawDeflateDecompression` | `KompressBaselineBenchmarks.rawDeflateDecompression` | Yes |

Stable corpus parameter names:

- `simpleText`
- `text`
- `model3D`
- `Rainier.bmp`
- `Maltese.bmp`
- `Sunrise.bmp`
- `compressed_MVT.pbf`

Do not rename classes, methods, or corpus names casually. Dashboards and regression tools depend on stable benchmark IDs.

## Reading Results

Each JSON entry contains:

- `benchmark`: fully qualified class and method name
- `params.corpus`: corpus name
- `primaryMetric.score`: average seconds per operation
- `primaryMetric.scorePercentiles["50.0"]`: p50 seconds per operation
- `primaryMetric.scorePercentiles["95.0"]`: p95 seconds per operation
- `primaryMetric.rawData`: raw measured iteration data

During setup, each benchmark prints a structured corpus line:

```text
BENCHMARK_CORPUS library=KFlate name=Sunrise.bmp originalBytes=49900000 originalMiB=47.5883 operationMiB=47.5883 rawBytes=... rawRatio=...
BENCHMARK_BASELINE_CORPUS library=Kompress name=Sunrise.bmp originalBytes=49900000 originalMiB=47.5883 operationMiB=47.5883 rawBytes=... rawRatio=...
```

Throughput in MiB/s is computed from the JSON average-time score:

```text
throughputMiBPerSecond = operationMiB / primaryMetric.score
```

For example, if `operationMiB` is `47.5883` and `primaryMetric.score` is `0.5`, throughput is `95.1766 MiB/s`.

## Comparing All Three Platforms

Run:

```bash
./gradlew :kflate:jvmBenchmarkBenchmark
./gradlew :kflate:linuxX64BenchmarkBenchmark
./gradlew :kflate:wasmJsBenchmarkBenchmark
```

Then compare the latest JSON files:

```text
kflate/build/reports/benchmarks/main/<timestamp>/jvmBenchmark.json
kflate/build/reports/benchmarks/main/<timestamp>/linuxX64Benchmark.json
kflate/build/reports/benchmarks/main/<timestamp>/wasmJsBenchmark.json
```

For each platform, compare rows with:

- same `params.corpus`
- same operation suffix, such as `rawDeflateCompression`
- RAW DEFLATE format
- KFlate class vs Kompress class

Recommended compression table:

| Platform | Corpus | Original size | KFlate compressed size | Kompress compressed size | KFlate avg ms | Kompress avg ms |
| --- | --- | --- | --- | --- | --- | --- |
| JVM | `text` | from metadata | from metadata | from metadata | from JSON | from JSON |

Recommended decompression table:

| Platform | Corpus | KFlate avg ms | Kompress avg ms |
| --- | --- | --- | --- |
| JVM | `text` | from JSON | from JSON |

Generate the Markdown tables and cleaned JSON summary from a benchmark run:

```bash
mkdir -p performance
rm -f performance/benchmark-metadata.jsonl
./gradlew :kflate:benchmarkAll
```

By default this writes:

```text
performance/benchmark-comparison-<timestamp>.md
performance/benchmark-comparison-<timestamp>.json
```

Use `--output <path>` to write the Markdown file somewhere else. Use `--json-output <path>` to write the cleaned JSON somewhere else.
Use `--run-dir <path>` to force a specific timestamp folder under `kflate/build/reports/benchmarks/main/`.
Without `--run-dir`, the script selects one timestamp folder and does not mix platform JSON from different runs.

The script reads timing data from kotlinx-benchmark JSON. It reads compressed sizes from one explicit metadata JSONL path.
By default this is `kflate/performance/benchmark-metadata.jsonl`.

Without size metadata for a platform/library/corpus row, the script fails.
Use `--metadata <path>` to point to a specific metadata file.

The decompression benchmarks use the same canonical RAW compressed payload for KFlate and Kompress. Compression benchmarks still time each library's own compressor output.
