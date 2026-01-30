# Testing

## Test Types

- **`BlockingValidityTest`**: Validates compressed data against reference implementations (java.util.zip)
- **`PerformanceTest`**: Benchmarks compression speed and ratio
- **`SizeTest`**: Measures output size against reference implementations

## Test Resources

Large binary and text files in `src/jvmTest/resources/`:
- Image files: Maltese.bmp, Rainier.bmp, Sunrise.bmp (multiple MB each)
- Text: English text corpus (1.2 MB)
- Binary: 3D model data (2.5 KB)
- Small: Simple text for quick testing (101 bytes)

Tests compare KFlate output against Java's built-in zip decompression to ensure correctness across all file types.
