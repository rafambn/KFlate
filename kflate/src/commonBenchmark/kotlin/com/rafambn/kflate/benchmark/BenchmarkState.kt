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

    protected val compressed: ByteArray
        get() = rawCompressed

    private lateinit var rawInput: ByteArray
    private lateinit var rawCompressed: ByteArray

    protected fun setupRawBenchmark(
        libraryName: String,
        reportPrefix: String,
        compress: (ByteArray) -> ByteArray,
        decompress: (ByteArray) -> ByteArray
    ) {
        rawInput = BenchmarkCorpus.load(corpus)
        rawCompressed = compress(rawInput)

        require(decompress(rawCompressed).contentEquals(rawInput)) {
            "$libraryName RAW roundtrip failed for benchmark corpus '$corpus'"
        }

        println(rawBenchmarkReportLine(reportPrefix, libraryName, corpus, rawInput.size, rawCompressed.size))
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
