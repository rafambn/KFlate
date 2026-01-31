# Testing

## Writing Tests

### Naming

- Class: `[Feature]Test` (e.g., `HuffmanTreeValidationTest`)
- Method: `test[Condition][Outcome]` (e.g., `testValidateOversubscribedTree`)

### Structure

Use arrange-act-assert pattern:

```kotlin
@Test
fun testGzipRoundTrip() {
    // Arrange
    val original = readResourceFile("simpleText")

    // Act
    val compressed = KFlate.Gzip.compress(original.toUByteArray())
    val decompressed = KFlate.Gzip.decompress(compressed)

    // Assert
    assertContentEquals(original, decompressed.toByteArray(), "Round-trip failed")
}
```

### What to Test

1. **Happy path**: Normal operation with valid input
2. **Error conditions**: Use `assertFailsWith<FlateError>` for expected failures
3. **Edge cases**: Empty data, maximum sizes, boundary conditions
4. **Cross-validation**: Compare output against `java.util.zip` implementations

### Testing Invalid Data

Create malformed data manually rather than relying on corruption:

```kotlin
@Test
fun testInvalidBlockType() {
    val malformed = UByteArray(5)
    malformed[0] = 0x07u  // BFINAL=1, BTYPE=11 (reserved)

    assertFailsWith<FlateError> {
        KFlate.Raw.inflate(malformed)
    }
}
```

### Helpers

Define these in your test class:

```kotlin
private fun readResourceFile(fileName: String): ByteArray =
    javaClass.classLoader.getResourceAsStream(fileName)?.readBytes()
        ?: throw IllegalArgumentException("Resource not found: $fileName")

private fun ByteArray.toUByteArray(): UByteArray =
    UByteArray(size) { this[it].toUByte() }

private fun UByteArray.toByteArray(): ByteArray =
    ByteArray(size) { this[it].toByte() }
```

## Test Resources

Files in `src/jvmTest/resources/`:

| File | Size | Purpose |
|------|------|---------|
| simpleText | 100 B | Quick sanity checks |
| model3D | 2.5 KB | Binary data |
| text | 1.2 MB | Text corpus |
| Rainier.bmp | 6.2 MB | Large image |
| Maltese.bmp | 16 MB | Large image |
| Sunrise.bmp | 52 MB | Stress testing |

## Test Classes

- **`BlockingValidityTest`**: Round-trip validation against java.util.zip
- **`PerformanceTest`**: Benchmarks compression speed and ratio
- **`SizeTest`**: Output size comparison with reference implementations
- **`*ValidationTest`**: RFC compliance and error handling
