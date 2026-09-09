# Refine level 9 parsing with dynamic Huffman prices

Build Huffman prices from the fixed-cost parse, try one dynamic-cost refinement, and keep it only if the rebuilt full-window estimate improves. This adds parsing work and scratch allocations. The estimate spans a parser window, which may become multiple encoded blocks, so it does not guarantee against size regressions on arbitrary inputs. Reject candidates shorter than the DEFLATE minimum of three bytes, including the packed two-byte no-match sentinel at window boundaries. The Huffman builder sentinel becomes Int.MAX_VALUE because window frequencies can exceed 25001.

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
| 9 | 37,251,418 | 36,780,911 | 1.2631% |

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
| text | 492,523 | 488,094 | 0.8992% | 503,400 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,258,570 | 3,255,198 | 0.1035% | 3,270,382 |
| Maltese.bmp | 7,034,092 | 6,860,004 | 2.4749% | 7,090,887 |
| Sunrise.bmp | 26,372,269 | 26,083,978 | 1.0932% | 26,652,810 |
| compressed_MVT.pbf | 91,727 | 91,400 | 0.3565% | 91,400 |

## Compression timing

Development measurements use three one-second warmups and five one-second measurement iterations. JVM has one fresh fork per corpus; Native has one process per corpus; Wasm uses one runner. These shorter runs detect broad tradeoffs and are not release-grade speed claims. Raw samples, errors and confidence intervals are retained. No decompression speed claim is made.

### jvm, level 9

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0272 | 0.0272 | 1.000x | 0.149x |
| text | 192.2328 | 233.2505 | 0.824x | 0.319x |
| model3D | 0.0969 | 0.1644 | 0.589x | 0.183x |
| Rainier.bmp | 2551.1684 | 2680.9962 | 0.952x | 0.041x |
| Maltese.bmp | 1206.9043 | 1650.6747 | 0.731x | 0.240x |
| Sunrise.bmp | 11385.8072 | 12729.0296 | 0.894x | 0.132x |
| compressed_MVT.pbf | 9.3247 | 11.5040 | 0.811x | 0.362x |


## Validation and reproduction

Linux JVM tests and 100% instruction/branch coverage verification passed, including the packed two-byte boundary regression, multiple windows, dictionaries and high Huffman frequencies. All 70 corpus/level outputs passed JDK and KFlate decompression; levels 0 through 8 are unchanged and level 9 has no fixture size regressions. All seven level 9 JMH compression cases completed. The combined variant also passed all 70 cases after the minimum-length fix. No Native or Wasm timing claim is made for this standalone variant.

Tracked source diff SHA-256: `9450056c9788829eb49cd6364321384fa4b483f912838ebe34855b1947e79591`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.
