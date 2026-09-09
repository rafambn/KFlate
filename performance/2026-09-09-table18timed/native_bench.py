import json
from pathlib import Path
import subprocess
import sys

binary = str(Path(sys.argv[1]).resolve())
directory = Path(sys.argv[2]).resolve()
directory.mkdir(parents=True, exist_ok=True)
config = directory / 'runner.cfg'
config.write_text('\n'.join([
    'name:ratio', f'reportFile:{directory}/benchmark.json',
    'traceFormat:text', 'reportFormat:json',
    'include:' + (sys.argv[5] if len(sys.argv) > 5 else '.*CompressionBenchmarks.rawDeflateCompression'),
    f'warmups:{sys.argv[3] if len(sys.argv) > 3 else 3}',
    f'iterations:{sys.argv[4] if len(sys.argv) > 4 else 5}',
    'iterationTime:1', 'iterationTimeUnit:sec',
    'outputTimeUnit:sec', 'mode:avgt',
]))

def run(action, *args):
    subprocess.run([binary, str(config), action, str(directory / 'trace.xml'), *map(str, args)], check=True)

run('--list', directory)
configs = sorted(directory.glob('*.txt'))
expected = 14 if len(sys.argv) > 5 and 'Decompression' in sys.argv[5] else 7
assert len(configs) == expected, configs
results = []
for benchmark_config in configs:
    samples = benchmark_config.with_suffix('.samples')
    run('--benchmark', benchmark_config, samples)
    values = samples.read_text().strip()
    assert values and 'null' not in values
    results.append(f'{benchmark_config}: {values}')
combined = directory / 'results'
combined.write_text('\n'.join(results))
run('--store-results', combined)
rows = json.loads((directory / 'benchmark.json').read_text())
assert len(rows) == expected
