// Serve the raw Kotlin-compiled ESM files at the root so index.html's web worker
// can import kflate-demo.mjs directly (e.g. import * as kflate from '.../kflate-demo.mjs')
const path = require('path');
const kotlinDir = path.resolve(__dirname, 'kotlin');
config.devServer = config.devServer || {};
const existing = config.devServer.static;
config.devServer.static = [
    ...(Array.isArray(existing) ? existing : existing ? [existing] : []),
    { directory: kotlinDir, publicPath: '/' }
];
