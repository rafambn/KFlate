# Benchmarking

The suite measures one-shot RAW DEFLATE compression at every level from 0 through 9, and decompression of the exact files saved by Kompress at those levels. Each platform covers all seven tracked corpus files in `kflate/src/jvmTest/resources`.

Kompress is pinned to 1.4.2. Its backends are `java.util.zip` on JVM, platform zlib on Linux x64 Native, and fflate 0.8.2 on Wasm/JS. Baselines and fixtures are separate for each platform. KFlate never generates a Kompress stream during its own benchmark runs.

## Capture once, compare repeatedly

Run every measurement on the Linux x64 benchmark machine, including JVM and Wasm. From its checkout:

```sh
python3 scripts/benchmark_suite.py baseline
python3 scripts/benchmark_suite.py run
```

The first command captures each missing Kompress baseline and stores its compressed fixtures. A completed baseline is reused without measuring Kompress again. The second command measures only KFlate, then appends a new run to the history. Platforms run sequentially.

Equivalent Gradle entry points are `:kflate:benchmarkBaseline` and `:kflate:benchmarkAll`. The script is preferred for long runs because it writes a separate log for each platform.

Select platforms or assign a readable run identifier:

```sh
python3 scripts/benchmark_suite.py baseline --platforms jvm linuxX64 wasmJs --run-id initial-baseline
python3 scripts/benchmark_suite.py run --platforms jvm --run-id parser-change
```

Do not change source files or corpus files while measurements are running. The capture checks their hashes before and after each platform. A run identifier cannot overwrite an existing platform result. Interrupted captures retain their logs; completed platform baselines are skipped when capture resumes.

## Saved files

```text
performance/
  kompress-baseline/<platform>/
    baseline.json
    fixtures/<level>/<corpus>.deflate
    raw/benchmark.json
    raw/metadata.jsonl
    raw/benchmark.log
    raw/result.json
  runs/<run-id>/<platform>/
    benchmark.json
    metadata.jsonl
    benchmark.log
    result.json
  history.json
```

Keep this directory when cleaning build outputs or moving the checkout. Back up the fixtures as well as the JSON. The corpus includes a 50 MiB image, so saving ten levels on three platforms takes substantial disk space. These are actual binary streams, not regenerated approximations.

Each result retains averages, errors, confidence intervals, iteration percentiles, fork/sample counts, and every raw iteration average. It also records timestamps, host information, runner environment fields, dependency declarations, source hashes, corpus hashes, and fixture hashes. KFlate runs reference the baseline run identifier.

Fixture and corpus hashes protect input identity. There are no drift benchmarks and no automatic baseline refresh. Do not replace a baseline that existing runs reference. A future dependency update would require a separately versioned baseline workflow.

## Measurement configuration

Full runs use 8 warmup iterations and 15 one-second measurement iterations. JVM uses 3 fresh forks. Native and Wasm use their runner process models. Each library has 140 cases per platform: seven files, ten levels, and two operations. The baseline and first KFlate run take roughly nine hours at the minimum configured iteration durations, plus compilation, process startup, and slow operations.

Setup performs compression, file loading, size recording, and round-trip validation outside the measured region. The measured APIs include allocation and backend bridge costs. Decompression always reads the platform-specific saved Kompress stream. Its plotted level is the stream compression level, not a decoder option.

## Smoke checks

Run the baseline smoke before the KFlate smoke on the Linux machine:

```sh
python3 scripts/benchmark_suite.py baseline --smoke
python3 scripts/benchmark_suite.py run --smoke
```

These use `simpleText` at level 6 and validate all three platforms. They retain separate smoke results and never publish timing data to the benchmark page. A full run still measures every file and level. Both workflows validate exact matrix coverage, usable samples, output correctness, and fixture identity.

## Benchmark page

Rebuild history from retained results and assemble the site:

```sh
python3 scripts/benchmark_suite.py report
./gradlew :web-demo:assembleWebDemo
```

Open `benchmarks.html` in the assembled site. Its two selectors choose platform and operation. Every retained KFlate run appears with a chart for each file, compared with its Kompress baseline. Expand a chart to inspect numerical measurements and uncertainty, or download the full history JSON.

Kandy generates the curves on the JVM during the site build. Its Lets-Plot browser renderer places compression time above compressed size in two stacked plots. Solid lines and points show milliseconds in the top plot; dashed lines show bytes in the bottom plot. Each plot has its own zero-based Y scale. Scales are fixed across runs for the same file, platform, and operation, and vary between files. Use the numerical table when comparing time and size values directly.

The site build generates charts from stored data and runs no measurements. With no saved history it shows an empty state. The browser renderer loads its pinned JavaScript from jsDelivr. Build output is under `web-demo/build/webDemo`; generated plots are under its `plots` directory.

Wasm uses the Kotlin Wasm yarn lock. Update it if Gradle reports a changed lock:

~~~bash
./gradlew kotlinWasmUpgradeYarnLock
~~~

The benchmark plugin writes target reports under:

~~~text
kflate/build/reports/benchmarks/main/<timestamp>/
~~~

`benchmarkAll` clears prior reports and metadata first. It then merges platform metadata into
`kflate/performance/benchmark-metadata.jsonl` and runs `scripts/benchmark_comparison.py`.

The comparison script writes:

~~~text
performance/benchmark-comparison-<timestamp>.md
performance/benchmark-comparison-<timestamp>.json
~~~

Use `--output`, `--json-output`, `--metadata`, or `--run-dir` to override those paths.
Automatic report selection chooses the newest timestamp directory and rejects missing platforms or benchmark rows.
Reports also reject missing or empty sample forks and nonnumeric, nonfinite, or nonpositive scores and samples.
These measurement checks apply even with `--allow-partial`; invalid samples are never discarded.
It never mixes files from different directories or rejects a platform because another target took longer to finish.
`--allow-partial` and `--allow-missing-sizes` exist for local investigation, not release reports.

## Results on the demo page

`benchmarkAll` also publishes the complete comparison to `performance/latest.json`.
Commit this snapshot with the benchmark reports to update the demo's latest run.
Smoke benchmarks and partial comparisons never replace it.

The demo build copies this tracked file to `benchmark-results.json`. Both local
browser builds and `assembleWebDemo` use that same source. GitHub Pages deploys
the assembled snapshot and checks that it matches the tracked file; deployment
does not rerun the benchmark suite or search timestamp directories.

To publish an archived complete run, invoke `scripts/benchmark_comparison.py`
with `--publish-latest`, its `--run-dir` and `--metadata`, and
`--benchmark-commit <measured SHA>`. Abbreviated commits are resolved to their full SHA.
If the archived directory is named `raw` rather than a timestamp, also pass
`--run-id <measured date or timestamp>`. Publication rejects undated run IDs.
Otherwise publication records the current
Git HEAD. Keep the checkout unchanged while running `benchmarkAll`.
The page identifies the measured run and commit, which can predate the demo code.

## Benchmark matrix

Compression times each library's own level-6 compressor and records its compressed size:

| Operation | KFlate method | Kompress method |
| --- | --- | --- |
| RAW compression | `CompressionBenchmarks.rawDeflateCompression` | `KompressBaselineBenchmarks.rawDeflateCompression` |

Decompression tests both decoders against streams produced by both compressors:

| Stream producer | KFlate method | Kompress method |
| --- | --- | --- |
| KFlate | `CompressionBenchmarks.rawDeflateDecompressionFromKFlate` | `KompressBaselineBenchmarks.rawDeflateDecompressionFromKFlate` |
| Kompress | `CompressionBenchmarks.rawDeflateDecompressionFromKompress` | `KompressBaselineBenchmarks.rawDeflateDecompressionFromKompress` |

Setup verifies that each decoder reproduces the original corpus from both streams before timing begins.
This catches incompatible output without including validation work in measured time.

## Reading results

Treat a small difference as noise when confidence intervals are wide or raw samples drift.
The report keeps both compressed size and time because a faster compressor that produces materially larger output is a tradeoff, not an unconditional win.

Throughput can be calculated for a row from its original byte count and average-time score:

~~~text
throughputMiBPerSecond = originalSizeBytes / 1,048,576 / averageSeconds
~~~

Record the Git commit, machine, operating system, JDK, Node version, and system load with any published result.
The cleaned JSON retains environment fields present in the source reports, but it cannot detect thermal throttling or competing processes.

The existing `performance/pr30-linux-e5992df` archive and `scripts/benchmark_comparison.py` describe the previous level-6 suite. They remain available for historical inspection and are not mixed into the new level matrix.
