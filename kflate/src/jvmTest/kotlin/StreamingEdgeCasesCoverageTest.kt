package com.rafambn.kflate

import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.DecompressionType
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
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

class StreamingEdgeCasesCoverageTest {

    @Test
    fun oneByteChunksRoundTripEveryFormat() {
        val original = "one byte source".repeat(100).encodeToByteArray()
        val formats = listOf(
            KFlate.compress(original, CompressionRaw()) to DecompressionRaw(),
            KFlate.compress(original, CompressionGzip()) to DecompressionGzip(),
            KFlate.compress(original, CompressionZlib()) to DecompressionZlib(),
        )

        for ((compressed, type) in formats) {
            val output = Buffer()
            KFlate.decompress(type, ChunkedRawSource(compressed, 1), output)
            assertContentEquals(original, output.readByteArray())
        }
    }

    @Test
    fun emptyStreamsFailForEveryFormat() {
        assertStreamError(byteArrayOf(), DecompressionRaw(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(byteArrayOf(), DecompressionGzip(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(byteArrayOf(), DecompressionZlib(), FlateErrorCode.UNEXPECTED_EOF)
    }

    @Test
    fun streamingGzipRejectsPartialSectionsAndTrailingGarbage() {
        assertStreamError(ByteArray(10), DecompressionGzip(), FlateErrorCode.INVALID_HEADER)
        val header = ByteArray(10).also {
            it[0] = 31
            it[1] = 139.toByte()
            it[2] = 8
        }
        assertStreamError(header, DecompressionGzip(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(header + byteArrayOf(2), DecompressionGzip(), FlateErrorCode.UNEXPECTED_EOF)

        val compressed = KFlate.compress("gzip stream".encodeToByteArray(), CompressionGzip())
        assertStreamError(compressed.copyOf(compressed.size - 1), DecompressionGzip(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(
            compressed.copyOf().also { it[it.size - 8] = (it[it.size - 8] + 1).toByte() },
            DecompressionGzip(),
            FlateErrorCode.CRC_MISMATCH,
        )
        assertStreamError(
            compressed.copyOf().also { it[it.size - 4] = (it[it.size - 4] + 1).toByte() },
            DecompressionGzip(),
            FlateErrorCode.ISIZE_MISMATCH,
        )
        assertStreamError(compressed + ByteArray(10), DecompressionGzip(), FlateErrorCode.TRAILING_GARBAGE)
        assertStreamError(
            compressed + byteArrayOf(31, 139.toByte(), 8, 0, 0),
            DecompressionGzip(),
            FlateErrorCode.TRAILING_GARBAGE,
            chunkSize = 1,
        )
    }

    @Test
    fun streamingZlibRejectsPartialSectionsAndBadChecksum() {
        assertStreamError(byteArrayOf(0x78), DecompressionZlib(), FlateErrorCode.UNEXPECTED_EOF, chunkSize = 1)
        assertStreamError(byteArrayOf(0x78, 0x9c.toByte()), DecompressionZlib(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(byteArrayOf(0x78, 0x9c.toByte(), 2), DecompressionZlib(), FlateErrorCode.UNEXPECTED_EOF)

        val compressed = KFlate.compress("zlib stream".encodeToByteArray(), CompressionZlib())
        assertStreamError(compressed.copyOf(compressed.size - 1), DecompressionZlib(), FlateErrorCode.UNEXPECTED_EOF)
        assertStreamError(
            compressed.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() },
            DecompressionZlib(),
            FlateErrorCode.CHECKSUM_MISMATCH,
        )
    }

    @Test
    fun streamingRawRejectsAnIncompleteDynamicBlock() {
        assertStreamError(byteArrayOf(2), DecompressionRaw(), FlateErrorCode.UNEXPECTED_EOF)
    }

    @Test
    fun blockingGzipChecksEveryMagicByte() {
        for (index in 0..2) {
            val invalid = ByteArray(20)
            invalid[0] = 31
            invalid[1] = 139.toByte()
            invalid[2] = 8
            invalid[index] = 0

            val error = assertFailsWith<FlateError> {
                KFlate.decompress(invalid, DecompressionGzip())
            }
            assertEquals(FlateErrorCode.TRAILING_GARBAGE, error.code)
        }
    }

    private fun assertStreamError(
        data: ByteArray,
        type: DecompressionType,
        expected: FlateErrorCode,
        chunkSize: Int = Int.MAX_VALUE,
    ) {
        val error = assertFailsWith<FlateError> {
            KFlate.decompress(type, ChunkedRawSource(data, chunkSize), Buffer())
        }
        assertEquals(expected, error.code)
    }

    private class ChunkedRawSource(
        private val data: ByteArray,
        private val chunkSize: Int,
    ) : RawSource {
        private var offset = 0

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            require(byteCount > 0)
            if (offset == data.size) return -1

            val count = minOf(chunkSize, byteCount.toInt(), data.size - offset)
            sink.write(data, offset, offset + count)
            offset += count
            return count.toLong()
        }

        override fun close() = Unit
    }
}
