# Benchmarking

KFlate keeps correctness tests separate from performance benchmarks.

## What the suite measures

The suite measures the one-shot RAW DEFLATE APIs of KFlate and Kompress on JVM, Linux x64 Native, and Wasm/JS.
Both compressors use compression level 6. KFlate uses memory level 8. Kompress maps to a different backend on each platform:

| Platform | Kompress backend |
| --- | --- |
| JVM | `java.util.zip` |
| Linux x64 Native | platform `zlib` |
| Wasm/JS | npm `fflate` |

These are end-to-end API comparisons. They include output allocation and any backend bridge cost.

Compare KFlate and Kompress only when the platform, corpus, operation, and decompression stream producer match.
Do not rank JVM, Native, and Wasm absolute times against one another. Their runtimes, code generation, and Kompress backends differ.

The active suite covers RAW DEFLATE only. KFlate also supports GZIP and ZLIB, but this suite makes no performance claim about them.

## Corpus

Every target reads the same tracked files from `kflate/src/jvmTest/resources`:

- `simpleText`
- `text`
- `model3D`
- `Rainier.bmp`
- `Maltese.bmp`
- `Sunrise.bmp`
- `compressed_MVT.pbf`

`BenchmarkCorpus` searches upward from the benchmark process working directory for the repository or `kflate` module.
Running a benchmark artifact outside the checkout fails instead of silently substituting generated data.
The report takes original sizes from benchmark metadata rather than a second hard-coded size table.

Do not rename corpus files, classes, or methods casually. Result history uses their names as stable identifiers.

## Measurement configuration

The main configuration uses average-time mode, JSON output, 8 warmup iterations, and 15 one-second measurement iterations.
JVM benchmarks use 3 fresh JVM forks. Native and Wasm use their runner's process model and do not inherit the JVM fork setting.
For release claims, repeat the full Native and Wasm commands in separate quiet system sessions and compare the retained raw samples.

The generated JSON summary preserves:

- average time and error
- confidence interval
- p50 and p95 of iteration-level average times
- fork and sample counts
- all raw iteration averages
- runtime and runner fields supplied by kotlinx-benchmark

The Markdown report shows the averages, errors, confidence intervals, and percentiles.
Use the raw JSON when investigating regressions or noisy results.

## Run benchmarks

Compile the configured benchmarks:

~~~bash
./gradlew :kflate:assembleBenchmarks
~~~

Run all configured targets and generate comparison reports:

~~~bash
./gradlew :kflate:benchmarkAll
~~~

Run benchmarkAll on a Linux x64 host. Other hosts cannot execute the Linux x64 Native target, and the comparison script rejects an incomplete release report.

Run one target:

~~~bash
./gradlew :kflate:jvmBenchmarkBenchmark
./gradlew :kflate:linuxX64BenchmarkBenchmark
./gradlew :kflate:wasmJsBenchmarkBenchmark
~~~

Wasm uses the Kotlin Wasm yarn lock. Update it if Gradle reports a changed lock:

~~~bash
./gradlew kotlinWasmUpgradeYarnLock
~~~

The benchmark plugin writes target reports under:

~~~text
kflate/build/reports/benchmarks/main/<timestamp>/
~~~

`benchmarkAll` clears prior reports and metadata first. It then merges platform metadata into
`kflate/performance/benchmark-metadata.jsonl` and runs `scripts/benchmark_comparison.py`.

The comparison script writes:

~~~text
performance/benchmark-comparison-<timestamp>.md
performance/benchmark-comparison-<timestamp>.json
~~~

Use `--output`, `--json-output`, `--metadata`, or `--run-dir` to override those paths.
Automatic report selection chooses the newest timestamp directory and rejects missing platforms or benchmark rows.
It never mixes files from different directories or rejects a platform because another target took longer to finish.
`--allow-partial` and `--allow-missing-sizes` exist for local investigation, not release reports.

## Benchmark matrix

Compression times each library's own level-6 compressor and records its compressed size:

| Operation | KFlate method | Kompress method |
| --- | --- | --- |
| RAW compression | `CompressionBenchmarks.rawDeflateCompression` | `KompressBaselineBenchmarks.rawDeflateCompression` |

Decompression tests both decoders against streams produced by both compressors:

| Stream producer | KFlate method | Kompress method |
| --- | --- | --- |
| KFlate | `CompressionBenchmarks.rawDeflateDecompressionFromKFlate` | `KompressBaselineBenchmarks.rawDeflateDecompressionFromKFlate` |
| Kompress | `CompressionBenchmarks.rawDeflateDecompressionFromKompress` | `KompressBaselineBenchmarks.rawDeflateDecompressionFromKompress` |

Setup verifies that each decoder reproduces the original corpus from both streams before timing begins.
This catches incompatible output without including validation work in measured time.

## Reading results

Treat a small difference as noise when confidence intervals are wide or raw samples drift.
The report keeps both compressed size and time because a faster compressor that produces materially larger output is a tradeoff, not an unconditional win.

Throughput can be calculated for a row from its original byte count and average-time score:

~~~text
throughputMiBPerSecond = originalSizeBytes / 1,048,576 / averageSeconds
~~~

Record the Git commit, machine, operating system, JDK, Node version, and system load with any published result.
The cleaned JSON retains environment fields present in the source reports, but it cannot detect thermal throttling or competing processes.
