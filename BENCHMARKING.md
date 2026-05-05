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

## Running Your Own Benchmark

For a local experiment, change as little as possible:

1. Add a deterministic corpus shape to `BenchmarkCorpus`.
2. Add its name to `BenchmarkCorpora`.
3. Add the corpus constant to `RawBenchmarkState`.
4. Run `./gradlew :kflate:assembleBenchmarks`.
5. Run the target benchmark task you care about.
6. Compare JSON entries with the same platform, corpus, operation, and format.

For a new operation:

1. Add a new stable benchmark method to `CompressionBenchmarks`.
2. Add the equivalent method to `KompressBaselineBenchmarks` only when the operation should be part of the active RAW comparison.
3. Keep setup work outside the timed benchmark method.
4. Return the produced `ByteArray`.
5. Document the new stable method name here.

For local-only testing, do not commit generated report directories. If results become official, record the environment and preserve generated JSON or CSV, not manually edited summaries.

## Publishing Results

Published performance claims must include:

- date
- CPU model
- operating system
- JDK distribution and version
- Kotlin version
- benchmark command
- benchmark target: JVM, Linux x64 Native, or Wasm/JS
- corpus names
- operation and format
- whether the comparison is KFlate-only or KFlate vs Kompress

Use measured language. Example:

```text
On JVM/Temurin 17/Linux x64, KFlate RAW compression on the generated text corpus measured X MiB/s versus Kompress at Y MiB/s.
```

Avoid broad claims such as "matches zlib", "matches Java standard library", or "matches fflate" unless the benchmark output and environment details are included.

## Updating Baselines

Update checked-in benchmark baselines only after:

- benchmark code or compression behavior changes intentionally
- the same stable benchmark names and corpus names are used
- environment details are recorded
- generated JSON or CSV output is preserved
- README performance statements are updated to match the measured data
