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

val input = "hello".encodeToByteArray().toUByteArray()

val deflated = KFlate.Raw.deflate(input)
val inflated = KFlate.Raw.inflate(deflated).toByteArray()
```

### GZIP

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.options.GzipOptions

val input = "hello".encodeToByteArray().toUByteArray()

val options = GzipOptions(
    filename = "hello.txt",
    comment = "example",
    extraFields = mapOf("AB" to ubyteArrayOf(1u, 2u)),
    includeHeaderCrc = true
)

val gz = KFlate.Gzip.compress(input, options)
val roundTrip = KFlate.Gzip.decompress(gz).toByteArray()
```

### ZLIB

```kotlin
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.options.DeflateOptions

val input = "hello".encodeToByteArray().toUByteArray()

val z = KFlate.Zlib.compress(input)
val out = KFlate.Zlib.decompress(z).toByteArray()

// With a preset dictionary
val dict = "common".encodeToByteArray().toUByteArray()
val options = DeflateOptions(dictionary = dict)
val zWithDict = KFlate.Zlib.compress(input, options)
```

## Options

- `DeflateOptions`
  - `level`: 0..9 compression level
  - `mem`: optional memory tuning
  - `dictionary`: optional preset dictionary (max 32 KB)
- `GzipOptions` (extends `DeflateOptions`)
  - `filename`: optional file name
  - `comment`: optional comment
  - `extraFields`: optional map of 2-byte IDs to raw data
  - `mtime`: optional modification time
  - `includeHeaderCrc`: include FHCRC in the header

## Development

Run JVM tests:

```bash
./gradlew :kflate:jvmTest
```

Publish to Maven Local:

```bash
./gradlew :kflate:publishToMavenLocal
```

## Project Notes

- API uses `UByteArray` for binary data. Convert with `toUByteArray()` and `toByteArray()` as needed.
- This project focuses on compatibility with standard gzip/zlib tools and the DEFLATE spec.
