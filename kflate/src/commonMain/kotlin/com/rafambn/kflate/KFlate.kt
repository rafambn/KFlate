@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError

object KFlate {

    fun compress(data: ByteArray, type: CompressionType): ByteArray {
        return when (type) {
            is RAW -> compressRaw(data, type)
            is GZIP -> compressGzip(data, type)
            is ZLIB -> compressZlib(data, type)
        }
    }

    fun decompress(data: ByteArray, type: DecompressionType): ByteArray {
        return when (type) {
            is Raw -> decompressRaw(data, type)
            is Gzip -> decompressGzip(data, type)
            is Zlib -> decompressZlib(data, type)
        }
    }

    /*
    suspend fun compress(type: CompressionType, writer: suspend () -> ByteArray?, reader: suspend (ByteArray) -> Unit) {
        throw NotImplementedError("Streaming compression not yet implemented")
    }

    suspend fun decompress(type: DecompressionType, writer: suspend () -> ByteArray?, reader: suspend (ByteArray) -> Unit) {
        throw NotImplementedError("Streaming decompression not yet implemented")
    }
    */

    private fun compressRaw(data: ByteArray, type: RAW): ByteArray {
        return deflateWithOptions(data, type, 0, 0)
    }

    private fun decompressRaw(data: ByteArray, type: Raw): ByteArray {
        return inflate(data.asUByteArray(), InflateState(lastCheck = 2), null, type.dictionary?.asUByteArray()).asByteArray()
    }

    private fun compressGzip(data: ByteArray, type: GZIP): ByteArray {
        val crc = Crc32Checksum()
        val dataLength = data.size
        crc.update(data)
        val deflatedData = deflateWithOptions(data, type, getGzipHeaderSize(type), 8)
        val deflatedDataLength = deflatedData.size
        writeGzipHeader(deflatedData, type)
        writeBytes(deflatedData.asUByteArray(), deflatedDataLength - 8, crc.getChecksum().toLong())
        writeBytes(deflatedData.asUByteArray(), deflatedDataLength - 4, dataLength.toLong())
        return deflatedData
    }

    private fun decompressGzip(data: ByteArray, type: Gzip): ByteArray {
        if (data.size < 20) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        val decompressedChunks = mutableListOf<ByteArray>()
        var currentPosition = 0

        while (currentPosition < data.size) {
            // Check if enough bytes for header
            if (currentPosition + 10 > data.size) {
                createFlateError(FlateErrorCode.TRAILING_GARBAGE)
            }

            // Validate gzip magic bytes
            if ((data[currentPosition].toInt() and 0xFF) != 31 ||
                (data[currentPosition + 1].toInt() and 0xFF) != 139 ||
                (data[currentPosition + 2].toInt() and 0xFF) != 8) {
                createFlateError(FlateErrorCode.TRAILING_GARBAGE)
            }

            // Process member
            val result = processSingleGzipMember(data.asUByteArray(), currentPosition, type.dictionary?.asUByteArray())
            decompressedChunks.add(result.decompressed.asByteArray())
            currentPosition += result.bytesConsumed
        }

        // Validate at least one member processed
        if (decompressedChunks.isEmpty()) {
            createFlateError(FlateErrorCode.INVALID_HEADER)
        }

        // Return single member as-is
        if (decompressedChunks.size == 1) {
            return decompressedChunks[0]
        }

        // Concatenate multiple members
        val totalSize = decompressedChunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in decompressedChunks) {
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }

        return result
    }

    private fun compressZlib(data: ByteArray, type: ZLIB): ByteArray {
        val adler = Adler32Checksum()
        adler.update(data)
        val deflatedData = deflateWithOptions(data, type, if (type.dictionary != null) 6 else 2, 4)

        writeZlibHeader(deflatedData, type)
        writeBytesBE(deflatedData.asUByteArray(), deflatedData.size - 4, adler.getChecksum())
        return deflatedData
    }

    private fun decompressZlib(data: ByteArray, type: Zlib): ByteArray {
        if (data.size < 6) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        val start = writeZlibStart(data, type.dictionary != null, type.dictionary)

        val storedAdler32 = readFourBytesBE(data.asUByteArray(), data.size - 4)

        val inputData = data.copyOfRange(start, data.size - 4)
        val decompressedData = inflate(
            inputData.asUByteArray(),
            InflateState(lastCheck = 2),
            null,
            type.dictionary?.asUByteArray()
        ).asByteArray()

        val computedAdler32 = Adler32Checksum().apply {
            update(decompressedData)
        }.getChecksum()

        if (computedAdler32 != storedAdler32) {
            createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
        }

        return decompressedData
    }
}
