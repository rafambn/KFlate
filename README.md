# KFlate

KFlate is a Kotlin Multiplatform port of the npm `fflate` library. It provides pure Kotlin DEFLATE, GZIP, and ZLIB compression and decompression with configurable options and dictionary support.

## Features

- Pure Kotlin implementation (no native dependencies)
- Raw DEFLATE compress/inflate
- GZIP and ZLIB wrappers
- Optional GZIP header fields (filename, comment, extra fields, header CRC)
- Dictionary support for DEFLATE/ZLIB
- Kotlin Multiplatform targets: JVM, Android, JS (Browser/Node), WASM, iOS

## Installation

Kotlin Multiplatform (commonMain):

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.rafambn:KFlate:0.1.0")
            }
        }
    }
}
```

JVM or Android (if you prefer platform-specific source sets):

```kotlin
dependencies {
    implementation("com.rafambn:KFlate:0.1.0")
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

## Options

- `CompressionType` (`RAW`, `GZIP`, `ZLIB`)
  - `level`: 0..9 compression level
  - `bufferSize`: optional internal buffer size (for hash table)
  - `dictionary`: optional preset dictionary (max 32 KB)
  - `GZIP` specific: `filename`, `comment`, `extraFields`, `mtime`, `includeHeaderCrc`
- `DecompressionType` (`Raw`, `Gzip`, `Zlib`)
  - All support optional `dictionary` (max 32 KB)

## Project Notes

- API uses standard `ByteArray` for binary data.
- This project focuses on compatibility with standard gzip/zlib tools and the DEFLATE spec.
