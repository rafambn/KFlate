@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.options.GzipOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GzipOptionalFieldsTest {

    private fun ByteArray.toUByteArray(): UByteArray {
        return UByteArray(this.size) { this[it].toUByte() }
    }

    private fun UByteArray.toByteArray(): ByteArray {
        return ByteArray(this.size) { this[it].toByte() }
    }

    @Test
    fun testHeaderSizeCalculationNoOptionalFields() {
        val options = GzipOptions()
        assertEquals(10, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationFilenameOnly() {
        val options = GzipOptions(filename = "test.txt")
        assertEquals(19, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationCommentOnly() {
        val options = GzipOptions(comment = "note")
        assertEquals(15, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationExtraFieldsOnly() {
        val options = GzipOptions(extraFields = mapOf("AB" to ubyteArrayOf(1u, 2u, 3u)))
        assertEquals(19, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationHeaderCrcOnly() {
        val options = GzipOptions(includeHeaderCrc = true)
        assertEquals(12, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeCalculationAllOptionalFields() {
        val options = GzipOptions(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to ubyteArrayOf(1u, 2u)),
            includeHeaderCrc = true
        )
        assertEquals(37, getGzipHeaderSize(options))
    }

    @Test
    fun testHeaderSizeMatchesActualHeader() {
        val options = GzipOptions(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to ubyteArrayOf(1u, 2u)),
            includeHeaderCrc = true
        )
        val data = "test".encodeToByteArray().toUByteArray()
        val compressed = KFlate.Gzip.compress(data, options)
        val actualHeaderSize = writeGzipStart(compressed)

        assertEquals(getGzipHeaderSize(options), actualHeaderSize)
    }

    @Test
    fun testGzipOptionalFieldsRoundTripWithKFlate() {
        val options = GzipOptions(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to ubyteArrayOf(1u, 2u, 3u)),
            includeHeaderCrc = true
        )
        val data = "optional-fields".encodeToByteArray()
        val compressed = KFlate.Gzip.compress(data.toUByteArray(), options)
        val decompressed = KFlate.Gzip.decompress(compressed).toByteArray()

        assertContentEquals(data, decompressed)
    }

    @Test
    fun testGzipOptionalFieldsReadableByJavaGzip() {
        val options = GzipOptions(
            filename = "file.txt",
            comment = "comment",
            extraFields = mapOf("XX" to ubyteArrayOf(1u)),
            includeHeaderCrc = true
        )
        val data = "optional-fields".encodeToByteArray()
        val compressed = KFlate.Gzip.compress(data.toUByteArray(), options)

        val inputStream = ByteArrayInputStream(compressed.toByteArray())
        val gzipInputStream = GZIPInputStream(inputStream)
        val outputStream = ByteArrayOutputStream()

        gzipInputStream.copyTo(outputStream)

        assertContentEquals(data, outputStream.toByteArray())
    }
}
