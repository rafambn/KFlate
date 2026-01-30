# CLAUDE.md

KFlate is a pure Kotlin implementation of DEFLATE, GZIP, and ZLIB compression algorithms.

## Quick Commands

### Build and Test
```bash
# Build the entire project
./gradlew build

# Run JVM tests only
./gradlew jvmTest

# Run a specific test class
./gradlew jvmTest --tests BlockingValidityTest

# Publish to local Maven repository (for testing)
./gradlew :kflate:publishToMavenLocal
```

### Multiplatform Targets
The project uses Kotlin Multiplatform (KMP) with the following targets:
- **JVM**: Main development target with test resources
- **Android**: Library support for API 24+
- **JavaScript (IR)**: Browser and Node.js support via Karma/Firefox
- **WebAssembly (WASM)**: Browser and Node.js support
- **iOS**: Multiple architectures (arm64, x64, simulator arm64)

## Documentation

- **[Architecture](docs/architecture.md)**: Entry points, core algorithms, state management
- **[API Design](docs/api-design.md)**: Checksums, format utilities, options, error handling
- **[Testing](docs/testing.md)**: Test types and resources
- **[Development Notes](docs/development-notes.md)**: Multiplatform considerations, tuning, platform-specific tasks
