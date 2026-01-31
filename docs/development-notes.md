# Development Notes

## Multiplatform Considerations

- Core algorithm in `commonMain` sourceset (shared across all targets)
- No target-specific implementations needed currently
- JVM tests use standard `kotlin.test` framework
- JavaScript tests use Karma with Firefox browser runner

## Compression Tuning

- Compression level 0: No compression (storage only)
- Compression levels 1-3: Fast compression, larger output
- Compression levels 4-7: Balanced (default is 6)
- Compression levels 8-9: Slower, better compression
- Memory setting (-1 or 0-12) affects internal hash table size

## Dictionary Support

ZLIB supports optional preset dictionaries for better compression of specific data patterns:
- Specified in `DeflateOptions.dictionary` during compression
- Same dictionary must be provided during decompression via `InflateOptions`
- Limited to 32KB maximum size

## Platform-Specific Gradle Tasks

- JVM: Standard `jvmTest` and compilation tasks
- Android: Library compilation for `release` variant (publishable to Maven)
- JavaScript/WASM: Browser tests run in Firefox via Karma
- iOS: Separate compilation tasks per architecture (arm64, x64, simulator arm64)

## Publishing

```bash
# Publish to local Maven repository (for testing)
./gradlew :kflate:publishToMavenLocal

# Run a specific test class
./gradlew jvmTest --tests ClassName
```
