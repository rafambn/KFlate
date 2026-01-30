# Architecture Overview

## Entry Point

The public API is exposed through the `KFlate` singleton object (`KFlate.kt`), which contains three sub-objects:

1. **`KFlate.Raw`**: Raw DEFLATE compression/decompression
   - `inflate(data, options)`: Decompress DEFLATE data
   - `deflate(data, options)`: Compress to DEFLATE format

2. **`KFlate.Gzip`**: GZIP format with headers, checksum, and metadata
   - `compress(data, options)`: Compress with GZIP headers (CRC32 checksum)
   - `decompress(data, options)`: Decompress GZIP format

3. **`KFlate.Zlib`**: ZLIB format with headers and optional dictionary support
   - `compress(data, options)`: Compress with ZLIB headers (Adler32 checksum)
   - `decompress(data, options)`: Decompress ZLIB format

## Core Compression Algorithm

The actual compression/decompression logic resides in `flate.kt`:

- **`inflate()`**: Decompresses DEFLATE streams. Handles three block types:
  - Type 0: Uncompressed (stored) blocks
  - Type 1: Fixed Huffman codes
  - Type 2: Dynamic Huffman codes with DEFLATE tree encoding
  - Supports stateful decompression via `InflateState` for streaming use cases

- **`deflate()`**: Compresses data using configurable compression levels (0-9)
  - Maintains compression state via `DeflateState`
  - Uses Huffman encoding with dynamic trees
  - Implements lazy matching for better compression

## State Management

Two state classes enable incremental compression/decompression:

- **`InflateState`**: Tracks decompression progress
  - Huffman code maps and bit positions
  - Output buffer progress
  - Block type and finalization flags
  - Used internally for stateful decompression

- **`DeflateState`**: Tracks compression progress
  - Hash tables for string matching (head/prev arrays)
  - Current position in input stream
  - Used by deflate algorithm for incremental encoding
