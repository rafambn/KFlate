# Benchmarking

KFlate keeps one performance suite: Kompress RAW DEFLATE measured at every compression level for every tracked corpus file.

## Matrix

The suite runs on JVM, Linux x64 Native, and Wasm/JS. Each target covers these seven files from `kflate/src/jvmTest/resources`:

- `simpleText`
- `text`
- `model3D`
- `Rainier.bmp`
- `Maltese.bmp`
- `Sunrise.bmp`
- `compressed_MVT.pbf`

Kotlinx Benchmark supplies levels `0` through `9` as parameters. Each level and corpus pair measures:

- `rawDeflateCompression`
- `rawDeflateDecompressionFromKompress`

That produces 70 parameter combinations and 140 benchmark rows per target. Setup loads the corpus, creates the Kompress stream, and checks its round trip before timing begins.

The suite measures RAW DEFLATE only. KFlate's GZIP and ZLIB APIs are outside this benchmark.

## Run benchmarks

Compile the benchmark binaries:

~~~bash
./gradlew :kflate:assembleBenchmarks
~~~

Run every configured target:

~~~bash
./gradlew :kflate:benchmarkAll
~~~

Run a single target:

~~~bash
./gradlew :kflate:jvmBenchmarkBenchmark
./gradlew :kflate:linuxX64BenchmarkBenchmark
./gradlew :kflate:wasmJsBenchmarkBenchmark
~~~

Smoke tasks use one corpus and level 6 for a quick build check:

~~~bash
./gradlew :kflate:jvmBenchmarkSmokeBenchmark
./gradlew :kflate:linuxX64BenchmarkSmokeBenchmark
./gradlew :kflate:wasmJsBenchmarkSmokeBenchmark
~~~

Wasm uses the Kotlin Wasm yarn lock. Update it if Gradle reports a changed lock:

~~~bash
./gradlew kotlinWasmUpgradeYarnLock
~~~

The benchmark plugin writes reports under `kflate/build/reports/benchmarks/`. Build output is disposable and is not part of the retained benchmark data.

## Retained data

`performance/history.json` contains one historical Kompress snapshot for each target. It retains the level and corpus matrix as reference data, but the benchmark runner does not read it.

Do not add generated fixture caches, raw logs, comparison reports, or web-demo snapshots to the repository. Rerun the suite when fresh measurements are needed.
