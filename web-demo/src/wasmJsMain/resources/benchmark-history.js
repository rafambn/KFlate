const platform = document.querySelector('#platform');
const operation = document.querySelector('#operation');
const status = document.querySelector('#status');
const runs = document.querySelector('#runs');
let charts = [];

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function number(value) {
  return value == null ? 'Unavailable' : value.toLocaleString('en', { maximumSignificantDigits: 5 });
}

function render() {
  runs.replaceChildren();
  const selected = charts.filter(chart => chart.platform === platform.value && chart.operation === operation.value);
  document.querySelector('#axis-note').textContent = operation.value === 'compression'
    ? 'Compression stacks time above compressed bytes. Each plot has its own scale.' : 'Solid + points: average time in milliseconds.';
  status.textContent = selected.length ? '' : 'No saved runs for this platform yet.';
  status.hidden = selected.length > 0;
  const runIds = [...new Set(selected.slice().sort((a, b) => b.startedAt.localeCompare(a.startedAt)).map(chart => chart.runId))];
  for (const runId of runIds) {
    const section = element('section', undefined, 'run');
    section.append(element('h2', `Run ${runId}`));
    const runCharts = selected.filter(chart => chart.runId === runId);
    section.append(element('p', `Kompress baseline: ${runCharts[0].baselineRunId}`, 'baseline-date'));
    for (const chart of runCharts) {
      const article = element('article', undefined, 'chart');
      article.append(element('h3', chart.corpus));
      const chartLegend = element('div', undefined, 'chart-legend');
      chartLegend.setAttribute('role', 'group');
      chartLegend.setAttribute('aria-label', 'Library colors');
      chartLegend.append(element('span', 'KFlate', 'kflate'));
      chartLegend.append(element('span', 'Kompress', 'kompress'));
      article.append(chartLegend);
      const scroll = element('div', undefined, 'chart-scroll');
      const frame = element('iframe');
      frame.src = `plots/${chart.url}`;
      frame.title = `${chart.corpus}, ${operation.value}, ${runId}`;
      frame.className = operation.value === 'compression' ? 'compression-plot' : 'decompression-plot';
      frame.loading = 'lazy';
      scroll.append(frame);
      article.append(scroll);
      const details = element('details');
      details.append(element('summary', 'Measurements and uncertainty'));
      const tableScroll = element('div', undefined, 'chart-scroll');
      const table = element('table');
      table.append(element('caption', 'Average ± runner error. Percentiles describe iteration averages, not individual operation latency.'));
      const head = element('tr');
      const headings = ['Level', 'Library', 'Average ± error (ms)', 'p50 (ms)', 'p95 (ms)', 'Samples'];
      if (operation.value === 'compression') headings.push('Compressed bytes');
      for (const title of headings) { const th = element('th', title); th.scope = 'col'; head.append(th); }
      const thead = element('thead'); thead.append(head); table.append(thead);
      const tbody = element('tbody');
      for (let level = 0; level <= 9; level++) {
        for (const [library, rows] of [['KFlate', chart.rows], ['Kompress', chart.baselineRows]]) {
          const row = rows.find(row => row.level === level);
          const metric = row.metric;
          const tr = element('tr');
          const values = [level, library, `${number(metric.averageMs)} ± ${number(metric.errorMs)}`, number(metric.p50Ms), number(metric.p95Ms), metric.sampleCount];
          if (operation.value === 'compression') values.push(number(row.compressedSizeBytes));
          for (const value of values) tr.append(element('td', String(value)));
          tbody.append(tr);
        }
      }
      table.append(tbody); tableScroll.append(table); details.append(tableScroll); article.append(details);
      section.append(article);
    }
    runs.append(section);
  }
}

platform.addEventListener('change', render);
operation.addEventListener('change', render);
fetch('plots/index.json').then(response => {
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}).then(data => {
  if (!Array.isArray(data.charts)) throw new Error('Invalid chart index');
  charts = data.charts;
  platform.disabled = operation.disabled = false;
  if (charts.length) platform.value = charts[0].platform;
  document.querySelector('#download').hidden = !charts.length;
  render();
}).catch(() => {
  status.textContent = 'Benchmark results could not be loaded. Reload the page to try again.';
});
