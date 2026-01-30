# API Design

## Checksums

Multiplatform checksum implementations:

- **`Crc32Checksum`**: Used by GZIP format
- **`Adler32Checksum`**: Used by ZLIB format
- Both implement `ChecksumGenerator` interface

## Format Utilities

Separate modules handle format-specific logic:

- **`GzipUtils.kt`**: GZIP header reading/writing, file metadata, checksum computation
- **`ZlibUtils.kt`**: ZLIB header parsing, dictionary support, checksum validation
- **`HuffmanCode.kt`**: Huffman tree construction and code generation

## Options and Configuration

Configuration classes allow customization of compression/decompression:

- **`DeflateOptions`**: Compression level (0-9), optional memory setting, optional dictionary
- **`GzipOptions`**: Extends DeflateOptions with GZIP-specific metadata
- **`InflateOptions`**: Decompression-only options (optional dictionary for ZLIB)

## Error Handling

Compression errors are represented as exceptions:

- **`FlateError`**: Exception class wrapping an error code
- **`FlateErrorCode`**: Enum defining five error types
  - `UNEXPECTED_EOF`: Incomplete compressed data
  - `INVALID_BLOCK_TYPE`: Corrupted block header
  - `INVALID_LENGTH_LITERAL`: Malformed Huffman tree
  - `INVALID_DISTANCE`: Invalid back-reference distance
  - `INVALID_HEADER`: Corrupted GZIP/ZLIB header
