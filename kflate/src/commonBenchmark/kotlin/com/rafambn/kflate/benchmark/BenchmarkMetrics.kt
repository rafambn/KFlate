package com.rafambn.kflate.benchmark

data class BenchmarkCorpusMetrics(
    val libraryName: String,
    val corpusName: String,
    val originalSize: Int,
    val compressedSizes: Map<BenchmarkFormat, Int>
) {
    val originalMiB: Double = originalSize / BYTES_PER_MIB

    fun toReportLine(prefix: String): String {
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

            for ((format, size) in compressedSizes) {
                append(' ')
                append(format.metricPrefix)
                append("Bytes=")
                append(size)
                append(' ')
                append(format.metricPrefix)
                append("Ratio=")
                append(formatDouble(size.toDouble() / originalSize))
            }
        }
    }

    private companion object {
        const val BYTES_PER_MIB = 1024.0 * 1024.0

        fun formatDouble(value: Double): Double {
            return (value * 10_000).toInt() / 10_000.0
        }
    }
}
