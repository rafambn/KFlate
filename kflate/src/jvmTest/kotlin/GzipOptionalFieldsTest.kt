@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.options.GzipOptions
import com.rafambn.kflate.options.DeflateOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GzipOptionalFieldsTest {


    @Test
    fun testHeaderSizeCalculationNoOptionalFields() {
        val gzip = GZIP()
        val options = GzipOptions(level = gzip.level)
        assertEquals(10, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationFilenameOnly() {
        val gzip = GZIP(filename = "test.txt")
        val options = GzipOptions(level = gzip.level, filename = gzip.filename)
        assertEquals(19, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationCommentOnly() {
        val gzip = GZIP(comment = "note")
        val options = GzipOptions(level = gzip.level, comment = gzip.comment)
        assertEquals(15, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationExtraFieldsOnly() {
        val gzip = GZIP(extraFields = mapOf("AB" to byteArrayOf(1, 2, 3)))
        val options = GzipOptions(level = gzip.level, extraFields = mapOf("AB" to ubyteArrayOf(1u, 2u, 3u)))
        assertEquals(19, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationHeaderCrcOnly() {
        val gzip = GZIP(includeHeaderCrc = true)
        val options = GzipOptions(level = gzip.level, includeHeaderCrc = gzip.includeHeaderCrc)
        assertEquals(12, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationAllOptionalFields() {
        val gzip = GZIP(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to byteArrayOf(1, 2)),
            includeHeaderCrc = true
        )
        val options = GzipOptions(
            level = gzip.level,
            filename = gzip.filename,
            comment = gzip.comment,
            extraFields = mapOf("XX" to ubyteArrayOf(1u, 2u)),
            includeHeaderCrc = gzip.includeHeaderCrc
        )
        assertEquals(37, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeMatchesActualHeader() {
        val gzip = GZIP(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to byteArrayOf(1, 2)),
            includeHeaderCrc = true
        )
        val data = "test".encodeToByteArray()
        val compressed = KFlate.compress(data, gzip)

        val options = GzipOptions(
            level = gzip.level,
            filename = gzip.filename,
            comment = gzip.comment,
            extraFields = mapOf("XX" to ubyteArrayOf(1u, 2u)),
            includeHeaderCrc = gzip.includeHeaderCrc
        )
        val compressedUByteArray = UByteArray(compressed.size) { i -> compressed[i].toUByte() }
        val actualHeaderSize = writeGzipStart(compressedUByteArray)

        assertEquals(getGzipHeaderSize(options), actualHeaderSize)
    }

    @Test
    fun testGzipOptionalFieldsRoundTripWithKFlate() {
        val gzip = GZIP(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to byteArrayOf(1, 2, 3)),
            includeHeaderCrc = true
        )
        val data = "optional-fields".encodeToByteArray()
        val compressed = KFlate.compress(data, gzip)
        val decompressed = KFlate.decompress(compressed, Gzip())

        assertContentEquals(data, decompressed)
    }

    @Test
    fun testGzipOptionalFieldsReadableByJavaGzip() {
        val gzip = GZIP(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to byteArrayOf(1)),
            includeHeaderCrc = true
        )
        val data = "optional-fields".encodeToByteArray()
        val compressed = KFlate.compress(data, gzip)

        val inputStream = ByteArrayInputStream(compressed)
        val gzipInputStream = GZIPInputStream(inputStream)
        val outputStream = ByteArrayOutputStream()

        gzipInputStream.copyTo(outputStream)

        assertContentEquals(data, outputStream.toByteArray())
    }

    @Test
    fun testXlenAtMaximum65535() {
        // Create extra fields totaling exactly 65535 bytes (maximum allowed XLEN)
        // Single field with 65531 bytes of data: 4 bytes (SI1+SI2+LEN) + 65531 bytes = 65535
        val maxData = ByteArray(65531) { it.toByte() }
        val gzip = GZIP(
            extraFields = mapOf("AB" to maxData)
        )
        val data = "test".encodeToByteArray()

        // Should not throw - this is a valid maximum XLEN
        val compressed = KFlate.compress(data, gzip)
        val decompressed = KFlate.decompress(compressed, Gzip())

        assertContentEquals(data, decompressed)
    }

    @Test
    fun testXlenExceedsMaximum() {
        // Create extra fields totaling 65536 bytes (exceeds maximum XLEN of 65535)
        // Single field with 65532 bytes of data: 4 bytes (SI1+SI2+LEN) + 65532 bytes = 65536
        val exceedData = ByteArray(65532) { it.toByte() }

        // Should throw because total XLEN exceeds 65535
        assertFailsWith<IllegalArgumentException> {
            GZIP(extraFields = mapOf("AB" to exceedData))
        }
    }

    @Test
    fun testXlenMultipleFieldsAtMaximum() {
        // Create multiple extra fields totaling exactly 65535 bytes
        // 100 fields with 651 bytes each: (100 * 4) + (100 * 651) = 400 + 65100 = 65500
        // Just under limit, so add one more field with 35 bytes: 4 + 35 = 39 total for last field
        // Total: 65500 + 39 = 65539 (too much)
        // Let's be more precise: 10 fields with 6551 bytes each: (10 * 4) + (10 * 6551) = 40 + 65510 = 65550 (too much)
        // Let's try: 10 fields with 6549 bytes each: (10 * 4) + (10 * 6549) = 40 + 65490 = 65530
        // Then one more field with 5 bytes: 4 + 5 = 9, total = 65539 (too much)
        // Let's try simpler approach: 655 fields with 100 bytes each: (655 * 4) + (655 * 100) = 2620 + 65500 = 68120 (too much)

        // Actually let's do it more carefully:
        // Total must be <= 65535
        // N fields, each with D bytes of data
        // Total = N * (4 + D)
        // Let's use 655 fields with 98 bytes each: 655 * (4 + 98) = 655 * 102 = 66810 (too much)
        // Let's use 100 fields with 651 bytes each: 100 * (4 + 651) = 100 * 655 = 65500 (ok!)
        val fields = mutableMapOf<String, ByteArray>()
        for (i in 0 until 100) {
            val fieldId = String(charArrayOf('A' + (i / 26), 'A' + (i % 26)))
            fields[fieldId] = ByteArray(651) { it.toByte() }
        }
        val gzip = GZIP(extraFields = fields)
        val data = "test".encodeToByteArray()

        // Should not throw - total is 65500 bytes
        val compressed = KFlate.compress(data, gzip)
        val decompressed = KFlate.decompress(compressed, Gzip())

        assertContentEquals(data, decompressed)
    }

    @Test
    fun testXlenMultipleFieldsExceedsMaximum() {
        // Create multiple extra fields totaling more than 65535 bytes
        // 100 fields with 652 bytes each: 100 * (4 + 652) = 100 * 656 = 65600 (exceeds limit)
        val fields = mutableMapOf<String, ByteArray>()
        for (i in 0 until 100) {
            val fieldId = String(charArrayOf('A' + (i / 26), 'A' + (i % 26)))
            fields[fieldId] = ByteArray(652) { it.toByte() }
        }

        // Should throw because total XLEN exceeds 65535
        assertFailsWith<IllegalArgumentException> {
            GZIP(extraFields = fields)
        }
    }
}
