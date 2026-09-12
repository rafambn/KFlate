# Level-6 zlib-style lazy-parser follow-up

Date: 2026-09-12  
Branch: `perf/restore-level6-speed`  
Base commit: `843513734e4cede3f1084a1a858112ecb66d19ef`  
Host: Linux remote, JVM 25.0.4  
Compression level: 6

## Decision

Retain the corrected implementation.

The parser policy remains:

- Level 0: no compression.
- Levels 1-3: greedy parsing.
- Levels 4-8: one-byte lazy parsing.
- Level 9: cost-aware parsing.

The regular matcher now uses a rolling 24-bit window for KFlate's existing three-byte hash ordering. It avoids reloading the first two bytes at every position and reuses the next-position hash during lazy lookahead. The lazy search also uses zlib-style good-match throttling and starts from the current match length, so it only spends chain work looking for a strictly longer next match. Level 9 keeps the original hash ordering and cost-aware path.

The level-4 throttle is effectively inactive because its lazy threshold is four and the throttle requires a current match of at least four. The effective throttle applies to levels 5-8, matching the intended zlib-style threshold behavior. The reference implementation and thresholds are in [zlib's `deflate.c`](https://github.com/madler/zlib/blob/develop/deflate.c).

## Controlled Linux benchmark

Baseline: direct three-byte hash with no good-match throttle.  
Candidate: rolling old-order hash window, good-match throttle, and seeded lazy lookahead.

Both runs used three one-second warmups, fifteen one-second measurements, three forks, average-time mode, and all seven level-6 benchmark corpora.

Raw results: [`kflate-zlib-direct-final-315.json`](kflate-zlib-direct-final-315.json) and [`kflate-zlib-window-final-315.json`](kflate-zlib-window-final-315.json).

| Corpus | Baseline ms | Candidate ms | Time change | Baseline bytes | Candidate bytes | Size change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `simpleText` | 0.004 | 0.004 | +0.03% | 84 | 84 | 0 |
| `text` | 62.733 | 64.882 | +3.43% | 506,455 | 506,673 | +218 (+0.04%) |
| `model3D` | 0.049 | 0.050 | +1.52% | 2,153 | 2,153 | 0 |
| `Rainier.bmp` | 113.105 | 102.797 | **-9.11%** | 3,283,450 | 3,283,450 | 0 |
| `Maltese.bmp` | 447.238 | 397.023 | **-11.23%** | 7,158,472 | 7,158,472 | 0 |
| `Sunrise.bmp` | 1,512.083 | 1,520.802 | +0.58% | 26,838,825 | 26,838,825 | 0 |
| `compressed_MVT.pbf` | 3.722 | 3.899 | +4.76% | 92,605 | 92,608 | +3 (+0.003%) |

The meaningful result is the large-image workload: Rainier and Maltese are about 9-11% faster with identical output sizes. The small fixtures and the text/MVT timing deltas are within the run-to-run confidence noise; the only material size changes are +218 bytes for text and +3 bytes for MVT.

## Experiments rejected

- An aggressive zlib hash ordering changed match-chain distribution and produced less stable size/time behavior, so it was not retained.
- Removing good-match throttling made the rolling-window parser worse on text, Sunrise, and MVT in the isolation run. The throttle remains.
- The leftover zlib hash ordering in the level-9 overload was found during review and restored before approval.

## Verification

- Local `:kflate:jvmTest`: passed.
- Remote Linux `:kflate:jvmTest`: passed.
- Local `:kflate:assemble`: passed for the configured JVM, Native, JS, Wasm, Android, Apple, and other targets.
- Remote benchmark-jar build: passed.
- Astra medium review: approved the corrected diff with no remaining blockers.
- Local and Linux checkouts are on `perf/restore-level6-speed` at the same base commit.

The commit contains only the six files listed in this report; unrelated worktree changes were left untouched.
