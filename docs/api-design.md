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

## Types and Configuration

Sealed interfaces define the compression and decompression types with their specific configurations:

- **`CompressionType`**: Sealed interface for `RAW`, `GZIP`, and `ZLIB`
  - `RAW`: Compression level (0-9), internal buffer size, optional dictionary
  - `GZIP`: Same as RAW plus metadata (filename, comment, mtime, extra fields) and header CRC option
  - `ZLIB`: Same as RAW
- **`DecompressionType`**: Sealed interface for `Raw`, `Gzip`, and `Zlib`
  - All support an optional dictionary for ZLIB decompression

## Error Handling

Compression errors are represented as exceptions:

- **`FlateError`**: Exception class wrapping an error code
- **`FlateErrorCode`**: Enum defining five error types
  - `UNEXPECTED_EOF`: Incomplete compressed data
  - `INVALID_BLOCK_TYPE`: Corrupted block header
  - `INVALID_LENGTH_LITERAL`: Malformed Huffman tree
  - `INVALID_DISTANCE`: Invalid back-reference distance
  - `INVALID_HEADER`: Corrupted GZIP/ZLIB header
