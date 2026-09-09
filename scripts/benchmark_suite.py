#!/usr/bin/env python3
"""Capture Kompress once, then retain KFlate runs against its exact streams."""
import argparse
import fcntl
import hashlib
import json
import platform
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from benchmark_comparison import CORPORA, metric_from_entry

ROOT = Path(__file__).resolve().parent.parent
PERFORMANCE = ROOT / "performance"
PLATFORMS = {"jvm": "JVM", "linuxX64": "Linux x64 Native", "wasmJs": "Wasm/JS"}
OPERATIONS = {"rawDeflateCompression": "compression", "rawDeflateDecompressionFromKompress": "decompression"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n")
    temporary.replace(path)


def corpus_hashes():
    return {name: digest(ROOT / "kflate/src/jvmTest/resources" / name) for name in CORPORA}


def source_hashes():
    paths = list((ROOT / "kflate/src").rglob("*.kt"))
    paths += [ROOT / "kflate/build.gradle.kts", ROOT / "gradle/libs.versions.toml", Path(__file__).resolve()]
    return {str(path.relative_to(ROOT)): digest(path) for path in sorted(paths)}


def metadata_files():
    paths = [PERFORMANCE / "benchmark-metadata.jsonl", ROOT / "kflate/performance/benchmark-metadata.jsonl"]
    for directory in (ROOT / "build/wasm/packages", ROOT / "kflate/build/wasm/packages"):
        if directory.exists():
            paths.extend(directory.rglob("benchmark-metadata.jsonl"))
    return paths


def validate_baseline(baseline, target, smoke=False):
    if baseline["corpusSha256"] != corpus_hashes():
        raise ValueError("Corpus changed since baseline capture")
    if baseline["platform"] != target or baseline["smoke"] != smoke:
        raise ValueError("Baseline platform or measurement configuration does not match")
    expected = {f"{level}/{name}.deflate" for name in (["simpleText"] if smoke else CORPORA)
                for level in ([6] if smoke else range(10))}
    if set(baseline["fixtureSha256"]) != expected:
        raise ValueError("Incomplete baseline fixture matrix")
    for name, expected in baseline["fixtureSha256"].items():
        path = PERFORMANCE / "kompress-baseline" / target / "fixtures" / name
        if not path.exists() or digest(path) != expected:
            raise ValueError(f"Missing or changed Kompress fixture: {path}")


def capture(target, baseline, smoke, run_id):
    baseline_path = PERFORMANCE / "kompress-baseline" / target / ("smoke.json" if smoke else "baseline.json")
    if baseline and baseline_path.exists():
        saved = json.loads(baseline_path.read_text())
        validate_baseline(saved, target, smoke)
        print(f"Reusing {baseline_path}", flush=True)
        return
    saved = None
    if not baseline:
        saved = json.loads(baseline_path.read_text())
        validate_baseline(saved, target, smoke)
    directory = (baseline_path.parent / ("smoke-raw" if smoke else "raw")) if baseline else (
        PERFORMANCE / ("smoke-runs" if smoke else "runs") / run_id / target
    )
    if (directory / "result.json").exists():
        raise ValueError(f"Run already exists: {directory}")
    directory.mkdir(parents=True, exist_ok=True)
    for path in metadata_files():
        path.unlink(missing_ok=True)
    config = ("baselineSmoke" if smoke else "baseline") if baseline else ("smoke" if smoke else "main")
    suffix = "" if config == "main" else config[0].upper() + config[1:]
    task = f":kflate:{target}Benchmark{suffix}Benchmark"
    before_sources = source_hashes()
    before_corpora = corpus_hashes()
    started = datetime.now(timezone.utc).isoformat()
    print(f"Running {task}; log: {directory / 'benchmark.log'}", flush=True)
    with (directory / "benchmark.log").open("w") as log:
        subprocess.run([str(ROOT / "gradlew"), task, "--console=plain", "--no-parallel"], cwd=ROOT,
                       stdout=log, stderr=subprocess.STDOUT, check=True)
    if before_sources != source_hashes() or before_corpora != corpus_hashes():
        raise ValueError("Source or corpus changed during measurement")
    report_root = ROOT / "kflate/build/reports/benchmarks" / config
    reports = list(report_root.rglob(f"{target}Benchmark.json"))
    report = max(reports, key=lambda path: path.stat().st_mtime)
    if report.stat().st_mtime < datetime.fromisoformat(started).timestamp():
        raise ValueError("Benchmark task did not produce a fresh report")
    shutil.copyfile(report, directory / "benchmark.json")
    metadata = {}
    lines = []
    library = "Kompress" if baseline else "KFlate"
    for path in metadata_files():
        if not path.exists():
            continue
        for line in path.read_text().splitlines():
            row = json.loads(line)
            if row["platform"] != PLATFORMS[target] or row["library"] != library:
                continue
            key = (row["corpus"], row["level"])
            if key in metadata and metadata[key] != row:
                raise ValueError(f"Conflicting size metadata: {key}")
            metadata[key] = row
            lines.append(line)
    (directory / "metadata.jsonl").write_text("\n".join(sorted(set(lines))) + "\n")
    rows = []
    seen = set()
    expected = {(name, level, operation) for name in (["simpleText"] if smoke else CORPORA)
                for level in ([6] if smoke else range(10)) for operation in OPERATIONS.values()}
    for entry in json.loads(report.read_text()):
        expected_class = "KompressBaselineBenchmarks" if baseline else "CompressionBenchmarks"
        if f".{expected_class}." not in entry["benchmark"]:
            raise ValueError("Unexpected library in benchmark report")
        operation = OPERATIONS[entry["benchmark"].rsplit(".", 1)[1]]
        name, level = entry["params"]["corpus"], int(entry["params"]["level"])
        key = (name, level, operation)
        if key in seen:
            raise ValueError(f"Duplicate measurement: {key}")
        seen.add(key)
        size = metadata[name, level]
        rows.append({"corpus": name, "level": level, "operation": operation,
                     "originalSizeBytes": size["originalSizeBytes"],
                     "compressedSizeBytes": size["compressedSizeBytes"],
                     "metric": metric_from_entry(entry)})
    if seen != expected:
        raise ValueError(f"Incomplete measurement matrix: missing {expected - seen}, extra {seen - expected}")
    fixture_dir = PERFORMANCE / "kompress-baseline" / target / "fixtures"
    fixtures = {f"{level}/{name}.deflate": digest(fixture_dir / str(level) / f"{name}.deflate")
                for name, level in metadata}
    result = {"schemaVersion": 2, "runId": run_id, "startedAt": started,
              "finishedAt": datetime.now(timezone.utc).isoformat(), "platform": target,
              "library": library, "smoke": smoke, "corpusSha256": corpus_hashes(),
              "fixtureSha256": fixtures, "sourceSha256": before_sources,
              "host": {"name": platform.node(), "os": platform.platform(), "machine": platform.machine(),
                       "cpu": subprocess.check_output(["lscpu"], text=True) if shutil.which("lscpu") else platform.processor()},
              "dependencies": (ROOT / "gradle/libs.versions.toml").read_text(),
              "environment": [{k: v for k, v in entry.items() if k not in
                               ("benchmark", "params", "primaryMetric", "secondaryMetrics")}
                              for entry in json.loads(report.read_text())[:1]],
              "rows": rows}
    if saved is not None:
        validate_baseline(saved, target, smoke)
        result["baselineRunId"] = saved["runId"]
    write_json(directory / "result.json", result)
    if baseline:
        write_json(baseline_path, result)
    print(f"Saved {len(rows)} measurements to {directory}", flush=True)


def publish():
    baselines = {}
    for target in PLATFORMS:
        path = PERFORMANCE / "kompress-baseline" / target / "baseline.json"
        if path.exists():
            baselines[target] = json.loads(path.read_text())
            validate_baseline(baselines[target], target)
    runs = []
    for path in sorted((PERFORMANCE / "runs").glob("*/*/result.json")):
        result = json.loads(path.read_text())
        target = result["platform"]
        if target not in baselines or result["baselineRunId"] != baselines[target]["runId"]:
            raise ValueError(f"Run references an unavailable baseline: {path}")
        runs.append(result)
    write_json(PERFORMANCE / "history.json", {"schemaVersion": 2, "platforms": PLATFORMS,
                                            "baselines": baselines, "runs": runs})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["baseline", "run", "report"])
    parser.add_argument("--platforms", nargs="+", choices=PLATFORMS, default=list(PLATFORMS))
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--run-id", default=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%SZ"))
    args = parser.parse_args()
    if not args.run_id or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_." for c in args.run_id) or args.run_id in (".", ".."):
        parser.error("run-id must be a filename-safe identifier")
    PERFORMANCE.mkdir(exist_ok=True)
    with (PERFORMANCE / ".benchmark.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            parser.error("Another benchmark capture is using this checkout")
        if args.command != "report":
            for target in args.platforms:
                capture(target, args.command == "baseline", args.smoke, args.run_id)
        if not args.smoke:
            publish()


if __name__ == "__main__":
    main()
