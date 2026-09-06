package com.rafambn.kflate.benchmark

import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
abstract class RawBenchmarkState {
    @Param(
        BenchmarkCorpora.SimpleText,
        BenchmarkCorpora.Text,
        BenchmarkCorpora.Model3D,
        BenchmarkCorpora.RainierBmp,
        BenchmarkCorpora.MalteseBmp,
        BenchmarkCorpora.SunriseBmp,
        BenchmarkCorpora.CompressedMvtPbf
    )
    var corpus: String = BenchmarkCorpora.SimpleText

    protected val input: ByteArray
        get() = rawInput

    protected val kflateCompressed: ByteArray
        get() = rawKFlateCompressed

    protected val kompressCompressed: ByteArray
        get() = rawKompressCompressed

    private lateinit var rawInput: ByteArray
    private lateinit var rawKFlateCompressed: ByteArray
    private lateinit var rawKompressCompressed: ByteArray

    protected fun setupRawBenchmark(
        library: BenchmarkLibrary,
        reportPrefix: String,
        compressWithKFlate: (ByteArray) -> ByteArray,
        compressWithKompress: (ByteArray) -> ByteArray,
        decompress: (ByteArray) -> ByteArray
    ) {
        rawInput = BenchmarkCorpus.load(corpus)
        rawKFlateCompressed = compressWithKFlate(rawInput)
        rawKompressCompressed = compressWithKompress(rawInput)
        val libraryCompressed = when (library) {
            BenchmarkLibrary.KFlate -> rawKFlateCompressed
            BenchmarkLibrary.Kompress -> rawKompressCompressed
        }

        require(decompress(rawKFlateCompressed).contentEquals(rawInput)) {
            "${library.reportName} failed to decompress KFlate RAW output for benchmark corpus '$corpus'"
        }
        require(decompress(rawKompressCompressed).contentEquals(rawInput)) {
            "${library.reportName} failed to decompress Kompress RAW output for benchmark corpus '$corpus'"
        }

        println(rawBenchmarkReportLine(reportPrefix, library.reportName, corpus, rawInput.size, libraryCompressed.size))
        appendBenchmarkMetadata(
            benchmarkMetadataJsonLine(
                platform = benchmarkPlatformName(),
                libraryName = library.reportName,
                corpusName = corpus,
                originalSize = rawInput.size,
                compressedSize = libraryCompressed.size
            )
        )
    }
}

const val BENCHMARK_COMPRESSION_LEVEL: Int = 6
const val BENCHMARK_MEMORY_LEVEL: Int = 8

expect fun appendBenchmarkMetadata(line: String)

expect fun benchmarkMetadataPath(): String

expect fun benchmarkPlatformName(): String

private fun benchmarkMetadataJsonLine(
    platform: String,
    libraryName: String,
    corpusName: String,
    originalSize: Int,
    compressedSize: Int
): String {
    return buildString {
        append('{')
        append("\"platform\":\"")
        append(platform)
        append("\",")
        append("\"library\":\"")
        append(libraryName)
        append("\",\"corpus\":\"")
        append(corpusName)
        append("\",\"originalSizeBytes\":")
        append(originalSize)
        append(",\"compressedSizeBytes\":")
        append(compressedSize)
        append('}')
    }
}

private fun rawBenchmarkReportLine(
    prefix: String,
    libraryName: String,
    corpusName: String,
    originalSize: Int,
    compressedSize: Int
): String {
    val originalMiB = originalSize / BYTES_PER_MIB
    return buildString {
        append(prefix)
        append(" library=")
        append(libraryName)
        append(" name=")
        append(corpusName)
        append(" originalBytes=")
        append(originalSize)
        append(" originalMiB=")
        append(formatDouble(originalMiB))
        append(" operationMiB=")
        append(formatDouble(originalMiB))
        append(" rawBytes=")
        append(compressedSize)
        append(" rawRatio=")
        append(formatDouble(compressedSize.toDouble() / originalSize))
    }
}

private const val BYTES_PER_MIB = 1024.0 * 1024.0

private fun formatDouble(value: Double): Double {
    return (value * 10_000).toInt() / 10_000.0
}
