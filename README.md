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
  KFlate is an independently written Kotlin implementation based on the design and API ideas of the npm <a href="https://github.com/101arrowz/fflate"><code>fflate</code></a> library.
</p>

<table align="center">
  <tr>
    <td align="center">
      <a href="https://kflate.rafambn.com"><strong>KFlate Web Compressor using Wasm</strong></a>
    </td>
  </tr>
</table>

## Features

- Raw DEFLATE, RFC 1952 GZIP, and RFC 1950 ZLIB.
- Blocking `ByteArray` and streaming `kotlinx-io` APIs.
- Compression levels from 0 through 9 with automatic hash-table sizing.
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

## Imports

Compression and decompression formats use the same short names in separate packages. Kotlin import aliases keep both sides explicit:

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import com.rafambn.kflate.error.FlateError
```

## Blocking API

```kotlin
val input = "hello".encodeToByteArray()

val deflated = KFlate.compress(input, CompressionRaw())
val inflated = KFlate.decompress(deflated, DecompressionRaw())

val gzip = KFlate.compress(
    input,
    CompressionGzip(
        filename = "hello.txt",
        comment = "example",
        extraFields = mapOf("AB" to byteArrayOf(1, 2)),
        includeHeaderCrc = true,
    ),
)
val ungzipped = KFlate.decompress(gzip, DecompressionGzip())

val dictionary = "common bytes".encodeToByteArray()
val zlib = KFlate.compress(input, CompressionZlib(dictionary = dictionary))
val unzlib = KFlate.decompress(
    zlib,
    DecompressionZlib(dictionary = dictionary),
)
```

GZIP does not support preset dictionaries because RFC 1952 has no interoperable field for one.

## Streaming API

The streaming overloads read a `RawSource`, write to a `RawSink`, and flush the buffered sink. KFlate does not close either resource. A decompression failure may leave bytes already written to the sink.

```kotlin
KFlate.compress(
    type = CompressionZlib(level = 6),
    source = inputSource,
    sink = compressedSink,
)

KFlate.decompress(
    type = DecompressionZlib(maxOutputSize = 64 * 1_024 * 1_024),
    source = compressedSource,
    sink = outputSink,
)
```

## Options

All compression formats accept `level` from 0 through 9. The default is 6.

- 0: No compression
- 1–3: Greedy parsing
- 4–8: Lazy parsing
- 9: Cost-aware parsing

KFlate sizes the hash table automatically from the compression level and input size. Streaming compression keeps a fixed level-based hash-table size for the entire stream.

Raw DEFLATE and ZLIB also accept a preset `dictionary` of at most 32 KiB. Decompression requires the same dictionary.

GZIP compression additionally accepts:

- `filename` and `comment`: ISO-8859-1 header text without NUL characters.
- `extraFields`: two-byte ISO-8859-1 field IDs mapped to at most 65,535 bytes in total.
- `mtime`: a `kotlin.time.Instant` within the unsigned 32-bit GZIP timestamp range. `null` writes the current time.
- `includeHeaderCrc`: writes the optional GZIP header CRC16.

All decompression formats accept `maxOutputSize`. Set it for untrusted data to stop decompression once the configured number of bytes is reached.

## Errors

Malformed, truncated, or oversized compressed data throws `FlateError`. Its `code` contains a `FlateErrorCode`, including `UNEXPECTED_EOF`, checksum errors, and `OUTPUT_LIMIT_EXCEEDED`.

```kotlin
try {
    KFlate.decompress(data, DecompressionGzip(maxOutputSize = 16 * 1_024 * 1_024))
} catch (error: FlateError) {
    println(error.code)
}
```

## Tests and coverage

Run unit tests and verify 100% instruction and branch coverage:

```sh
./gradlew :kflate:jvmTest :kflate:koverVerifyJvm
```

Generate the HTML coverage report:

```sh
./gradlew :kflate:koverHtmlReportJvm
```

## License

KFlate is available under the [Apache License 2.0](LICENSE).
