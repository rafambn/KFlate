# Split blocks when a midpoint reduces their encoded bit count

Compare the original block with two blocks divided at the midpoint token boundary. Reuse the selected Huffman plans to emit the lower-bit-cost option. Account for stored-block alignment and synthetic distance codes without counting nonexistent distance tokens. The main measured benefit is the map-tile fixture; the weighted aggregate gain is small. Planning adds CPU work and allocations. The exact stored-block cost helper overlaps PR33 and should be deduplicated when merging.

Base: `fe2ff51`, `dev-1.1.0`. Measured on September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14; Node uses 22.22.1.

Only KFlate was timed. Kompress sizes and timings come unchanged from the trusted September 8 archive in `performance/history.json`. Benchmark setup may produce Kompress streams for decoder validation outside timing.

## Compressed size

The JVM sweep compresses all seven tracked corpora at every listed level. Every output must round-trip through both the JDK RAW inflater and KFlate. Byte counts are deterministic. Single-run durations in the sweep JSON are diagnostic, not throughput benchmarks.

Output reduction is `(beforeBytes - afterBytes) / beforeBytes`. Totals weight each corpus by its compressed bytes; they are not averages of percentage changes.

| Level | Before bytes | After bytes | Output reduction |
| ---: | ---: | ---: | ---: |
| 0 | 76,379,868 | 76,379,868 | 0.0000% |
| 1 | 40,222,941 | 40,221,943 | 0.0025% |
| 2 | 39,269,936 | 39,268,740 | 0.0030% |
| 3 | 38,906,551 | 38,905,056 | 0.0038% |
| 4 | 38,333,169 | 38,331,394 | 0.0046% |
| 5 | 38,293,272 | 38,291,032 | 0.0058% |
| 6 | 37,882,044 | 37,879,680 | 0.0062% |
| 7 | 37,868,330 | 37,865,989 | 0.0062% |
| 8 | 37,737,043 | 37,734,795 | 0.0060% |
| 9 | 37,251,418 | 37,248,714 | 0.0073% |

### Level 6

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 506,455 | 506,307 | 0.0292% | 505,318 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,283,450 | 3,283,045 | 0.0123% | 3,275,337 |
| Maltese.bmp | 7,158,472 | 7,158,287 | 0.0026% | 7,096,685 |
| Sunrise.bmp | 26,838,825 | 26,838,626 | 0.0007% | 26,698,992 |
| compressed_MVT.pbf | 92,605 | 91,178 | 1.5410% | 91,408 |

### Level 9

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 492,523 | 492,432 | 0.0185% | 503,400 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,258,570 | 3,258,104 | 0.0143% | 3,270,382 |
| Maltese.bmp | 7,034,092 | 7,033,901 | 0.0027% | 7,090,887 |
| Sunrise.bmp | 26,372,269 | 26,372,060 | 0.0008% | 26,652,810 |
| compressed_MVT.pbf | 91,727 | 89,980 | 1.9046% | 91,400 |

## Compression timing

Development measurements use three one-second warmups and five one-second measurement iterations. JVM has one fresh fork per corpus; Native has one process per corpus; Wasm uses one runner. These shorter runs detect broad tradeoffs and are not release-grade speed claims. Raw samples, errors and confidence intervals are retained. No decompression speed claim is made.

### jvm, level 6

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0279 | 0.0277 | 1.005x | 0.153x |
| text | 60.4159 | 62.6884 | 0.964x | 0.831x |
| model3D | 0.0669 | 0.1156 | 0.579x | 0.261x |
| Rainier.bmp | 108.3289 | 122.2448 | 0.886x | 0.844x |
| Maltese.bmp | 477.9739 | 516.8326 | 0.925x | 0.731x |
| Sunrise.bmp | 1673.0244 | 1674.0776 | 0.999x | 0.739x |
| compressed_MVT.pbf | 4.5006 | 4.4917 | 1.002x | 0.869x |


## Validation and reproduction

Linux JVM tests and 100% instruction/branch coverage verification passed. Coverage includes literal-only and mixed block cost/end-position checks and unavailable-source sentinel behavior. All 70 outputs passed both JDK and KFlate decoding with no fixture size regressions; their hashes matched the previous corrected split run after removing an unreachable guard. All seven level 6 JMH compression cases completed. No Native or Wasm timing claim is made for this standalone variant.

Tracked source diff SHA-256: `f3e6457d40e0d6104061209a66fa277db8452484f3622f33ca8d0fc40cfeff44`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.

## Timing calibration

A later three-fork unchanged-baseline run measured text at 60.37, 85.43, and 64.00 ms per fork. This spread means the initial single-fork text comparison cannot establish a causal speed change. The deterministic size results are unaffected. See `baseline-recheck-jmh.json` for all samples.
