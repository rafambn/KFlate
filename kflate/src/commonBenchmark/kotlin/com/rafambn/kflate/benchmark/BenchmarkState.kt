package com.rafambn.kflate.benchmark

import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

@State(Scope.Benchmark)
abstract class RawBenchmarkState {
    @Param("simpleText", "text", "model3D", "Rainier.bmp", "Maltese.bmp", "Sunrise.bmp", "compressed_MVT.pbf")
    var corpus: String = "simpleText"

    @Param("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    var level: Int = 6

    protected lateinit var input: ByteArray
    protected lateinit var kompressCompressed: ByteArray

    protected fun setupRawBenchmark(
        library: BenchmarkLibrary,
        compress: (ByteArray) -> ByteArray,
        decompress: (ByteArray) -> ByteArray
    ) {
        input = BenchmarkCorpus.load(corpus)
        val platformDirectory = when (benchmarkPlatformName()) {
            "JVM" -> "jvm"
            "Linux x64 Native" -> "linuxX64"
            else -> "wasmJs"
        }
        val root = BenchmarkCorpus.corpusDirectory.parent!!.parent!!.parent!!.parent!!
        val directory = Path(root, "performance", "kompress-baseline", platformDirectory, "fixtures", level.toString())
        val fixture = Path(directory, "$corpus.deflate")
        val compressed = compress(input)
        if (library == BenchmarkLibrary.Kompress) {
            if (SystemFileSystem.exists(fixture)) {
                val saved = SystemFileSystem.source(fixture).buffered().use { it.readByteArray() }
                check(saved.contentEquals(compressed)) { "Kompress fixture changed: $fixture" }
            } else {
                SystemFileSystem.createDirectories(directory)
                SystemFileSystem.sink(fixture).buffered().use { it.write(compressed) }
            }
        }
        check(SystemFileSystem.exists(fixture)) { "Missing Kompress fixture: $fixture. Run the baseline first." }
        kompressCompressed = SystemFileSystem.source(fixture).buffered().use { it.readByteArray() }
        check(decompress(kompressCompressed).contentEquals(input)) { "Invalid Kompress fixture for $corpus level $level" }
        check(decompress(compressed).contentEquals(input)) { "Compression round trip failed for $corpus level $level" }
        appendBenchmarkMetadata(
            """{"platform":"${benchmarkPlatformName()}","library":"${library.reportName}","corpus":"$corpus","level":$level,"originalSizeBytes":${input.size},"compressedSizeBytes":${compressed.size}}"""
        )
    }
}

expect fun appendBenchmarkMetadata(line: String)
expect fun benchmarkMetadataPath(): String
expect fun benchmarkPlatformName(): String
