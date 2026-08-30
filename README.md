<h1 align="center">KFlate</h1>

<p align="center">Pure Kotlin Multiplatform DEFLATE, GZIP, and ZLIB compression.</p>

<p align="center">
  <img src="KFlate-Logo.svg" alt="KFlate logo" width="200" height="200">
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.rafambn/KFlate">
    <img alt="Maven Central" src="https://img.shields.io/maven-central/v/com.rafambn/KFlate?label=Maven%20Central">
  </a>
  <a href="LICENSE">
    <img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-blue.svg">
  </a>
  <img alt="Platform targets" src="https://img.shields.io/badge/targets-android%20%7C%20jvm%20%7C%20js%20%7C%20wasm%20%7C%20ios%20%7C%20macos%20%7C%20linux%20%7C%20windows-0A7EA4">
</p>

<p align="center">
  KFlate is an independently written Kotlin implementation based on the design and API ideas of
  <a href="https://github.com/101arrowz/fflate"><code>fflate</code></a>.
</p>

<table align="center">
  <tr>
    <td align="center">
      <a href="https://kflate.rafambn.com"><strong>KFlate Web Compressor (Wasm)</strong></a>
    </td>
  </tr>
</table>

## Features

- Raw DEFLATE, RFC 1952 GZIP, and RFC 1950 ZLIB.
- Blocking `ByteArray` and streaming `kotlinx-io` APIs.
- Compression levels 0 through 9 and configurable hash-table memory.
- Preset dictionaries for raw DEFLATE and ZLIB.
- GZIP filename, comment, extra fields, modification time, and header CRC.
- JVM, Android, JS, Wasm, and Kotlin/Native targets.

## Setup

Add KFlate to `commonMain`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.rafambn:KFlate:1.1.0")
        }
    }
}
```

## Blocking API

```kotlin
val input = "hello".encodeToByteArray()

val deflated = KFlate.compress(input, RawCompression())
val inflated = KFlate.decompress(deflated, RawDecompression())

val gzip = KFlate.compress(
    input,
    GzipCompression(
        filename = "hello.txt",
        comment = "example",
        extraFields = mapOf("AB" to byteArrayOf(1, 2)),
        includeHeaderCrc = true,
    ),
)
val ungzipped = KFlate.decompress(gzip, GzipDecompression())

val dictionary = "common bytes".encodeToByteArray()
val zlib = KFlate.compress(input, ZlibCompression(dictionary = dictionary))
val unzlib = KFlate.decompress(
    zlib,
    ZlibDecompression(dictionary = dictionary),
)
```

GZIP does not support preset dictionaries because RFC 1952 has no interoperable way to declare one.

## Streaming API

The streaming overloads read a `RawSource`, write to a `RawSink`, and flush the buffered sink. KFlate does not close either resource.

```kotlin
KFlate.compress(
    options = ZlibCompression(level = 6),
    source = inputSource,
    sink = compressedSink,
)

KFlate.decompress(
    options = ZlibDecompression(maxOutputSize = 64 * 1024 * 1024),
    source = compressedSource,
    sink = outputSink,
)
```

If streaming decompression fails, bytes already written to the sink are not rolled back.

## Options

`RawCompression`, `GzipCompression`, and `ZlibCompression` accept:

- `level`: compression level from 0 to 9. The default is 6.
- `mem`: hash-table memory level from 0 to 12. The default is 8.

The hash table uses approximately 8 KiB at `mem = 0`, 128 KiB at `mem = 4`, 2 MiB at `mem = 8`, and 32 MiB at `mem = 12`. Compression also needs a 64 KiB history table and temporary input/output buffers. For a blocking call with the default `mem = 8`, KFlate can select a smaller table for small inputs.

Raw DEFLATE and ZLIB compression/decompression also accept a preset `dictionary` of at most 32 KiB. The decompressor must receive the same dictionary.

`GzipCompression` additionally accepts:

- `filename` and `comment`: ISO-8859-1 header text without NUL characters.
- `extraFields`: two-byte ISO-8859-1 field IDs mapped to at most 65,535 bytes in total.
- `mtime`: a `kotlin.time.Instant`; `null` writes the current time and epoch zero writes a reproducible zero timestamp.
- `includeHeaderCrc`: writes the optional GZIP header CRC16.

All decompression configurations accept `maxOutputSize`. Set it for untrusted data to limit decompressed output and mitigate compression bombs.

## Errors and validation

Malformed, truncated, or oversized compressed data throws `FlateError`. Its `code` property contains a `FlateErrorCode`, including `UNEXPECTED_EOF`, checksum errors, and `OUTPUT_LIMIT_EXCEEDED`.

```kotlin
try {
    KFlate.decompress(data, GzipDecompression(maxOutputSize = 16 * 1024 * 1024))
} catch (error: FlateError) {
    println(error.code)
}
```

## Migrating to 1.1.0

Version 1.1.0 replaces names that differed only by letter case. This avoids class-file collisions on case-insensitive filesystems.

| 1.0.x | 1.1.0 |
|---|---|
| `CompressionType` | `CompressionOptions` |
| `DecompressionType` | `DecompressionOptions` |
| `RAW` / `Raw` | `RawCompression` / `RawDecompression` |
| `GZIP` / `Gzip` | `GzipCompression` / `GzipDecompression` |
| `ZLIB` / `Zlib` | `ZlibCompression` / `ZlibDecompression` |

The 1.1.0 GZIP configuration no longer exposes the non-standard dictionary option, and `mtime` now uses `kotlin.time.Instant` instead of `Any`.

## Benchmarks

Correctness tests are separate from the `kotlinx-benchmark` suite. See [BENCHMARKING.md](BENCHMARKING.md) for JVM, Linux Native, and Wasm commands, corpus definitions, and result interpretation.

## License

KFlate is available under the [Apache License 2.0](LICENSE).
