# KFlate

Pure Kotlin Multiplatform DEFLATE, GZIP, and ZLIB compression.

<p align="center">
  <img src="KFlate-Logo.svg" alt="KFlate-Logo" width="200" height="200">
</p>

[![Maven Central](https://img.shields.io/maven-central/v/com.rafambn/KFlate?style=flat-square)](https://central.sonatype.com/artifact/com.rafambn/KFlate)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?style=flat-square)](https://kotlinlang.org)

KFlate is a Kotlin Multiplatform port of the npm [`fflate`](https://github.com/101arrowz/fflate) library. It provides compression and decompression with configurable levels, dictionary support, and both blocking and streaming APIs.

## Check out the demo app on web: [**KFlate Demo**](https://kflate.rafambn.com)

Current version [1.0.0](https://github.com/rafambn/KFlate/releases).

### Key Features

- **Pure Kotlin Implementation**: No native dependencies, works everywhere Kotlin runs
- **Multiplatform Support**: JVM, Android, JS (Browser/Node), WASM, and all native targets (iOS, macOS, Linux, Windows)
- **Multiple Compression Formats**: Raw DEFLATE, GZIP with optional headers, and ZLIB with dictionary support
- **Flexible APIs**: Both blocking and streaming (kotlinx-io) interfaces for your use case
- **Configurable Compression**: Compression levels 0-9 with intelligent hash table sizing per level
- **Dictionary Support**: Full preset dictionary support for DEFLATE/ZLIB (max 32 KB)
- **Production Ready**: Fully tested against standard tools and libraries

### Performance

KFlate delivers performance comparable to standard implementations across all platforms:

- **Native targets**: Matches **zlib**
- **JVM**: Matches **Java standard library**
- **Web**: Matches **fflate**

For detailed benchmark results, see the [performance/](performance/) folder.

## Installation

```kotlin
dependencies {
    implementation("com.rafambn:KFlate:1.0.0")
}
```

## Usage

### Raw DEFLATE

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.RAW
import com.rafambn.kflate.Raw

val input = "hello".encodeToByteArray()

val deflated = KFlate.compress(input, RAW())
val inflated = KFlate.decompress(deflated, Raw())
```

### GZIP

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.GZIP
import com.rafambn.kflate.Gzip

val input = "hello".encodeToByteArray()

val options = GZIP(
    filename = "hello.txt",
    comment = "example",
    extraFields = mapOf("AB" to byteArrayOf(1, 2)),
    includeHeaderCrc = true
)

val gz = KFlate.compress(input, options)
val roundTrip = KFlate.decompress(gz, Gzip())
```

### ZLIB

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.ZLIB
import com.rafambn.kflate.Zlib

val input = "hello".encodeToByteArray()

val z = KFlate.compress(input, ZLIB())
val out = KFlate.decompress(z, Zlib())

// With a preset dictionary
val dict = "common".encodeToByteArray()
val options = ZLIB(dictionary = dict)
val zWithDict = KFlate.compress(input, options)
```

## Configuration Options

### Compression Options

- **`level`**: Compression level 0–9 (default: 6)
  - 0: No compression
  - 1–3: Fast compression
  - 4–6: Balanced (6 is default)
  - 7–9: Maximum compression (9 uses full 1M entry hash table)
- **`bufferSize`**: Internal hash table size (optional, auto-sized per level)
- **`dictionary`**: Preset dictionary up to 32 KB (DEFLATE/ZLIB only)

### GZIP-Specific Options

- `filename`: Original filename
- `comment`: File comment
- `extraFields`: Custom header fields
- `mtime`: Modification time
- `includeHeaderCrc`: Include CRC16 of header

### Decompression Options

- **`dictionary`**: Preset dictionary for DEFLATE/ZLIB (required if compression used one)
