# Refine level 9 parsing with dynamic Huffman prices

Build Huffman prices from the fixed-cost parse, try one dynamic-cost refinement, and keep it only if the rebuilt full-window estimate improves. This reduces actual level 9 output in all changed fixtures. It adds a second parsing pass and scratch allocations. The estimate spans a parser window, which may become multiple encoded blocks, so it is not a guarantee against output regressions on arbitrary inputs. The Huffman builder sentinel changes to Int.MAX_VALUE because window frequencies can exceed its old 25001 bound.

Base: `fe2ff51`, `dev-1.1.0`. Measured on September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14; Node uses 22.22.1.

Only KFlate was timed. Kompress sizes and timings come unchanged from the trusted September 8 archive in `performance/history.json`. Benchmark setup may produce Kompress streams for decoder validation outside timing.

## Compressed size

The JVM sweep compresses all seven tracked corpora at every listed level. Every output must round-trip through both the JDK RAW inflater and KFlate. Byte counts are deterministic. Single-run durations in the sweep JSON are diagnostic, not throughput benchmarks.

Output reduction is `(beforeBytes - afterBytes) / beforeBytes`. Totals weight each corpus by its compressed bytes; they are not averages of percentage changes.

| Level | Before bytes | After bytes | Output reduction |
| ---: | ---: | ---: | ---: |
| 9 | 37,251,418 | 36,780,911 | 1.2631% |

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
| simpleText | 0.0272 | 0.0273 | 0.996x | 0.148x |
| text | 192.2328 | 194.8697 | 0.986x | 0.382x |
| model3D | 0.0969 | 0.1522 | 0.637x | 0.197x |
| Rainier.bmp | 2551.1684 | 2882.2180 | 0.885x | 0.038x |
| Maltese.bmp | 1206.9043 | 1639.5376 | 0.736x | 0.242x |
| Sunrise.bmp | 11385.8072 | 13942.4601 | 0.817x | 0.121x |
| compressed_MVT.pbf | 9.3247 | 10.8204 | 0.862x | 0.384x |


## Validation and reproduction

Linux JVM tests passed, including multiple parser windows, dictionaries, and high Huffman frequencies. All seven level 9 fixture outputs passed both JDK and KFlate decoding and had no size regressions. All seven level 9 JMH compression cases completed. No Native or Wasm timing claim is made.

Tracked source diff SHA-256: `c5ac518b7849a2fe92ae788dff7a5c162a4bf520a3c819051959a652e01b5c0d`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.
