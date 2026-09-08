const platform = document.getElementById('benchmark-platform');
const operation = document.getElementById('benchmark-operation');
const status = document.getElementById('benchmark-status');
const baseline = { 'JVM': 'java.util.zip', 'Linux x64 Native': 'zlib', 'Wasm/JS': 'fflate' };
const number = new Intl.NumberFormat('en', { maximumFractionDigits: 0 });
const timing = new Intl.NumberFormat('en', { minimumFractionDigits: 4, maximumFractionDigits: 4 });

function time(metric) {
  const mean = timing.format(metric.averageMs);
  return Number.isFinite(metric.errorMs) ? `${mean} ± ${timing.format(metric.errorMs)}` : mean;
}

function cell(row, tag, text) {
  const element = document.createElement(tag);
  element.textContent = text;
  if (tag === 'th') element.scope = 'col';
  row.append(element);
}

function render(data) {
  const compressing = operation.value === 'compression';
  document.getElementById('benchmark-baseline').textContent = `Baseline: Kompress using ${baseline[platform.value]}.`;
  const head = document.createElement('tr');
  let rows;
  const results = data[operation.value].filter(row => row.platform === platform.value);
  if (compressing) {
    ['Fixture', 'KFlate [ms]', 'Kompress [ms]', 'KFlate [bytes]', 'Kompress [bytes]']
      .forEach(text => cell(head, 'th', text));
    document.getElementById('benchmark-head').replaceChildren(head);
    rows = results.map(result => {
      const row = document.createElement('tr');
      cell(row, 'td', result.corpus);
      cell(row, 'td', time(result.kflate));
      cell(row, 'td', time(result.kompress));
      cell(row, 'td', number.format(result.kflateCompressedSizeBytes));
      cell(row, 'td', number.format(result.kompressCompressedSizeBytes));
      return row;
    });
  } else {
    cell(head, 'th', 'Fixture');
    head.lastElementChild.rowSpan = 2;
    for (const name of ['KFlate-produced stream', 'Kompress-produced stream']) {
      cell(head, 'th', name);
      head.lastElementChild.colSpan = 2;
      head.lastElementChild.scope = 'colgroup';
    }
    const decoders = document.createElement('tr');
    for (const name of ['KFlate [ms]', 'Kompress [ms]', 'KFlate [ms]', 'Kompress [ms]']) cell(decoders, 'th', name);
    document.getElementById('benchmark-head').replaceChildren(head, decoders);
    rows = [...new Set(results.map(result => result.corpus))].map(corpus => {
      const row = document.createElement('tr');
      cell(row, 'td', corpus);
      for (const producer of ['KFlate', 'Kompress']) {
        const result = results.find(result => result.corpus === corpus && result.producer === producer);
        cell(row, 'td', time(result.kflate));
        cell(row, 'td', time(result.kompress));
      }
      return row;
    });
  }
  document.getElementById('benchmark-rows').replaceChildren(...rows);
}

try {
  const response = await fetch(new URL('benchmark-results.json', import.meta.url));
  if (!response.ok) throw new Error(`Benchmark response ${response.status}`);
  const data = await response.json();
  const commit = data.benchmarkCommit;
  if (!/^[a-f0-9]{40}$/.test(commit) || !data.runId || !data.compression?.length || !data.decompression?.length) {
    throw new Error('Incomplete benchmark snapshot');
  }
  const link = document.createElement('a');
  link.href = `https://github.com/rafambn/KFlate/commit/${commit}`;
  link.textContent = commit.slice(0, 7);
  document.getElementById('benchmark-run').append(`Run ${data.runId.slice(0, 10)} · Commit `, link);
  render(data);
  for (const control of [platform, operation]) control.addEventListener('change', () => render(data));
  status.hidden = true;
  document.getElementById('benchmark-content').hidden = false;
} catch (error) {
  status.textContent = 'Benchmark results are unavailable. Please try reloading the page.';
  console.error(error);
}
