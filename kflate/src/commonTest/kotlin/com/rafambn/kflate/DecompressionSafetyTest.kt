package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecompressionSafetyTest {
    @Test
    fun reservedLiteralLengthCodeIsRejected() {
        val compressed = byteArrayOf(0x73, 0x1c, 0x03, 0x00)

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed, RawDecompression())
        }

        assertEquals(FlateErrorCode.INVALID_LENGTH_LITERAL, error.code)
    }

    @Test
    fun emptyRawCompressedInputIsRejected() {
        val blockingError = assertFailsWith<FlateError> {
            KFlate.decompress(ByteArray(0), RawDecompression())
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, blockingError.code)

        val streamingError = assertFailsWith<FlateError> {
            KFlate.decompress(RawDecompression(), Buffer(), Buffer())
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, streamingError.code)
    }

    @Test
    fun zlibStreamingAcceptsOneByteInputChunks() {
        val original = "one byte at a time".repeat(100).encodeToByteArray()
        val compressed = KFlate.compress(original, ZlibCompression())
        val output = Buffer()

        KFlate.decompress(ZlibDecompression(), OneByteRawSource(compressed), output)

        assertContentEquals(original, output.readByteArray())
    }

    @Test
    fun blockingOutputLimitsAreEnforcedForEveryFormat() {
        val original = ByteArray(4_096)

        assertOutputLimit(
            KFlate.compress(original, RawCompression()),
            RawDecompression(maxOutputSize = 1_024),
        )
        assertOutputLimit(
            KFlate.compress(original, GzipCompression()),
            GzipDecompression(maxOutputSize = 1_024),
        )
        assertOutputLimit(
            KFlate.compress(original, ZlibCompression()),
            ZlibDecompression(maxOutputSize = 1_024),
        )
    }

    @Test
    fun streamingOutputLimitIsEnforcedBeforeWritingPastIt() {
        val compressed = KFlate.compress(ByteArray(4_096), RawCompression())
        val output = Buffer()

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(
                RawDecompression(maxOutputSize = 1_024),
                Buffer().apply { write(compressed) },
                output,
            )
        }

        assertEquals(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED, error.code)
        assertEquals(0L, output.size)
    }

    @Test
    fun gzipRejectsNulTerminatedMetadataFromCallers() {
        assertFailsWith<IllegalArgumentException> {
            GzipCompression(filename = "file\u0000name")
        }
        assertFailsWith<IllegalArgumentException> {
            GzipCompression(comment = "comment\u0000suffix")
        }
    }

    @Test
    fun gzipRejectsNonIsoExtraFieldIds() {
        assertFailsWith<IllegalArgumentException> {
            GzipCompression(extraFields = mapOf("A€" to byteArrayOf(1)))
        }
    }

    @Test
    fun configurationCopiesMutableByteArrays() {
        val dictionary = "shared dictionary content".encodeToByteArray()
        val compression = ZlibCompression(dictionary = dictionary)
        val decompression = ZlibDecompression(dictionary = dictionary)
        dictionary.fill(0)
        val original = "shared dictionary content repeated".repeat(20).encodeToByteArray()

        val compressed = KFlate.compress(original, compression)

        assertContentEquals(original, KFlate.decompress(compressed, decompression))
    }

    @Test
    fun gzipConfigurationCopiesExtraFieldData() {
        val fieldData = byteArrayOf(1, 2, 3)
        val options = GzipCompression(extraFields = mapOf("AB" to fieldData))
        fieldData[0] = 9

        val compressed = KFlate.compress(byteArrayOf(1), options)

        assertEquals(1, compressed[16].toInt())
    }

    private fun assertOutputLimit(compressed: ByteArray, options: DecompressionOptions) {
        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed, options)
        }
        assertEquals(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED, error.code)
    }

    private class OneByteRawSource(private val data: ByteArray) : RawSource {
        private var offset = 0

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount > 0)
            if (offset == data.size) return -1
            sink.write(byteArrayOf(data[offset++]))
            return 1
        }

        override fun close() = Unit
    }
}
