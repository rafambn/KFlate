package com.rafambn.kflate

import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmptyRawInputTest {
    @Test
    fun rejectsEmptyCompressedInput() {
        val blockingError = assertFailsWith<FlateError> {
            KFlate.decompress(ByteArray(0), DecompressionRaw())
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, blockingError.code)

        val streamingError = assertFailsWith<FlateError> {
            KFlate.decompress(DecompressionRaw(), Buffer(), Buffer())
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, streamingError.code)
    }

    @Test
    fun compressedEmptyPayloadStillRoundTrips() {
        val compressed = KFlate.compress(ByteArray(0), CompressionRaw())

        assertContentEquals(ByteArray(0), KFlate.decompress(compressed, DecompressionRaw()))
    }
}
