@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.*
import kotlin.test.Test
import kotlin.test.assertContentEquals

class BlockingValidityTest {

    private val testFiles = listOf(
        "model3D",
        "text",
        "Rainier.bmp",
        "Maltese.bmp",
        "Sunrise.bmp",
        "simpleText",
    )

    private val expectedFileSizes = mapOf(
        "Maltese.bmp" to 16427390,
        "text" to 1232923,
        "Rainier.bmp" to 6220854,
        "Sunrise.bmp" to 52344054,
        "model3D" to 2478,
        "simpleText" to 100,
    )

    private fun readResourceFile(fileName: String): ByteArray {
        return javaClass.classLoader.getResourceAsStream(fileName)?.readBytes()
            ?: throw IllegalArgumentException("Resource file not found: $fileName")
    }


    // RESOURCE TESTS

    @Test
    fun testResourceFilesExist() {
        for (fileName in testFiles) {
            val fileData = readResourceFile(fileName)
            val expectedSize = expectedFileSizes[fileName]

            assert(fileData.isNotEmpty()) { "File $fileName could not be loaded or is empty" }
            assert(fileData.size == expectedSize) {
                "File $fileName has size ${fileData.size} but expected $expectedSize"
            }
        }
    }

    // RAW TESTS

    @Test
    fun testFlateCompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val compressedData = KFlate.compress(originalData, RAW())

            val inflater = Inflater(true)
            val inputStream = ByteArrayInputStream(compressedData)
            val inflaterStream = InflaterInputStream(inputStream, inflater)
            val outputStream = ByteArrayOutputStream()

            inflaterStream.copyTo(outputStream)
            val decompressedData = outputStream.toByteArray()

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")

            inflater.end()
        }
    }

    @Test
    fun testFlateDecompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val deflater = Deflater(6, true)
            val outputStream = ByteArrayOutputStream()
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)

            deflaterStream.write(originalData)
            deflaterStream.finish()
            deflaterStream.close()

            val compressedData = outputStream.toByteArray()

            val decompressedData = KFlate.decompress(compressedData, Raw())

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")

            deflater.end()
        }
    }

    // GZIP TESTS

    @Test
    fun testGzipCompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val compressedData = KFlate.compress(originalData, GZIP())

            val inputStream = ByteArrayInputStream(compressedData)
            val gzipInputStream = GZIPInputStream(inputStream)
            val outputStream = ByteArrayOutputStream()

            gzipInputStream.copyTo(outputStream)
            val decompressedData = outputStream.toByteArray()

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")
        }
    }

    @Test
    fun testGzipDecompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val outputStream = ByteArrayOutputStream()
            val gzipOutputStream = GZIPOutputStream(outputStream)

            gzipOutputStream.write(originalData)
            gzipOutputStream.finish()
            gzipOutputStream.close()

            val compressedData = outputStream.toByteArray()

            val decompressedData = KFlate.decompress(compressedData, Gzip())

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")
        }
    }

    @Test
    fun testGzipXflFlags() {
        // Use simpleText resource to ensure compressed data is large enough
        val testData = readResourceFile("simpleText")

        // Test level 0-1: should set XFL = 4u (max speed)
        val compressed0 = KFlate.compress(testData, GZIP(level = 0))
        assert(compressed0[8] == 4.toByte()) { "Level 0 should set XFL = 4 (max speed)" }

        val compressed1 = KFlate.compress(testData, GZIP(level = 1))
        assert(compressed1[8] == 4.toByte()) { "Level 1 should set XFL = 4 (max speed)" }

        // Test levels 2-8: should set XFL = 0u (default)
        for (level in 2..8) {
            val compressed = KFlate.compress(testData, GZIP(level = level))
            assert(compressed[8] == 0.toByte()) {
                "Level $level should set XFL = 0 (default), but got ${compressed[8]}"
            }
        }

        // Test level 9: should set XFL = 2u (max compression)
        val compressed9 = KFlate.compress(testData, GZIP(level = 9))
        assert(compressed9[8] == 2.toByte()) { "Level 9 should set XFL = 2 (max compression)" }
    }

    // ZLIB TESTS

    @Test
    fun testZlibCompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val compressedData = KFlate.compress(originalData, ZLIB())

            val inflater = Inflater()
            val inputStream = ByteArrayInputStream(compressedData)
            val inflaterStream = InflaterInputStream(inputStream, inflater)
            val outputStream = ByteArrayOutputStream()

            inflaterStream.copyTo(outputStream)
            val decompressedData = outputStream.toByteArray()

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")

            inflater.end()
        }
    }

    @Test
    fun testZlibDecompress() {
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
            val outputStream = ByteArrayOutputStream()
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)

            deflaterStream.write(originalData)
            deflaterStream.finish()
            deflaterStream.close()

            val compressedData = outputStream.toByteArray()

            val decompressedData = KFlate.decompress(compressedData, Zlib())

            assertContentEquals(originalData, decompressedData, "Failed on file: $fileName")

            deflater.end()
        }
    }

    // CHECKSUM VALIDATION TESTS

    @Test
    fun testZlibDecompressValidChecksumAccepted() {
        val originalData = readResourceFile("simpleText")

        // Compress with KFlate (includes valid ADLER32)
        val compressedData = KFlate.compress(originalData, ZLIB())

        // Should decompress successfully without throwing error
        val decompressedData = KFlate.decompress(compressedData, Zlib())

        assertContentEquals(originalData, decompressedData)
    }

    @Test
    fun testZlibDecompressCorruptedChecksumRejected() {
        val originalData = readResourceFile("simpleText")

        // Compress with KFlate
        val compressedData = KFlate.compress(originalData, ZLIB()).toMutableList()

        // Corrupt the ADLER32 checksum (last 4 bytes)
        val checksumStartIndex = compressedData.size - 4
        compressedData[checksumStartIndex] = (compressedData[checksumStartIndex].toInt() xor 0xFF).toByte()

        // Should throw error due to checksum mismatch
        try {
            KFlate.decompress(compressedData.toByteArray(), Zlib())
            assert(false) { "Expected checksum validation error but none was thrown" }
        } catch (e: Exception) {
            assert(e.message?.contains("checksum", ignoreCase = true) == true) {
                "Expected checksum error but got: ${e.message}"
            }
        }
    }

    @Test
    fun testZlibDecompressCorruptedDataRejected() {
        val originalData = readResourceFile("simpleText")

        // Compress with KFlate
        val compressedData = KFlate.compress(originalData, ZLIB()).toMutableList()

        // Corrupt the compressed data (not the checksum)
        // Skip header (2 bytes) and corrupt somewhere in the middle
        if (compressedData.size > 10) {
            compressedData[5] = (compressedData[5].toInt() xor 0xFF).toByte()
        }

        // Should throw error due to checksum mismatch
        try {
            KFlate.decompress(compressedData.toByteArray(), Zlib())
            assert(false) { "Expected checksum validation error but none was thrown" }
        } catch (e: Exception) {
            assert(e.message?.contains("checksum", ignoreCase = true) == true) {
                "Expected checksum error but got: ${e.message}"
            }
        }
    }

    @Test
    fun testZlibDecompressEmptyDataWithValidChecksum() {
        // Create a ZLIB stream with empty data
        // Empty data should have ADLER32 = 1 (initial state: a=1, b=0)
        val emptyData = byteArrayOf(
            0x78.toByte(), 0x9c.toByte(),  // ZLIB header (CMF=0x78, FLG=0x9c)
            0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte()  // Empty block + ADLER32(1)
        )

        // Should decompress successfully
        val decompressedData = KFlate.decompress(emptyData, Zlib())

        assertContentEquals(ByteArray(0), decompressedData)
    }
}
