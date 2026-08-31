package com.rafambn.kflate

import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZlibStreamingHeaderTest {
    @Test
    fun acceptsOneByteSourceChunks() {
        val original = "one byte at a time".repeat(100).encodeToByteArray()
        val compressed = KFlate.compress(original, CompressionZlib())
        val output = Buffer()

        KFlate.decompress(DecompressionZlib(), OneByteRawSource(compressed), output)

        assertContentEquals(original, output.readByteArray())
    }

    @Test
    fun rejectsEofAfterOneHeaderByte() {
        val compressed = KFlate.compress("truncated".encodeToByteArray(), CompressionZlib())

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(DecompressionZlib(), OneByteRawSource(compressed.copyOf(1)), Buffer())
        }

        assertEquals(FlateErrorCode.UNEXPECTED_EOF, error.code)
    }

    @Test
    fun acceptsDictionaryHeaderInOneByteChunks() {
        val dictionary = "shared dictionary".encodeToByteArray()
        val original = "shared dictionary content".repeat(100).encodeToByteArray()
        val compressed = KFlate.compress(original, CompressionZlib(dictionary = dictionary))
        val output = Buffer()

        KFlate.decompress(
            DecompressionZlib(dictionary = dictionary),
            OneByteRawSource(compressed),
            output
        )

        assertContentEquals(original, output.readByteArray())
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
