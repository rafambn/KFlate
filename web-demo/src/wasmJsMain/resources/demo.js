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

    // ── DOM refs ─────────────────────────────────────────────────────────────
    const dot          = document.getElementById('wasm-dot');
    const statusText   = document.getElementById('wasm-status-text');
    const tabs         = document.querySelectorAll('.tab-btn');
    const levelGroup   = document.getElementById('level-group');
    const levelInput   = document.getElementById('level-input');
    const levelValue   = document.getElementById('level-value');
    const dropZone     = document.getElementById('drop-zone');
    const fileInputEl  = document.getElementById('file-input');
    const dropHint     = document.getElementById('drop-hint');
    const fileNameEl   = document.getElementById('file-name');
    const fileSizeEl   = document.getElementById('file-size');
    const processBtn   = document.getElementById('process-btn');
    const spinner      = document.getElementById('spinner');
    const errorMsg     = document.getElementById('error-msg');
    const modalBackdrop = document.getElementById('modal-backdrop');
    const modalClose   = document.getElementById('modal-close');

    let mode = 'compress';
    let selectedFile = null;
    let currentObjectUrl = null;

    // ── Tab switching ─────────────────────────────────────────────────────────
    tabs.forEach(btn => btn.addEventListener('click', () => {
      tabs.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      mode = btn.dataset.mode;
      levelGroup.style.display = mode === 'compress' ? '' : 'none';
      processBtn.textContent = mode === 'compress' ? 'Compress' : 'Decompress';
      updateBtn();
    }));

    // ── Level slider ──────────────────────────────────────────────────────────
    levelInput.addEventListener('input', () => {
      levelValue.textContent = levelInput.value;
    });

    // ── File drag / drop / pick ───────────────────────────────────────────────
    const MAX_INPUT_BYTES = 64 * 1024 * 1024;

    dropZone.addEventListener('click', () => fileInputEl.click());

    dropZone.addEventListener('dragover', e => {
      e.preventDefault();
      dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));

    dropZone.addEventListener('drop', e => {
      e.preventDefault();
      dropZone.classList.remove('drag-over');
      const f = e.dataTransfer.files[0];
      if (f) setFile(f);
    });

    fileInputEl.addEventListener('change', () => {
      if (fileInputEl.files[0]) setFile(fileInputEl.files[0]);
    });

    function setFile(f) {
      hideError();
      if (f.size > MAX_INPUT_BYTES) {
        clearSelectedFile();
        showError(`File is too large (${fmtSize(f.size)}). Maximum allowed size is 64 [MiB].`);
        return;
      }
      selectedFile = f;
      dropHint.style.display = 'none';
      fileNameEl.style.display = '';
      fileSizeEl.style.display = '';
      fileNameEl.textContent = f.name;
      fileSizeEl.textContent = fmtSize(f.size);
      updateBtn();
    }

    function clearSelectedFile() {
      selectedFile = null;
      fileInputEl.value = '';
      dropHint.style.display = '';
      fileNameEl.style.display = 'none';
      fileSizeEl.style.display = 'none';
      updateBtn();
    }

    // ── Process ───────────────────────────────────────────────────────────────
    processBtn.addEventListener('click', async () => {
      if (!selectedFile || !wasmReady) return;
      hideError();
      setProcessing(true);

      const file = selectedFile;
      const action = mode;
      const format = document.querySelector('input[name=format]:checked').value;
      const level = parseInt(levelInput.value, 10);
      const t0 = performance.now();
      try {
        const buf = await file.arrayBuffer();

        const reply = await callWorker({ action, input: buf, format, level });

        if (reply.error) {
          showErrorModal(reply.error, action);
          return;
        }

        const elapsed = performance.now() - t0;
        showResult(reply.result, file.size, reply.result.byteLength, elapsed, file.name, format, action);
      } catch (e) {
        showErrorModal(e.message || String(e), action);
      } finally {
        setProcessing(false);
      }
    });

    // ── Result modal ──────────────────────────────────────────────────────────
    function showResult(buffer, inBytes, outBytes, ms, origName, format, action) {
      if (currentObjectUrl) URL.revokeObjectURL(currentObjectUrl);
      const blob = new Blob([buffer]);
      currentObjectUrl = URL.createObjectURL(blob);
      const dlName = outFilename(origName, format, action);

      document.getElementById('modal-title').innerHTML =
        action === 'compress' ? '<span>Compressed</span> successfully' : '<span>Decompressed</span> successfully';
      document.getElementById('modal-in').textContent    = fmtSize(inBytes);
      document.getElementById('modal-out').textContent   = fmtSize(outBytes);
      document.getElementById('modal-ratio').textContent =
        inBytes > 0 ? ((outBytes / inBytes) * 100).toFixed(1) + ' [%]' : '—';
      document.getElementById('modal-time').textContent  =
        ms >= 1000 ? (ms / 1000).toFixed(2) + ' [s]' : Math.round(ms) + ' [ms]';

      const dl = document.getElementById('modal-dl');
      dl.href     = currentObjectUrl;
      dl.download = dlName;
      dl.textContent = '⬇ Save  ' + dlName;

      modalBackdrop.classList.remove('error');
      modalBackdrop.classList.add('open');
    }

    function showErrorModal(msg, action) {
      document.getElementById('modal-title').innerHTML =
        action === 'compress' ? '<span>Compression</span> failed' : '<span>Decompression</span> failed';
      document.getElementById('modal-error-text').textContent = msg;
      modalBackdrop.classList.add('error');
      modalBackdrop.classList.add('open');
    }

    function closeModal() {
      modalBackdrop.classList.remove('open');
      modalBackdrop.classList.remove('error');
      if (currentObjectUrl) {
        URL.revokeObjectURL(currentObjectUrl);
        currentObjectUrl = null;
      }
    }

    modalClose.addEventListener('click', closeModal);
    modalBackdrop.addEventListener('click', e => { if (e.target === modalBackdrop) closeModal(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

    // ── Helpers ───────────────────────────────────────────────────────────────
    function outFilename(name, format, mode) {
      if (mode === 'compress') {
        const ext = { raw: '.deflate', gzip: '.gz', zlib: '.zlib' }[format] ?? '.bin';
        return name + ext;
      }
      return name.replace(/\.(gz|gzip|zlib|deflate|zz)$/i, '') || name + '.out';
    }

    function setProcessing(on) {
      spinner.style.display = on ? 'block' : 'none';
      if (on) processBtn.disabled = true;
      else updateBtn();
    }

    function updateBtn() {
      processBtn.disabled = !(wasmReady && selectedFile);
    }

    function showError(msg) {
      errorMsg.textContent = msg;
      errorMsg.style.display = '';
    }

    function hideError() {
      errorMsg.style.display = 'none';
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    function fmtSize(bytes) {
      if (bytes < 1024)         return bytes + ' [bytes]';
      if (bytes < 1024 ** 2)    return (bytes / 1024).toFixed(1) + ' [KiB]';
      if (bytes < 1024 ** 3)    return (bytes / 1024 ** 2).toFixed(2) + ' [MiB]';
      return (bytes / 1024 ** 3).toFixed(2) + ' [GiB]';
    }
