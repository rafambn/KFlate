package com.rafambn.kflate.performance

import com.rafambn.kflate.KFlate
import com.rafambn.kflate.RAW
import com.rafambn.kflate.Raw
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.readBytes
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.writeBytes
import kotlin.math.pow
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatDecimal(value: Double, decimals: Int = 2): String {
    val multiplier = 10.0.pow(decimals.toDouble()).toLong()
    val rounded = ((value * multiplier).toLong().toDouble() / multiplier)
    return rounded.toString()
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${formatDecimal(bytes / (1024.0 * 1024 * 1024))}GB"
        bytes >= 1024 * 1024 -> "${formatDecimal(bytes / (1024.0 * 1024))}MB"
        bytes >= 1024 -> "${formatDecimal(bytes / 1024.0)}KB"
        else -> "${bytes}B"
    }
}

data class CompressionBenchmark(
    val fileName: String,
    val originalSize: Long,
    val kflateCompressedSize: Long,
    val kompressCompressedSize: Long,
    val kflateAvgCompressionTimeMs: Double,
    val kompressAvgCompressionTimeMs: Double,
    val kflateAvgDecompressionTimeMs: Double,
    val kompressAvgDecompressionTimeMs: Double,
    val kflateCompressionRatio: Double,
    val kompressCompressionRatio: Double,
    val sizeDifference: Long,
    val sizeDifferencePercent: Double,
    val testDate: String
) {
    fun toReadable(): String = """
        Test Date: $testDate
        File: $fileName
        Original Size: ${formatBytes(originalSize)}

        KFlate:
          Compressed Size: ${formatBytes(kflateCompressedSize)} (${formatDecimal(kflateCompressionRatio)}%)
          Avg Compression Time (10 iterations): ${formatDecimal(kflateAvgCompressionTimeMs)}ms
          Avg Decompression Time (10 iterations): ${formatDecimal(kflateAvgDecompressionTimeMs)}ms

        Kompress:
          Compressed Size: ${formatBytes(kompressCompressedSize)} (${formatDecimal(kompressCompressionRatio)}%)
          Avg Compression Time (10 iterations): ${formatDecimal(kompressAvgCompressionTimeMs)}ms
          Avg Decompression Time (10 iterations): ${formatDecimal(kompressAvgDecompressionTimeMs)}ms

        Comparison:
          Size Difference: ${formatBytes(sizeDifference)} (${formatDecimal(sizeDifferencePercent)}%)
          KFlate is ${if (sizeDifference < 0) "LARGER" else "SMALLER"} than Kompress

    """.trimIndent()
}

class PerformanceComparisonTest {

    private val testFilesPath = "/mnt/Arquivos/MyProjects/KFlate/kflate/src/commonTest/resources"
    private val resultsPath = "/mnt/Arquivos/MyProjects/KFlate/performance"
    private val iterations = 10

    @Test
    fun compressionBenchmark() {
        val results = mutableListOf<CompressionBenchmark>()
        val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()

        println("\n=== KFlate vs Kompress Compression Benchmark ===")
        println("File: Sunrise.bmp")
        println("Test Date: $testDate")
        println("Iterations: $iterations\n")

        val filePath = "$testFilesPath/Sunrise.bmp"
        val data = try {
            println("teste1")
            SysTempDir
                .resolve(filePath)
                .canonicalFile2()
                .readBytes()
        } catch (e: Exception) {
            println("teste fail")
            println("Failed to load Sunrise.bmp: ${e.message}")
            return
        }

        val originalSize = data.size.toLong()
        println("Original Size: ${formatBytes(originalSize)}\n")

        // Test KFlate compression/decompression multiple times
        val kflateCompressionTimes = mutableListOf<Double>()
        val kflateDecompressionTimes = mutableListOf<Double>()
        var kflateCompressed: ByteArray? = null

        println("Testing KFlate ($iterations iterations)...")
        repeat(iterations) { iteration ->
            var compressed: ByteArray
            val compTime = measureTime {
                compressed = KFlate.compress(data, RAW())
            }.toDouble(DurationUnit.MILLISECONDS)
            kflateCompressionTimes.add(compTime)

            if (iteration == 0) {
                kflateCompressed = compressed
            }

            var decompressed: ByteArray
            val decompTime = measureTime {
                decompressed = KFlate.decompress(compressed, Raw())
            }.toDouble(DurationUnit.MILLISECONDS)
            kflateDecompressionTimes.add(decompTime)

            require(decompressed.contentEquals(data)) {
                "KFlate decompression verification failed at iteration $iteration"
            }

            println("  Iteration ${iteration + 1}: Compress=${formatDecimal(compTime)}ms, Decompress=${formatDecimal(decompTime)}ms")
        }

        // Test Kompress compression/decompression multiple times
        val kompressCompressionTimes = mutableListOf<Double>()
        val kompressDecompressionTimes = mutableListOf<Double>()
        var kompressCompressed: ByteArray? = null

        println("\nTesting Kompress ($iterations iterations)...")
        repeat(iterations) { iteration ->
            var compressed: ByteArray
            val compTime = measureTime {
                compressed = Deflater.deflate(data)
            }.toDouble(DurationUnit.MILLISECONDS)
            kompressCompressionTimes.add(compTime)

            if (iteration == 0) {
                kompressCompressed = compressed
            }

            var decompressed: ByteArray
            val decompTime = measureTime {
                decompressed = Inflater.inflate(compressed)
            }.toDouble(DurationUnit.MILLISECONDS)
            kompressDecompressionTimes.add(decompTime)

            require(decompressed.contentEquals(data)) {
                "Kompress decompression verification failed at iteration $iteration"
            }

            println("  Iteration ${iteration + 1}: Compress=${formatDecimal(compTime)}ms, Decompress=${formatDecimal(decompTime)}ms")
        }

        // Calculate averages
        val kflateAvgCompression = kflateCompressionTimes.average()
        val kflateAvgDecompression = kflateDecompressionTimes.average()
        val kompressAvgCompression = kompressCompressionTimes.average()
        val kompressAvgDecompression = kompressDecompressionTimes.average()

        val kflateSize = kflateCompressed!!.size.toLong()
        val kompressSize = kompressCompressed!!.size.toLong()

        val benchmark = CompressionBenchmark(
            fileName = "Sunrise.bmp",
            originalSize = originalSize,
            kflateCompressedSize = kflateSize,
            kompressCompressedSize = kompressSize,
            kflateAvgCompressionTimeMs = kflateAvgCompression,
            kompressAvgCompressionTimeMs = kompressAvgCompression,
            kflateAvgDecompressionTimeMs = kflateAvgDecompression,
            kompressAvgDecompressionTimeMs = kompressAvgDecompression,
            kflateCompressionRatio = (kflateSize.toDouble() / originalSize) * 100,
            kompressCompressionRatio = (kompressSize.toDouble() / originalSize) * 100,
            sizeDifference = kompressSize - kflateSize,
            sizeDifferencePercent = ((kompressSize - kflateSize).toDouble() / kompressSize) * 100,
            testDate = testDate
        )

        results.add(benchmark)

        println("\n${"=".repeat(70)}")
        println(benchmark.toReadable())
        println("${"=".repeat(70)}\n")

        writeResultsToFile(results)

        println("Benchmark complete!")
        println("   CSV results: $resultsPath/compression_results.csv")
        println("   Readable results: $resultsPath/compression_results.txt")
    }

    private fun writeResultsToFile(results: List<CompressionBenchmark>) {
        val readableContent = buildString {
            appendLine("=== KFlate vs Kompress Compression Benchmark Results ===\n")
            results.forEach { benchmark ->
                appendLine(benchmark.toReadable())
                appendLine("─".repeat(70))
            }
        }

        try {
            val txtFile = SysTempDir.resolve("$resultsPath/compression_$PLATFORM_NAME.txt").canonicalFile2()
            txtFile.writeBytes(excl = null, readableContent.encodeToByteArray())
        } catch (e: Exception) {
            println("Failed to write readable results file: ${e.message}")
        }
    }
}
