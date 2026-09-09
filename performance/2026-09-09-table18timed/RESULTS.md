# Reduce hash collisions at levels 6 through 8

Raise the hash-bit cap to 18 at levels 6 through 8. Large-input hash-head storage rises from 64 KiB at levels 6/7 and 128 KiB at level 8 to 512 KiB. This reduces compressed size but increases text compression time in the measured level 6 run. The 2 MiB variant saved only another 0.067 percentage points overall at level 6, so this change retains the smaller table.

Base: `fe2ff51`, `dev-1.1.0`. Measured on September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14; Node uses 22.22.1.

Only KFlate was timed. Kompress sizes and timings come unchanged from the trusted September 8 archive in `performance/history.json`. Benchmark setup may produce Kompress streams for decoder validation outside timing.

## Compressed size

The JVM sweep compresses all seven tracked corpora at every listed level. Every output must round-trip through both the JDK RAW inflater and KFlate. Byte counts are deterministic. Single-run durations in the sweep JSON are diagnostic, not throughput benchmarks.

Output reduction is `(beforeBytes - afterBytes) / beforeBytes`. Totals weight each corpus by its compressed bytes; they are not averages of percentage changes.

| Level | Before bytes | After bytes | Output reduction |
| ---: | ---: | ---: | ---: |
| 0 | 76,379,868 | 76,379,868 | 0.0000% |
| 1 | 40,222,941 | 40,222,941 | 0.0000% |
| 2 | 39,269,936 | 39,269,936 | 0.0000% |
| 3 | 38,906,551 | 38,906,551 | 0.0000% |
| 4 | 38,333,169 | 38,333,169 | 0.0000% |
| 5 | 38,293,272 | 38,293,272 | 0.0000% |
| 6 | 37,882,044 | 37,658,928 | 0.5890% |
| 7 | 37,868,330 | 37,645,390 | 0.5887% |
| 8 | 37,737,043 | 37,633,460 | 0.2745% |
| 9 | 37,251,418 | 37,251,418 | 0.0000% |

### Level 6

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 506,455 | 505,467 | 0.1951% | 505,318 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,283,450 | 3,272,788 | 0.3247% | 3,275,337 |
| Maltese.bmp | 7,158,472 | 7,110,022 | 0.6768% | 7,096,685 |
| Sunrise.bmp | 26,838,825 | 26,676,269 | 0.6057% | 26,698,992 |
| compressed_MVT.pbf | 92,605 | 92,145 | 0.4967% | 91,408 |

### Level 9

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 492,523 | 492,523 | 0.0000% | 503,400 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,258,570 | 3,258,570 | 0.0000% | 3,270,382 |
| Maltese.bmp | 7,034,092 | 7,034,092 | 0.0000% | 7,090,887 |
| Sunrise.bmp | 26,372,269 | 26,372,269 | 0.0000% | 26,652,810 |
| compressed_MVT.pbf | 91,727 | 91,727 | 0.0000% | 91,400 |

## Compression timing

Development measurements use three one-second warmups and five one-second measurement iterations. JVM has one fresh fork per corpus; Native has one process per corpus; Wasm uses one runner. These shorter runs detect broad tradeoffs and are not release-grade speed claims. Raw samples, errors and confidence intervals are retained. No decompression speed claim is made.

### jvm, level 6

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0279 | 0.0263 | 1.059x | 0.162x |
| text | 60.4159 | 86.0590 | 0.702x | 0.605x |
| model3D | 0.0669 | 0.0637 | 1.050x | 0.474x |
| Rainier.bmp | 108.3289 | 124.8295 | 0.868x | 0.826x |
| Maltese.bmp | 477.9739 | 495.8255 | 0.964x | 0.762x |
| Sunrise.bmp | 1673.0244 | 1540.8887 | 1.086x | 0.803x |
| compressed_MVT.pbf | 4.5006 | 4.1359 | 1.088x | 0.944x |


## Validation and reproduction

Linux JVM tests passed, including monotonic search-effort settings. All 70 corpus/level cases passed JDK and KFlate decompression. Levels 6 through 8 had no compressed-size regressions among the seven fixtures; other levels retained their settings. All seven level 6 JMH cases completed. Native and Wasm were not timed for this variant.

Tracked source diff SHA-256: `003f65e2d3b4b5a0c7fcbcaf45ca2f4f23898039f74cf5f6e7a31a44ab616706`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.

## Timing calibration

A later three-fork unchanged-baseline run measured text at 60.37, 85.43, and 64.00 ms per fork. This spread means the initial single-fork text comparison cannot establish a causal speed change. The deterministic size results are unaffected. See `baseline-recheck-jmh.json` for all samples.
