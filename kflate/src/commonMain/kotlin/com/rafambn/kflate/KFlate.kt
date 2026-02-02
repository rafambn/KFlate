@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.options.DeflateOptions
import com.rafambn.kflate.options.GzipOptions
import com.rafambn.kflate.options.InflateOptions

object KFlate {

    fun compress(data: ByteArray, type: CompressionType): ByteArray {
        val ubyteData = UByteArray(data.size) { i -> data[i].toUByte() }

        val result = when (type) {
            is RAW -> compressRaw(ubyteData, type)
            is GZIP -> compressGzip(ubyteData, type)
            is ZLIB -> compressZlib(ubyteData, type)
        }

        return ByteArray(result.size) { i -> result[i].toByte() }
    }

    fun decompress(data: ByteArray, type: DecompressionType): ByteArray {
        val ubyteData = UByteArray(data.size) { i -> data[i].toUByte() }

        val result = when (type) {
            is Raw -> decompressRaw(ubyteData, type)
            is Gzip -> decompressGzip(ubyteData, type)
            is Zlib -> decompressZlib(ubyteData, type)
        }

        return ByteArray(result.size) { i -> result[i].toByte() }
    }

    /*
    suspend fun compress(type: CompressionType, writer: suspend () -> ByteArray?, reader: suspend (ByteArray) -> Unit) {
        throw NotImplementedError("Streaming compression not yet implemented")
    }

    suspend fun decompress(type: DecompressionType, writer: suspend () -> ByteArray?, reader: suspend (ByteArray) -> Unit) {
        throw NotImplementedError("Streaming decompression not yet implemented")
    }
    */

    private fun compressRaw(data: UByteArray, type: RAW): UByteArray {
        val options = DeflateOptions(
            level = type.level,
            bufferSize = type.bufferSize,
            dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } }
        )
        return deflateWithOptions(data, options, 0, 0)
    }

    private fun decompressRaw(data: UByteArray, type: Raw): UByteArray {
        val options = InflateOptions(
            dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } }
        )
        return inflate(data, InflateState(lastCheck = 2), null, options.dictionary)
    }

    private fun compressGzip(data: UByteArray, type: GZIP): UByteArray {
        val options = GzipOptions(
            level = type.level,
            bufferSize = type.bufferSize,
            dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } },
            filename = type.filename,
            mtime = type.mtime,
            comment = type.comment,
            extraFields = type.extraFields?.mapValues { (_, v) ->
                UByteArray(v.size) { i -> v[i].toUByte() }
            },
            includeHeaderCrc = type.includeHeaderCrc
        )

        val crc = Crc32Checksum()
        val dataLength = data.size
        crc.update(data.asByteArray())
        val deflatedData = deflateWithOptions(data, options, getGzipHeaderSize(options), 8)
        val deflatedDataLength = deflatedData.size
        writeGzipHeader(deflatedData, options)
        writeBytes(deflatedData, deflatedDataLength - 8, crc.getChecksum().toLong())
        writeBytes(deflatedData, deflatedDataLength - 4, dataLength.toLong())
        return deflatedData
    }

    private fun decompressGzip(data: UByteArray, type: Gzip): UByteArray {
        if (data.size < 20) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        val decompressedChunks = mutableListOf<UByteArray>()
        var currentPosition = 0

        while (currentPosition < data.size) {
            // Check if enough bytes for header
            if (currentPosition + 10 > data.size) {
                createFlateError(FlateErrorCode.TRAILING_GARBAGE)
            }

            // Validate gzip magic bytes
            if (data[currentPosition].toInt() != 31 ||
                data[currentPosition + 1].toInt() != 139 ||
                data[currentPosition + 2].toInt() != 8) {
                createFlateError(FlateErrorCode.TRAILING_GARBAGE)
            }

            // Process member
            val dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } }
            val result = processSingleGzipMember(data, currentPosition, dictionary)
            decompressedChunks.add(result.decompressed)
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
        val result = UByteArray(totalSize)
        var offset = 0
        for (chunk in decompressedChunks) {
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }

        return result
    }

    private fun compressZlib(data: UByteArray, type: ZLIB): UByteArray {
        val options = DeflateOptions(
            level = type.level,
            bufferSize = type.bufferSize,
            dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } }
        )

        val adler = Adler32Checksum()
        adler.update(data.asByteArray())
        val deflatedData = deflateWithOptions(data, options, if (type.dictionary != null) 6 else 2, 4)

        val gzipOptions = GzipOptions(
            level = type.level,
            bufferSize = options.bufferSize,
            dictionary = options.dictionary
        )
        writeZlibHeader(deflatedData.asByteArray(), options)
        writeBytesBE(deflatedData, deflatedData.size - 4, adler.getChecksum())
        return deflatedData
    }

    private fun decompressZlib(data: UByteArray, type: Zlib): UByteArray {
        if (data.size < 6) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        val dictionary = type.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } }
        val start = writeZlibStart(data.asByteArray(), dictionary != null, dictionary?.asByteArray())

        val storedAdler32 = readFourBytesBE(data, data.size - 4)

        val inputData = data.copyOfRange(start, data.size - 4)
        val decompressedData = inflate(
            inputData,
            InflateState(lastCheck = 2),
            null,
            dictionary
        )

        val computedAdler32 = Adler32Checksum().apply {
            update(decompressedData.asByteArray())
        }.getChecksum()

        if (computedAdler32 != storedAdler32) {
            createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
        }

        return decompressedData
    }
}
