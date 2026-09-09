# Copy inflate matches with bulk array operations

Use a fill for distance-one matches, a single copy for non-overlapping matches, and expanding copies for overlapping matches. Dictionary handling and output-limit checks remain at the call site. Compression output is unchanged.

Base `fe2ff51` on `dev-1.1.0`. Measured September 9, 2026 on Linux x86_64, Intel Core i7-11800H. JVM uses JBR 17.0.14.

Only KFlate was timed. Saved Kompress timings are accepted unchanged from `performance/history.json`. The Kompress comparison uses only Kompress-produced streams, matching the archived benchmark inputs.

Three one-second warmups, five one-second measurements, one fork per JVM case. These short development measurements expose broad tradeoffs; they are not release-grade speed claims. Raw samples and errors are included.

## jvm, rawDeflateDecompressionFromKFlate

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0029 | 0.0028 | 1.036x | Different input stream |
| text | 7.2320 | 6.0596 | 1.193x | Different input stream |
| model3D | 0.0203 | 0.0201 | 1.011x | Different input stream |
| Rainier.bmp | 37.2355 | 34.7857 | 1.070x | Different input stream |
| Maltese.bmp | 91.8651 | 82.6290 | 1.112x | Different input stream |
| Sunrise.bmp | 363.8703 | 370.5068 | 0.982x | Different input stream |
| compressed_MVT.pbf | 1.0398 | 0.9554 | 1.088x | Different input stream |

## jvm, rawDeflateDecompressionFromKompress

| Corpus | Before ms | After ms | Before/after speedup | Saved Kompress/after speedup |
| --- | ---: | ---: | ---: | ---: |
| simpleText | 0.0029 | 0.0028 | 1.036x | 0.292x |
| text | 7.1466 | 5.7811 | 1.236x | 0.879x |
| model3D | 0.0241 | 0.0222 | 1.082x | 0.387x |
| Rainier.bmp | 36.4853 | 31.8823 | 1.144x | 0.527x |
| Maltese.bmp | 97.7610 | 78.1425 | 1.251x | 0.738x |
| Sunrise.bmp | 375.8864 | 338.8647 | 1.109x | 0.597x |
| compressed_MVT.pbf | 1.0341 | 1.0448 | 0.990x | 0.640x |


## Validation and reproduction

All 180 Linux JVM tests passed, covering dictionaries, streaming, output limits and overlap semantics. All seven level 6 fixtures passed JDK and KFlate round trips. All 14 decoder JMH cases completed. Native and Wasm were not timed for this candidate.

Tracked source diff SHA-256: `7fd5f9a4495062d50ebb25ee8b5b276cab5859703da6e13a464685b697a6f5c1`. Added source/test files are present in this commit.

Linux build: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:jvmTest :kflate:jvmBenchmarkBenchmarkJar --no-parallel --max-workers=1`.

For level 9 timing, set `BENCHMARK_COMPRESSION_LEVEL` to 9 in `BenchmarkState.kt` before building; leave it at 6 otherwise. Use JBR 17.0.14 and the generated JMH jar with the main and benchmark classes on its classpath. Run `org.openjdk.jmh.Main` with the relevant `CompressionBenchmarks` method filter and `-wi 3 -i 5 -w 1s -r 1s -f 1 -foe true -rf json -rff result.json`. Only KFlate benchmark methods should be selected.

Compile `RatioSweep.java` against the KFlate classes and generated benchmark jar, then run it with `kflate/src/jvmTest/resources FIRST_LEVEL LAST_LEVEL`. It checks both JDK and KFlate decompression and records deterministic sizes and output hashes.

`./gradlew :kflate:koverVerifyJvm` passed on Linux with 100% instruction and branch coverage.
