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

    object Raw {
        fun inflate(data: UByteArray, options: InflateOptions = InflateOptions()): UByteArray =
            inflate(data, InflateState(lastCheck = 2), null, options.dictionary)

        fun deflate(data: UByteArray, options: DeflateOptions = DeflateOptions()): UByteArray =
            deflateWithOptions(data, options, 0, 0)
    }

    object Gzip {

        fun compress(data: UByteArray, options: GzipOptions = GzipOptions()): UByteArray {
            val crc = Crc32Checksum()
            val dataLength = data.size
            crc.update(data)
            val deflatedData = deflateWithOptions(data, options, getGzipHeaderSize(options), 8)
            val deflatedDataLength = deflatedData.size
            writeGzipHeader(deflatedData, options)
            writeBytes(deflatedData, deflatedDataLength - 8, crc.getChecksum().toLong())
            writeBytes(deflatedData, deflatedDataLength - 4, dataLength.toLong())
            return deflatedData
        }

        fun decompress(data: UByteArray, options: DeflateOptions = DeflateOptions()): UByteArray {
            if (data.size < 18) {
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
                val result = processSingleGzipMember(data, currentPosition, options.dictionary)
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
    }

    object Zlib {
        fun compress(data: UByteArray, options: DeflateOptions = DeflateOptions()): UByteArray {
            val adler = Adler32Checksum()
            adler.update(data)
            val deflatedData = deflateWithOptions(data, options, if (options.dictionary != null) 6 else 2, 4)
            writeZlibHeader(deflatedData, options)
            writeBytesBE(deflatedData, deflatedData.size - 4, adler.getChecksum())
            return deflatedData
        }

        fun decompress(data: UByteArray, options: InflateOptions = InflateOptions()): UByteArray {
            if (data.size < 6) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }

            val start = writeZlibStart(data, options.dictionary != null, options.dictionary)

            val storedAdler32 = readFourBytesBE(data, data.size - 4)

            val inputData = data.copyOfRange(start, data.size - 4)
            val decompressedData = inflate(
                inputData,
                InflateState(lastCheck = 2),
                null,
                options.dictionary
            )

            val computedAdler32 = Adler32Checksum().apply {
                update(decompressedData)
            }.getChecksum()

            if (computedAdler32 != storedAdler32) {
                createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
            }

            return decompressedData
        }
    }
}
