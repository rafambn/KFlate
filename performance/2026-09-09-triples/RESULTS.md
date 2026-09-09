# Retain useful distant three-byte matches

Remove the unconditional rejection of three-byte matches beyond distance 4096. The resulting size tradeoff favors the image fixtures: total level-6 output falls 4.04%, while text grows 0.29% and map data grows 0.94%. Level 9 still evaluates match choices through its cost-aware parser.

Base: `fe2ff51`, `dev-1.1.0`. Measured on September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14; Node uses 22.22.1.

Only KFlate was timed. Kompress sizes and timings come unchanged from the trusted September 8 archive in `performance/history.json`. Benchmark setup may produce Kompress streams for decoder validation outside timing.

## Compressed size

The JVM sweep compresses all seven tracked corpora at every listed level. Every output must round-trip through both the JDK RAW inflater and KFlate. Byte counts are deterministic. Single-run durations in the sweep JSON are diagnostic, not throughput benchmarks.

Output reduction is `(beforeBytes - afterBytes) / beforeBytes`. Totals weight each corpus by its compressed bytes; they are not averages of percentage changes.

| Level | Before bytes | After bytes | Output reduction |
| ---: | ---: | ---: | ---: |
| 0 | 76,379,868 | 76,379,868 | 0.0000% |
| 1 | 40,222,941 | 39,994,240 | 0.5686% |
| 2 | 39,269,936 | 38,612,535 | 1.6741% |
| 3 | 38,906,551 | 38,282,975 | 1.6028% |
| 4 | 38,333,169 | 37,242,461 | 2.8453% |
| 5 | 38,293,272 | 37,216,184 | 2.8127% |
| 6 | 37,882,044 | 36,351,301 | 4.0408% |
| 7 | 37,868,330 | 36,337,601 | 4.0422% |
| 8 | 37,737,043 | 35,867,468 | 4.9542% |
| 9 | 37,251,418 | 36,334,651 | 2.4610% |

### Level 6

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 506,455 | 507,907 | -0.2867% | 505,318 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,283,450 | 3,194,187 | 2.7186% | 3,275,337 |
| Maltese.bmp | 7,158,472 | 6,752,861 | 5.6662% | 7,096,685 |
| Sunrise.bmp | 26,838,825 | 25,800,631 | 3.8683% | 26,698,992 |
| compressed_MVT.pbf | 92,605 | 93,478 | -0.9427% | 91,408 |

### Level 9

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 492,523 | 492,576 | -0.0108% | 503,400 |
| model3D | 2,153 | 2,153 | 0.0000% | 2,149 |
| Rainier.bmp | 3,258,570 | 3,157,457 | 3.1030% | 3,270,382 |
| Maltese.bmp | 7,034,092 | 6,610,727 | 6.0188% | 7,090,887 |
| Sunrise.bmp | 26,372,269 | 25,979,422 | 1.4896% | 26,652,810 |
| compressed_MVT.pbf | 91,727 | 92,232 | -0.5505% | 91,400 |

## Compression timing

Development measurements use three one-second warmups and five one-second measurement iterations. JVM has one fresh fork per corpus; Native has one process per corpus; Wasm uses one runner. These shorter runs detect broad tradeoffs and are not release-grade speed claims. Raw samples, errors and confidence intervals are retained. No decompression speed claim is made.

### jvm

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0279 | 0.0270 | 1.034x | 0.158x |
| text | 60.4159 | 77.4568 | 0.780x | 0.673x |
| model3D | 0.0669 | 0.0635 | 1.052x | 0.475x |
| Rainier.bmp | 108.3289 | 129.3832 | 0.837x | 0.797x |
| Maltese.bmp | 477.9739 | 454.0345 | 1.053x | 0.832x |
| Sunrise.bmp | 1673.0244 | 1429.8168 | 1.170x | 0.865x |
| compressed_MVT.pbf | 4.5006 | 3.9631 | 1.136x | 0.985x |


## Validation and reproduction

All 177 JVM tests passed on Linux. All 70 corpus/level outputs passed KFlate round trips and JDK RAW inflater interoperability checks. The timings above measure JVM compression only; they do not establish Native or Wasm speed effects.

Build with `./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar`. Compile and run `RatioSweep.java` against the main classes and benchmark JAR with `kflate/src/jvmTest/resources` as its argument. JMH used filter `.*CompressionBenchmarks.rawDeflateCompression`, `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json`, and main/benchmark classes plus the generated JMH JAR on its classpath.

Measurements ran before committing the same implementation and report. The measured algorithm source is identified by `source-sha256.json`.
