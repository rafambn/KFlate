# KFlate

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Developers evaluating KFlate for a project.

## Product Purpose

KFlate is an experimental compression library written in Kotlin. The web demo lets developers try compression and decompression in WebAssembly and inspect recorded compression benchmarks.

## Capabilities and Constraints

The existing demo supports raw DEFLATE, GZIP, and ZLIB, compression levels 0–9, local file selection, and downloads of results. It uses a WebAssembly worker and limits input to 64 MiB. These capabilities are confirmed by the repository implementation.

The opening viewport must contain the compression demo with a hint linking to benchmarks below, at every screen size. Three design alternatives are requested.

## Brand Commitments

Preserve the KFlate name and repository logo palette. Describe the library factually as experimental. Avoid promotional copy, implied performance promises, and sales-page framing. Previous design proposals were rejected and carry no visual authority.

## Evidence on Hand

- `KFlate-Logo.svg` contains green, cyan, blue, and slate colors.
- `README.md` documents the actual library API and formats.
- `performance/latest.json` contains recorded benchmark data.
- `web-demo/src/wasmJsMain/resources/demo.js` contains the working browser demo.

## Product Principles

- Help developers evaluate the library through working behavior.
- Preserve the distinction between observed results and claims.
- Keep compression controls usable on narrow and short screens.
- Use literal, concise copy.
