# Size deflate scratch buffers to short inputs

Bound the token array by input length and bound a new final-call history ring by input length. Non-final calls retain the full history ring. This preserves encoded bytes while cutting unnecessary allocation for short inputs. Large fixtures retain the previous array sizes, so their timing variation should not be read as an allocation benefit.

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
| 6 | 37,882,044 | 37,882,044 | 0.0000% |
| 7 | 37,868,330 | 37,868,330 | 0.0000% |
| 8 | 37,737,043 | 37,737,043 | 0.0000% |
| 9 | 37,251,418 | 37,251,418 | 0.0000% |

### Level 6

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 506,455 | 506,455 | 0.0000% | 505,318 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,283,450 | 3,283,450 | 0.0000% | 3,275,337 |
| Maltese.bmp | 7,158,472 | 7,158,472 | 0.0000% | 7,096,685 |
| Sunrise.bmp | 26,838,825 | 26,838,825 | 0.0000% | 26,698,992 |
| compressed_MVT.pbf | 92,605 | 92,605 | 0.0000% | 91,408 |

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
| simpleText | 0.0279 | 0.0047 | 5.988x | 0.914x |
| text | 60.4159 | 87.8322 | 0.688x | 0.593x |
| model3D | 0.0669 | 0.0512 | 1.306x | 0.590x |
| Rainier.bmp | 108.3289 | 113.5118 | 0.954x | 0.909x |
| Maltese.bmp | 477.9739 | 471.3553 | 1.014x | 0.802x |
| Sunrise.bmp | 1673.0244 | 1564.5829 | 1.069x | 0.791x |
| compressed_MVT.pbf | 4.5006 | 4.3388 | 1.037x | 0.900x |


## Validation and reproduction

Linux JVM tests passed, including empty/tiny inputs, dictionaries, and final/non-final history reuse. All 70 corpus/level cases passed both JDK and KFlate decoding and matched baseline SHA-256 hashes. All seven level 6 JMH cases completed. No Native or Wasm timing claim is made.

Tracked source diff SHA-256: `9dbba52ef9393e557e4b3c8d88977375c3b769eac10db4e780cf54895c77e660`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.

## Three-fork confirmation

Repeated the baseline and scratch variant on simpleText, text and model3D with three independent JVM forks each, retaining three one-second warmups and five one-second measurements per fork. Small-fixture gains reproduced. Text varies between forks even in the unchanged baseline and does not establish a causal slowdown.

| Corpus | Baseline ms | Scratch ms | Speedup |
| --- | ---: | ---: | ---: |
| simpleText | 0.02697 | 0.00442 | 6.106x |
| text | 69.93305 | 77.29299 | 0.905x |
| model3D | 0.06770 | 0.05211 | 1.299x |

Linux `:kflate:koverVerifyJvm` passed with 100% instruction and branch coverage.
