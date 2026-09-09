# Recover matches hidden by hash collisions

Walk past nonmatching hash-chain entries instead of stopping at the newest collision. Across the tracked corpus this reduces output at every compressed level without a per-corpus size regression. It trades compression time for smaller output; see timings below.

Base: `fe2ff51`, `dev-1.1.0`. Measured on September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14; Node uses 22.22.1.

Only KFlate was timed. Kompress sizes and timings come unchanged from the trusted September 8 archive in `performance/history.json`. Benchmark setup may produce Kompress streams for decoder validation outside timing.

## Compressed size

The JVM sweep compresses all seven tracked corpora at every listed level. Every output must round-trip through both the JDK RAW inflater and KFlate. Byte counts are deterministic. Single-run durations in the sweep JSON are diagnostic, not throughput benchmarks.

Output reduction is `(beforeBytes - afterBytes) / beforeBytes`. Totals weight each corpus by its compressed bytes; they are not averages of percentage changes.

| Level | Before bytes | After bytes | Output reduction |
| ---: | ---: | ---: | ---: |
| 0 | 76,379,868 | 76,379,868 | 0.0000% |
| 1 | 40,222,941 | 39,622,345 | 1.4932% |
| 2 | 39,269,936 | 38,468,406 | 2.0411% |
| 3 | 38,906,551 | 37,992,438 | 2.3495% |
| 4 | 38,333,169 | 37,847,745 | 1.2663% |
| 5 | 38,293,272 | 37,812,255 | 1.2561% |
| 6 | 37,882,044 | 37,628,245 | 0.6700% |
| 7 | 37,868,330 | 37,614,576 | 0.6701% |
| 8 | 37,737,043 | 37,602,558 | 0.3564% |
| 9 | 37,251,418 | 37,246,954 | 0.0120% |

### Level 6

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 506,455 | 503,866 | 0.5112% | 505,318 |
| model3D | 2,153 | 2,148 | 0.2322% | 2,149 |
| Rainier.bmp | 3,283,450 | 3,271,245 | 0.3717% | 3,275,337 |
| Maltese.bmp | 7,158,472 | 7,092,741 | 0.9182% | 7,096,685 |
| Sunrise.bmp | 26,838,825 | 26,666,060 | 0.6437% | 26,698,992 |
| compressed_MVT.pbf | 92,605 | 92,101 | 0.5442% | 91,408 |

### Level 9

| Corpus | Before bytes | After bytes | Reduction | Saved JVM Kompress bytes |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 84 | 84 | 0.0000% | 84 |
| text | 492,523 | 492,051 | 0.0958% | 503,400 |
| model3D | 2,153 | 2,147 | 0.2787% | 2,149 |
| Rainier.bmp | 3,258,570 | 3,258,337 | 0.0072% | 3,270,382 |
| Maltese.bmp | 7,034,092 | 7,031,627 | 0.0350% | 7,090,887 |
| Sunrise.bmp | 26,372,269 | 26,371,028 | 0.0047% | 26,652,810 |
| compressed_MVT.pbf | 91,727 | 91,680 | 0.0512% | 91,400 |

## Compression timing

Development measurements use three one-second warmups and five one-second measurement iterations. JVM has one fresh fork per corpus; Native has one process per corpus; Wasm uses one runner. These shorter runs detect broad tradeoffs and are not release-grade speed claims. Raw samples, errors and confidence intervals are retained. No decompression speed claim is made.

### jvm

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0279 | 0.0271 | 1.026x | 0.157x |
| text | 60.4159 | 78.8756 | 0.766x | 0.660x |
| model3D | 0.0669 | 0.0786 | 0.851x | 0.384x |
| Rainier.bmp | 108.3289 | 186.0693 | 0.582x | 0.554x |
| Maltese.bmp | 477.9739 | 536.2622 | 0.891x | 0.705x |
| Sunrise.bmp | 1673.0244 | 1795.4100 | 0.932x | 0.689x |
| compressed_MVT.pbf | 4.5006 | 5.4291 | 0.829x | 0.719x |

### linuxX64

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0672 | 0.0651 | 1.032x | 0.448x |
| text | 65.9090 | 67.3055 | 0.979x | 0.749x |
| model3D | 0.1313 | 0.1332 | 0.986x | 0.385x |
| Rainier.bmp | 103.6015 | 149.6475 | 0.692x | 0.706x |
| Maltese.bmp | 446.5430 | 463.2164 | 0.964x | 0.765x |
| Sunrise.bmp | 1283.2512 | 1613.3915 | 0.795x | 0.727x |
| compressed_MVT.pbf | 3.5805 | 4.5448 | 0.788x | 0.831x |

### wasmJs

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | Unavailable | 0.0933 | Unavailable | 0.692x |
| text | Unavailable | 84.6156 | Unavailable | 1.271x |
| model3D | Unavailable | 0.1930 | Unavailable | 1.242x |
| Rainier.bmp | Unavailable | 211.9063 | Unavailable | 1.825x |
| Maltese.bmp | Unavailable | 665.7267 | Unavailable | 1.621x |
| Sunrise.bmp | Unavailable | 2357.2363 | Unavailable | 1.556x |
| compressed_MVT.pbf | Unavailable | 6.4155 | Unavailable | 1.608x |


## Validation and reproduction

Linux JVM tests: 178 passed. Native Release and Wasm/JS benchmark builds passed. Each of the seven benchmark corpora passed setup round-trip validation on Native and Wasm. The 70-case JVM size sweep also passed JDK interoperability checks.

Measurements ran on the uncommitted implementation subsequently committed with this report. `source-sha256.json` identifies the measured algorithm source.

Build the benchmark artifacts with `./gradlew :kflate:assembleBenchmarks :kflate:wasmJsBenchmarkSmokeBenchmark -PbenchmarkLibrary=kflate`. Compile `RatioSweep.java` against the JVM main classes and benchmark JAR, then run it with `kflate/src/jvmTest/resources` as its argument.

JMH filter: `.*CompressionBenchmarks.rawDeflateCompression`, with `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json`. The classpath includes main classes, benchmark classes and the generated JMH JAR. `native_bench.py` runs the equivalent Native configuration. Wasm uses the same generated runner configuration with the report path changed, passed to its generated `.mjs` entry point through Node.

## Timing calibration

A later three-fork unchanged-baseline run measured text at 60.37, 85.43, and 64.00 ms per fork. This spread means the initial single-fork text comparison cannot establish a causal speed change. The deterministic size results are unaffected. See `baseline-recheck-jmh.json` for all samples.
