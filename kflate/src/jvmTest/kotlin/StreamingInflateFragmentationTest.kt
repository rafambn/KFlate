package com.rafambn.kflate

import com.rafambn.kflate.decompression.Raw
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import java.util.Random
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertContentEquals

class StreamingInflateFragmentationTest {
    @Test
    fun acceptsDynamicHuffmanCodesSplitAcrossSourceChunks() {
        val random = Random(0)
        val original = ByteArray(1_000 + random.nextInt(10_000)) {
            random.nextInt(8).toByte()
        }
        val compressed = deflateRaw(original)
        val output = Buffer()

        assertContentEquals(original, KFlate.decompress(compressed, Raw()))
        KFlate.decompress(Raw(), ChunkedRawSource(compressed, chunkSize = 23), output)

        assertContentEquals(original, output.readByteArray())
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(6, true)
        return try {
            val output = ByteArray(input.size * 2)
            deflater.setInput(input)
            deflater.finish()
            output.copyOf(deflater.deflate(output))
        } finally {
            deflater.end()
        }
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
            sink.write(data.copyOfRange(offset, offset + count))
            offset += count
            return count.toLong()
        }

        override fun close() = Unit
    }
}
