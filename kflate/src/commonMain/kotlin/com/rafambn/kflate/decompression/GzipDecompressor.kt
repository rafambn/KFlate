package com.rafambn.kflate.decompression

import com.rafambn.kflate.Gzip
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.format.GzipMemberResult
import com.rafambn.kflate.format.processSingleGzipMember
import com.rafambn.kflate.format.writeGzipStart
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.inflateStreamChunk
import com.rafambn.kflate.streaming.updateHistory
import com.rafambn.kflate.util.readFourBytes
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.write

internal fun decompressGzip(data: ByteArray, type: Gzip): ByteArray {
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
        val result = processSingleGzipMember(data, currentPosition, type.dictionary)
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
    val result = ByteArray(totalSize)
    var offset = 0
    for (chunk in decompressedChunks) {
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }

    return result
}

internal fun decompressStreamGzip(type: Gzip, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
    var inputBuffer = ByteArray(0)
    var sourceExhausted = false
    var headerParsed = false
    var awaitingTrailer = false
    var inflateState = InflateState(validationMode = 0)
    var history = type.dictionary ?: ByteArray(0)
    var crc = Crc32Checksum()
    var uncompressedSize = 0L
    var members = 0

    while (true) {
        if (!sourceExhausted) {
            val read = bufferedSource.readAtMostTo(readBuffer)
            if (read == -1) {
                sourceExhausted = true
            } else if (read > 0) {
                inputBuffer = appendBytes(inputBuffer, readBuffer, read)
            }
        }

        if (!headerParsed) {
            if (inputBuffer.isEmpty()) {
                if (sourceExhausted) {
                    if (members == 0) {
                        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                    }
                    break
                }
                continue
            }
            try {
                val headerSize = writeGzipStart(inputBuffer, 0)
                inputBuffer = inputBuffer.copyOfRange(headerSize, inputBuffer.size)
                headerParsed = true
                awaitingTrailer = false
                inflateState = InflateState(validationMode = 0)
                history = type.dictionary ?: ByteArray(0)
                crc = Crc32Checksum()
                uncompressedSize = 0L
                members++
            } catch (error: com.rafambn.kflate.error.FlateError) {
                if (error.code == FlateErrorCode.UNEXPECTED_EOF && !sourceExhausted) {
                    continue
                }
                if (members > 0) {
                    createFlateError(FlateErrorCode.TRAILING_GARBAGE)
                }
                throw error
            }
        }

        if (headerParsed && !awaitingTrailer) {
            if (inputBuffer.isEmpty()) {
                if (sourceExhausted) {
                    createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
                continue
            }

            inflateState.outputOffset = 0
            val output = inflateStreamChunk(inputBuffer, inflateState, history, sourceExhausted) ?: continue
            if (output.isNotEmpty()) {
                bufferedSink.write(output)
                crc.update(output)
                uncompressedSize += output.size.toLong()
                history = updateHistory(history, output)
            }

            if (inflateState.isFinalBlock && inflateState.literalMap == null) {
                awaitingTrailer = true
            } else {
                val consumedBytes = inflateState.inputBitPosition / 8
                val bitRemainder = inflateState.inputBitPosition % 8
                if (consumedBytes > 0) {
                    inputBuffer = inputBuffer.copyOfRange(consumedBytes, inputBuffer.size)
                    inflateState.inputBitPosition = bitRemainder
                } else if (sourceExhausted) {
                    createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
            }
        }

        if (awaitingTrailer) {
            val alignedBytes = (inflateState.inputBitPosition + 7) / 8
            if (inputBuffer.size < alignedBytes + 8) {
                if (sourceExhausted) {
                    createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
                continue
            }
            val storedCrc = readFourBytes(inputBuffer, alignedBytes).toInt()
            val storedISize = readFourBytes(inputBuffer, alignedBytes + 4)
            if (crc.getChecksum() != storedCrc) {
                createFlateError(FlateErrorCode.CRC_MISMATCH)
            }
            if ((uncompressedSize and 0xFFFFFFFFL) != storedISize) {
                createFlateError(FlateErrorCode.ISIZE_MISMATCH)
            }
            inputBuffer = inputBuffer.copyOfRange(alignedBytes + 8, inputBuffer.size)
            headerParsed = false
            awaitingTrailer = false

            if (sourceExhausted && inputBuffer.isEmpty()) {
                break
            }
        }
    }

    if (members == 0) {
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }

    bufferedSink.flush()
}
