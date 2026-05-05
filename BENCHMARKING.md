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

Recommended comparison table:

| Platform | Corpus | Operation | KFlate avg s/op | Kompress avg s/op | KFlate MiB/s | Kompress MiB/s | KFlate vs Kompress |
| --- | --- | --- | --- | --- | --- | --- | --- |
| JVM | `text` | `rawDeflateCompression` | from JSON | from JSON | computed | computed | computed |
| Linux x64 Native | `text` | `rawDeflateCompression` | from JSON | from JSON | computed | computed | computed |
| Wasm/JS | `text` | `rawDeflateCompression` | from JSON | from JSON | computed | computed | computed |


