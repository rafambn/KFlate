# Kotlin Conventions

Follow these conventions when contributing to KFlate.

## Naming

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `InflateState`, `HuffmanTable` |
| Functions | camelCase | `createHuffmanTree()`, `readBits()` |
| Variables | camelCase | `literalMap`, `currentBitPosition` |
| Constants | UPPER_SNAKE_CASE | `FIXED_LENGTH_EXTRA_BITS`, `CRC32_TABLE` |

## Visibility

- Use `internal` for implementation details not part of the public API
- Expose public API through object singletons (`object KFlate`)
- Keep utility functions `internal`

```kotlin
// Good: internal implementation
internal fun readBits(data: UByteArray, bitPosition: Int, bitMask: Int): Int

// Good: public API via singleton
object KFlate {
    object Gzip {
        fun compress(data: UByteArray, options: GzipOptions = GzipOptions()): UByteArray
    }
}
```

## Error Handling

Use `FlateErrorCode` enum with `createFlateError()` for compression errors:

```kotlin
if (blockType == 3) {
    createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
}
```

Use `require()` for precondition validation in options/configuration:

```kotlin
init {
    require(level in 0..9) { "level must be in range 0..9, but was $level" }
}
```

## Kotlin Idioms

Prefer these patterns:

```kotlin
// apply for initialization
val table = UShortArray(256).apply {
    for (i in indices) { this[i] = compute(i) }
}

// let for optional handling
options.dictionary?.let { validateDictionary(it) }

// Elvis for defaults
val buffer = outputBuffer ?: UByteArray(defaultSize)

// when for exhaustive branching
when (blockType) {
    0 -> handleStored()
    1 -> handleFixed()
    2 -> handleDynamic()
}

// Destructuring pairs
val (tree, maxBits) = buildHuffmanTree(frequencies)
```

## Documentation

Reference RFC specs in comments for algorithm implementations:

```kotlin
// RFC 1951, section 3.2.7: code length order
internal val CODE_LENGTH_INDEX_MAP = ubyteArrayOf(16u, 17u, 18u, 0u, 8u, ...)

/**
 * Validates Huffman code lengths form a complete tree per RFC 1951.
 */
internal fun validateHuffmanCodeLengths(codeLengths: UByteArray, maxBits: Int): Boolean
```

## File Organization

- One primary class per file, filename matches class name
- Group related utilities: `Utils.kt`, `GzipUtils.kt`
- State classes can share a file: `State.kt` contains both `InflateState` and `DeflateState`
- File header order: `@file:OptIn`, package, imports

```kotlin
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.error.FlateErrorCode
import kotlin.math.floor
```
