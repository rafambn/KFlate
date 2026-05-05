package com.rafambn.kflate.benchmark

import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
abstract class CorpusBenchmarkState {
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

    private lateinit var benchmarkData: PreparedBenchmarkData

    protected val input: ByteArray
        get() = benchmarkData.input

    protected fun compressed(format: BenchmarkFormat): ByteArray {
        return benchmarkData.compressed(format)
    }

    protected fun setupBenchmarkData(
        codec: BenchmarkCodec,
        formats: List<BenchmarkFormat>,
        reportPrefix: String
    ) {
        benchmarkData = PreparedBenchmarkData.create(
            corpusName = corpus,
            codec = codec,
            formats = formats,
            reportPrefix = reportPrefix
        )
    }
}

private class PreparedBenchmarkData(
    val input: ByteArray,
    private val compressedByFormat: Map<BenchmarkFormat, ByteArray>
) {
    fun compressed(format: BenchmarkFormat): ByteArray {
        return compressedByFormat[format]
            ?: error("No ${format.displayName} compressed data was prepared for this benchmark")
    }

    companion object {
        fun create(
            corpusName: String,
            codec: BenchmarkCodec,
            formats: List<BenchmarkFormat>,
            reportPrefix: String
        ): PreparedBenchmarkData {
            val input = BenchmarkCorpus.load(corpusName)
            val compressedByFormat = formats.associateWith { format ->
                codec.compress(format, input)
            }

            for ((format, compressed) in compressedByFormat) {
                require(codec.decompress(format, compressed).contentEquals(input)) {
                    "${codec.libraryName} ${format.displayName} roundtrip failed for benchmark corpus '$corpusName'"
                }
            }

            val metrics = BenchmarkCorpusMetrics(
                libraryName = codec.libraryName,
                corpusName = corpusName,
                originalSize = input.size,
                compressedSizes = compressedByFormat.mapValues { (_, compressed) -> compressed.size }
            )
            println(metrics.toReportLine(reportPrefix))

            return PreparedBenchmarkData(
                input = input,
                compressedByFormat = compressedByFormat
            )
        }
    }
}
