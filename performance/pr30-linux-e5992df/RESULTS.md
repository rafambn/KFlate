# PR #30 Linux benchmark results

Commit: `e5992dfce45cac68322edfa581807c82ee9373a1` on `fix/meaningful-benchmarks`.
Run: September 6, 2026. Duration: 1 hour 32 minutes 5 seconds.
Machine: Intel Core i7-11800H, Ubuntu 26.04 LTS, Linux x86_64. JVM benchmark runtime: JBR 17.0.14.

Command: `ANDROID_HOME=/home/rafael/Android/Sdk ./gradlew :kflate:benchmarkAll --no-parallel --max-workers=1`.

All 126 measurements passed report validation: 42 per platform. Each JVM measurement contains 45 samples across three forks; Native and Wasm each contain 15 samples from one runner. Eight warmup iterations precede measurement.

This measures level-6 RAW DEFLATE through the one-shot APIs, including allocation and backend bridge costs. Native and Wasm represent one run each; repeat them before making release-wide performance claims.

Compare libraries within a platform. The baseline is Kompress using java.util.zip on JVM, zlib on Native, and fflate on Wasm. Wasm results do not isolate fflate algorithm performance from its bridge costs.

## Findings

- JVM compression: KFlate wins on text, Rainier, and map data; Maltese timings overlap; the two smallest fixtures and Sunrise favor the baseline.
- Native compression: KFlate wins on five of seven fixtures, losing on simpleText and model3D.
- JVM and Native: KFlate produces 2.3–4.8% fewer bytes on the images, but 3.6% more bytes on text.
- Wasm compression: KFlate wins on six of seven fixtures, by 1.58–3.84×, but produces slightly larger output on every nontrivial fixture.
- Decompression: KFlate takes 1.45–3.60× baseline time on JVM and 1.31–2.60× on Native. Wasm favors KFlate on every fixture and producer combination.

## Detailed results

Times below are mean milliseconds per operation. Speedup is baseline time divided by KFlate time: above 1 favors KFlate. Output change is relative to the baseline: negative means fewer bytes. See the full report for confidence intervals.

### JVM

Compression:

| Fixture | KFlate ms | Baseline ms | Speedup | KFlate bytes | Baseline bytes | Output change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| simpleText | 0.0269 | 0.0041 | 0.15× | 84 | 84 | +0.00% |
| text | 45.6331 | 52.2834 | 1.15× | 523,365 | 505,318 | +3.57% |
| model3D | 0.0567 | 0.0292 | 0.52× | 2,166 | 2,149 | +0.79% |
| Rainier.bmp | 90.5711 | 98.9046 | 1.09× | 3,199,784 | 3,275,337 | -2.31% |
| Maltese.bmp | 367.2601 | 368.2417 | 1.00× | 6,758,938 | 7,096,685 | -4.76% |
| Sunrise.bmp | 1317.4719 | 1194.4956 | 0.91× | 25,817,354 | 26,698,992 | -3.30% |
| compressed_MVT.pbf | 3.1973 | 3.6841 | 1.15× | 93,980 | 91,408 | +2.81% |

Decompression:

| Fixture | Stream producer | KFlate ms | Baseline ms | Speedup |
| --- | --- | ---: | ---: | ---: |
| simpleText | KFlate | 0.0029 | 0.0008 | 0.28× |
| simpleText | Kompress | 0.0029 | 0.0008 | 0.28× |
| text | KFlate | 7.5533 | 4.7965 | 0.64× |
| text | Kompress | 7.3803 | 4.9708 | 0.67× |
| model3D | KFlate | 0.0208 | 0.0087 | 0.42× |
| model3D | Kompress | 0.0230 | 0.0087 | 0.38× |
| Rainier.bmp | KFlate | 39.7318 | 18.1406 | 0.46× |
| Rainier.bmp | Kompress | 36.9970 | 16.2489 | 0.44× |
| Maltese.bmp | KFlate | 91.0797 | 62.6285 | 0.69× |
| Maltese.bmp | Kompress | 96.0193 | 60.1524 | 0.63× |
| Sunrise.bmp | KFlate | 366.9215 | 216.6018 | 0.59× |
| Sunrise.bmp | Kompress | 392.6270 | 202.3916 | 0.52× |
| compressed_MVT.pbf | KFlate | 1.0933 | 0.6999 | 0.64× |
| compressed_MVT.pbf | Kompress | 1.1024 | 0.6647 | 0.60× |

### Linux x64 Native

Compression:

| Fixture | KFlate ms | Baseline ms | Speedup | KFlate bytes | Baseline bytes | Output change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| simpleText | 0.0591 | 0.0287 | 0.49× | 84 | 84 | +0.00% |
| text | 45.3279 | 52.5620 | 1.16× | 523,365 | 505,318 | +3.57% |
| model3D | 0.1281 | 0.0512 | 0.40× | 2,166 | 2,149 | +0.79% |
| Rainier.bmp | 80.4708 | 93.2167 | 1.16× | 3,199,784 | 3,275,337 | -2.31% |
| Maltese.bmp | 360.4904 | 397.7793 | 1.10× | 6,758,938 | 7,096,685 | -4.76% |
| Sunrise.bmp | 1072.1667 | 1137.5644 | 1.06× | 25,817,354 | 26,698,992 | -3.30% |
| compressed_MVT.pbf | 2.7364 | 3.9657 | 1.45× | 93,980 | 91,408 | +2.81% |

Decompression:

| Fixture | Stream producer | KFlate ms | Baseline ms | Speedup |
| --- | --- | ---: | ---: | ---: |
| simpleText | KFlate | 0.0017 | 0.0008 | 0.50× |
| simpleText | Kompress | 0.0017 | 0.0008 | 0.49× |
| text | KFlate | 7.7926 | 4.5880 | 0.59× |
| text | Kompress | 7.4174 | 4.7199 | 0.64× |
| model3D | KFlate | 0.0211 | 0.0081 | 0.39× |
| model3D | Kompress | 0.0209 | 0.0085 | 0.41× |
| Rainier.bmp | KFlate | 44.4933 | 19.6465 | 0.44× |
| Rainier.bmp | Kompress | 40.8260 | 18.0235 | 0.44× |
| Maltese.bmp | KFlate | 85.3852 | 65.3250 | 0.77× |
| Maltese.bmp | Kompress | 100.5246 | 56.2213 | 0.56× |
| Sunrise.bmp | KFlate | 384.5597 | 210.6986 | 0.55× |
| Sunrise.bmp | Kompress | 398.4364 | 218.0765 | 0.55× |
| compressed_MVT.pbf | KFlate | 1.1999 | 0.6412 | 0.53× |
| compressed_MVT.pbf | Kompress | 1.1010 | 0.6881 | 0.62× |

### Wasm/JS

Compression:

| Fixture | KFlate ms | Baseline ms | Speedup | KFlate bytes | Baseline bytes | Output change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| simpleText | 0.0967 | 0.0635 | 0.66× | 84 | 84 | +0.00% |
| text | 49.8747 | 110.1887 | 2.21× | 523,365 | 520,188 | +0.61% |
| model3D | 0.1499 | 0.2370 | 1.58× | 2,166 | 2,152 | +0.65% |
| Rainier.bmp | 99.2808 | 381.0786 | 3.84× | 3,199,784 | 3,143,371 | +1.79% |
| Maltese.bmp | 415.1019 | 1069.2689 | 2.58× | 6,758,938 | 6,603,460 | +2.35% |
| Sunrise.bmp | 1517.3740 | 3613.7754 | 2.38× | 25,817,354 | 24,923,928 | +3.58% |
| compressed_MVT.pbf | 3.4479 | 10.4051 | 3.02× | 93,980 | 93,881 | +0.11% |

Decompression:

| Fixture | Stream producer | KFlate ms | Baseline ms | Speedup |
| --- | --- | ---: | ---: | ---: |
| simpleText | KFlate | 0.0016 | 0.0388 | 23.67× |
| simpleText | Kompress | 0.0017 | 0.0376 | 21.65× |
| text | KFlate | 9.4298 | 57.2392 | 6.07× |
| text | Kompress | 9.4590 | 56.4969 | 5.97× |
| model3D | KFlate | 0.0291 | 0.1807 | 6.20× |
| model3D | Kompress | 0.0290 | 0.2021 | 6.97× |
| Rainier.bmp | KFlate | 49.0702 | 299.6246 | 6.11× |
| Rainier.bmp | Kompress | 48.3279 | 281.3505 | 5.82× |
| Maltese.bmp | KFlate | 116.0486 | 622.4208 | 5.36× |
| Maltese.bmp | Kompress | 113.3638 | 630.9194 | 5.57× |
| Sunrise.bmp | KFlate | 459.5439 | 2207.2545 | 4.80× |
| Sunrise.bmp | Kompress | 414.3654 | 2526.5033 | 6.10× |
| compressed_MVT.pbf | KFlate | 1.4447 | 7.1636 | 4.96× |
| compressed_MVT.pbf | Kompress | 1.2921 | 8.3117 | 6.43× |

## Files

- [Full comparison with uncertainty](comparison.md)
- [Machine-readable comparison and raw samples](comparison.json)
- [Execution log](linux-benchmark.log)
- [Machine environment at start](linux-environment.txt)
- [JVM raw report](raw/jvmBenchmark.json)
- [Native raw report](raw/linuxX64Benchmark.json)
- [Wasm raw report](raw/wasmJsBenchmark.json)

Remote worktree: `/mnt/Arquivos/MyProjects/KFlate-pr30`. The original Linux checkout and its uncommitted changes were preserved.
