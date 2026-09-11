# Choose stored blocks using their actual padding cost

Use the current bit position when comparing stored, fixed and dynamic blocks. This corrects near-tie decisions at non-byte-aligned boundaries.

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

## Boundary case

The regression test writes a complete RAW stream with three high-byte literals in a nonfinal fixed block, followed by 26 high-byte literals. The first block ends at bit 37. The old cost estimate chooses a final fixed block ending at bit 281, requiring 36 bytes. Accounting for padding selects a stored block ending at bit 280, requiring 35 bytes. This saves one byte in this specific case; the seven corpus files at all ten levels retain identical sizes.

## Validation

Linux JVM tests and the 70-case size sweep validate correctness. The whole-stream regression verifies all 29 decoded bytes using strict KFlate validation. No throughput improvement is claimed.

Reproduce with `./gradlew :kflate:jvmTest`; the regression is `InternalHelpersCoverageTest.blockWriterPreservesWholeStreamStoredBlockSavings`. `RatioSweep.java` contains the corpus sweep and JDK interoperability checks. Measurements ran before committing the same implementation with this report.
