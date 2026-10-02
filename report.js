    // Base URL so the blob-URL worker can resolve absolute imports
    const BASE_URL = new URL('.', import.meta.url).href;

    // ── Web Worker (inline, loaded as ESM via blob URL) ──────────────────────
    const workerSrc = `
import * as kflate from '${BASE_URL}kflate-demo.mjs';

// Signal that the WASM module is initialised (top-level await has resolved)
self.postMessage({ type: 'ready' });

self.onmessage = ({ data: msg }) => {
  const { id, action, input, format, level } = msg;
  try {
    kflate.loadInput(new Uint8Array(input));
    const size = action === 'compress'
      ? kflate.runCompress(format, level ?? 6)
      : kflate.runDecompress(format);
    if (size < 0) {
      self.postMessage({ id, error: kflate.getLastError() || 'Operation failed' });
      return;
    }
    const out = kflate.getOutput();
    self.postMessage({ id, result: out.buffer }, [out.buffer]);
  } catch (e) {
    self.postMessage({ id, error: e.message || String(e) });
  }
};
`;

    const blobUrl = URL.createObjectURL(
      new Blob([workerSrc], { type: 'application/javascript' })
    );
    const worker = new Worker(blobUrl, { type: 'module' });

    const pending = new Map();
    let nextId = 1;
    let wasmReady = false;

    worker.onmessage = ({ data }) => {
      if (data.type === 'ready') {
        URL.revokeObjectURL(blobUrl);
        wasmReady = true;
        dot.className = 'dot ready';
        statusText.textContent = 'WASM ready';
        updateBtn();
        return;
      }
      const request = pending.get(data.id);
      if (request) {
        pending.delete(data.id);
        request.resolve(data);
      }
    };

    function failWorker(message) {
      URL.revokeObjectURL(blobUrl);
      wasmReady = false;
      dot.className = 'dot error';
      statusText.textContent = 'WASM unavailable';
      for (const { reject } of pending.values()) reject(new Error(message));
      pending.clear();
      showError(message);
      updateBtn();
    }

    worker.onerror = e => failWorker(
      'WebAssembly worker failed: ' + (e.message || 'unknown error')
    );
    worker.onmessageerror = () => failWorker('The WebAssembly worker returned an unreadable response.');

    function callWorker(msg) {
      return new Promise((resolve, reject) => {
        const id = nextId++;
        pending.set(id, { resolve, reject });
        const transfer = msg.input instanceof ArrayBuffer ? [msg.input] : [];
        try {
          worker.postMessage({ ...msg, id }, transfer);
        } catch (error) {
          pending.delete(id);
          reject(error);
        }
      });
    }


const byId = id => document.getElementById(id);
const dot = byId('wasm-dot');
const statusText = byId('wasm-status-text');
const processButton = byId('process-btn');
const picker = byId('file-input');
const dropZone = byId('drop-zone');
const chooseButton = byId('choose-file');
const errorMessage = byId('error-msg');
const resultPanel = document.querySelector('.result-panel');
const resultStatus = byId('result-status');
const download = byId('download-result');
const levelInput = byId('level-input');
const operationButtons = [...document.querySelectorAll('.tab-btn')];
const formatInputs = [...document.querySelectorAll('input[name="format"]')];
const MAX_INPUT_BYTES = 64 * 1024 * 1024;
let selectedFile = null;
let mode = 'compress';
let processing = false;
let resultUrl = null;

function updateBtn() {
  processButton.disabled = !wasmReady || !selectedFile || processing;
}

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
}

function hideError() {
  errorMessage.hidden = true;
  errorMessage.textContent = '';
}

function clearResult() {
  if (resultUrl) URL.revokeObjectURL(resultUrl);
  resultUrl = null;
  download.removeAttribute('href');
  download.removeAttribute('download');
  download.classList.add('disabled');
  download.setAttribute('aria-disabled', 'true');
  download.tabIndex = -1;
  for (const id of ['result-in', 'result-out', 'result-ratio', 'result-time']) byId(id).textContent = '—';
  byId('output-name').hidden = true;
  byId('output-name').textContent = '';
  resultPanel.dataset.state = 'empty';
  resultStatus.textContent = `Run ${mode === 'compress' ? 'compression' : 'decompression'} to inspect the result`;
}

function selectFile(file) {
  if (processing) return;
  hideError();
  clearResult();
  if (file.size > MAX_INPUT_BYTES) {
    selectedFile = null;
    picker.value = '';
    dropZone.classList.remove('has-file');
    byId('file-name').hidden = true;
    byId('file-size').hidden = true;
    byId('drop-hint').hidden = false;
    chooseButton.textContent = 'Choose a file';
    showError(`This file is ${formatSize(file.size)}. Choose a file no larger than 64 MiB.`);
  } else {
    selectedFile = file;
    dropZone.classList.add('has-file');
    byId('file-name').textContent = file.name;
    byId('file-name').hidden = false;
    byId('file-size').textContent = formatSize(file.size);
    byId('file-size').hidden = false;
    byId('drop-hint').hidden = true;
    chooseButton.textContent = 'Change file';
  }
  updateBtn();
}

chooseButton.addEventListener('click', () => picker.click());
picker.addEventListener('change', () => {
  if (picker.files[0]) selectFile(picker.files[0]);
});
for (const name of ['dragenter', 'dragover']) dropZone.addEventListener(name, event => {
  event.preventDefault();
  if (!processing) dropZone.classList.add('drag-over');
});
dropZone.addEventListener('dragleave', event => {
  if (!dropZone.contains(event.relatedTarget)) dropZone.classList.remove('drag-over');
});
dropZone.addEventListener('drop', event => {
  event.preventDefault();
  dropZone.classList.remove('drag-over');
  if (event.dataTransfer.files[0]) selectFile(event.dataTransfer.files[0]);
});
// Keep a dropped file outside the target from replacing the current page.
document.addEventListener('dragover', event => {
  if (event.dataTransfer.types.includes('Files')) event.preventDefault();
});
document.addEventListener('drop', event => {
  if (event.dataTransfer.types.includes('Files')) event.preventDefault();
});

operationButtons.forEach(button => button.addEventListener('click', () => {
  if (processing || mode === button.dataset.mode) return;
  mode = button.dataset.mode;
  operationButtons.forEach(other => {
    other.classList.toggle('active', other === button);
    other.setAttribute('aria-pressed', String(other === button));
  });
  byId('level-group').hidden = mode === 'decompress';
  processButton.textContent = mode === 'compress' ? 'Compress' : 'Decompress';
  hideError();
  clearResult();
}));
formatInputs.forEach(input => input.addEventListener('change', () => {
  hideError();
  clearResult();
}));
levelInput.addEventListener('input', () => {
  byId('level-value').textContent = levelInput.value;
  levelInput.style.setProperty('--fill', `${Number(levelInput.value) / 9 * 100}%`);
  hideError();
  clearResult();
});
download.addEventListener('click', event => {
  if (!resultUrl) event.preventDefault();
});

processButton.addEventListener('click', async () => {
  if (!selectedFile || !wasmReady || processing) return;
  hideError();
  clearResult();
  const file = selectedFile;
  const format = formatInputs.find(input => input.checked).value;
  const level = Number(levelInput.value);
  setProcessing(true);
  resultStatus.textContent = mode === 'compress' ? 'Compressing file…' : 'Decompressing file…';
  resultPanel.dataset.state = 'processing';
  const start = performance.now();
  try {
    const input = await file.arrayBuffer();
    const reply = await callWorker({action: mode, input, format, level});
    if (reply.error) throw new Error(reply.error);
    const elapsed = performance.now() - start;
    const output = reply.result;
    resultUrl = URL.createObjectURL(new Blob([output]));
    const filename = outputFilename(file.name, format, mode);
    byId('result-in').textContent = formatSize(file.size);
    byId('result-out').textContent = formatSize(output.byteLength);
    byId('result-ratio').textContent = file.size ? `${(output.byteLength / file.size * 100).toFixed(1)}%` : '—';
    byId('result-time').textContent = elapsed >= 1000 ? `${(elapsed / 1000).toFixed(2)} s` : `${elapsed.toFixed(1)} ms`;
    byId('output-name').textContent = filename;
    byId('output-name').hidden = false;
    download.href = resultUrl;
    download.download = filename;
    download.classList.remove('disabled');
    download.removeAttribute('aria-disabled');
    download.removeAttribute('tabindex');
    resultPanel.dataset.state = 'success';
    resultStatus.textContent = mode === 'compress' ? 'Compression complete' : 'Decompression complete';
  } catch (error) {
    resultPanel.dataset.state = 'error';
    resultStatus.textContent = `${mode === 'compress' ? 'Compression' : 'Decompression'} failed`;
    const recovery = mode === 'decompress' ? 'Check that the selected format matches the file, or choose another file.' : 'Try another file. If the problem continues, reload the demo.';
    showError(`${error.message || 'The operation could not finish.'} ${recovery}`);
  } finally {
    setProcessing(false);
  }
});

function setProcessing(value) {
  processing = value;
  resultPanel.setAttribute('aria-busy', String(value));
  for (const control of [chooseButton, picker, levelInput, ...operationButtons, ...formatInputs]) control.disabled = value;
  processButton.textContent = value ? (mode === 'compress' ? 'Compressing…' : 'Decompressing…') : (mode === 'compress' ? 'Compress' : 'Decompress');
  updateBtn();
}

function outputFilename(name, format, action) {
  if (action === 'compress') return name + {raw: '.deflate', gzip: '.gz', zlib: '.zlib'}[format];
  const stripped = name.replace(/\.(gz|gzip|zlib|deflate|zz)$/i, '');
  return stripped && stripped !== name ? stripped : name + '.out';
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} bytes`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 ** 2).toFixed(2)} MiB`;
}

window.addEventListener('pagehide', () => {
  if (resultUrl) URL.revokeObjectURL(resultUrl);
});
