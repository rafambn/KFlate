
package com.rafambn.kflate

import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered

object KFlate {

    private const val STREAM_CHUNK_SIZE = 65536
    private const val STREAM_HISTORY_SIZE = 32768

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

    suspend fun compress(type: CompressionType, source: RawSource, sink: RawSink) {
        val bufferedSource = source.buffered()
        val bufferedSink = sink.buffered()

        when (type) {
            is RAW -> compressStreamRaw(type, bufferedSource, bufferedSink)
            is GZIP -> compressStreamGzip(type, bufferedSource, bufferedSink)
            is ZLIB -> compressStreamZlib(type, bufferedSource, bufferedSink)
        }

        bufferedSink.flush()
    }

    suspend fun decompress(type: DecompressionType, source: RawSource, sink: RawSink) {
        val bufferedSource = source.buffered()
        val bufferedSink = sink.buffered()

        when (type) {
            is Raw -> decompressStreamRaw(type, bufferedSource, bufferedSink)
            is Gzip -> decompressStreamGzip(type, bufferedSource, bufferedSink)
            is Zlib -> decompressStreamZlib(type, bufferedSource, bufferedSink)
        }

        bufferedSink.flush()
    }

    private fun compressRaw(data: ByteArray, type: RAW): ByteArray {
        return deflateWithOptions(data, type, 0, 0)
    }

    private fun decompressRaw(data: ByteArray, type: Raw): ByteArray {
        return inflate(data, InflateState(validationMode = 2), null, type.dictionary)
    }

    private fun compressGzip(data: ByteArray, type: GZIP): ByteArray {
        val crc = Crc32Checksum()
        val dataLength = data.size
        crc.update(data)
        val deflatedData = deflateWithOptions(data, type, getGzipHeaderSize(type), 8)
        val deflatedDataLength = deflatedData.size
        writeGzipHeader(deflatedData, type)
        writeBytes(deflatedData, deflatedDataLength - 8, crc.getChecksum().toLong())
        writeBytes(deflatedData, deflatedDataLength - 4, dataLength.toLong())
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

    private fun compressZlib(data: ByteArray, type: ZLIB): ByteArray {
        val adler = Adler32Checksum()
        adler.update(data)
        val deflatedData = deflateWithOptions(data, type, if (type.dictionary != null) 6 else 2, 4)

        writeZlibHeader(deflatedData, type)
        writeBytesBE(deflatedData, deflatedData.size - 4, adler.getChecksum())
        return deflatedData
    }

    private fun decompressZlib(data: ByteArray, type: Zlib): ByteArray {
        if (data.size < 6) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        val start = writeZlibStart(data, type.dictionary != null, type.dictionary)

        val storedAdler32 = readFourBytesBE(data, data.size - 4)

        val inputData = data.copyOfRange(start, data.size - 4)
        val decompressedData = inflate(
            inputData,
            InflateState(validationMode = 2),
            null,
            type.dictionary
        )

        val computedAdler32 = Adler32Checksum().apply {
            update(decompressedData)
        }.getChecksum()

        if (computedAdler32 != storedAdler32) {
            createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
        }

        return decompressedData
    }

    private fun compressStreamRaw(type: RAW, source: Source, sink: Sink) {
        deflateStream(type, source, sink, null)
    }

    private fun compressStreamGzip(type: GZIP, source: Source, sink: Sink) {
        val header = ByteArray(getGzipHeaderSize(type))
        writeGzipHeader(header, type)
        sink.write(header)

        val crc = Crc32Checksum()
        var size = 0L

        deflateStream(type, source, sink) { chunk ->
            crc.update(chunk)
            size += chunk.size.toLong()
        }

        val trailer = ByteArray(8)
        writeBytes(trailer, 0, crc.getChecksum().toLong())
        writeBytes(trailer, 4, size and 0xFFFFFFFFL)
        sink.write(trailer)
    }

    private fun compressStreamZlib(type: ZLIB, source: Source, sink: Sink) {
        val headerSize = if (type.dictionary != null) 6 else 2
        val header = ByteArray(headerSize)
        writeZlibHeader(header, type)
        sink.write(header)

        val adler = Adler32Checksum()
        deflateStream(type, source, sink) { chunk ->
            adler.update(chunk)
        }

        val trailer = ByteArray(4)
        writeBytesBE(trailer, 0, adler.getChecksum())
        sink.write(trailer)
    }

    private fun deflateStream(
        type: CompressionType,
        source: Source,
        sink: Sink,
        onInput: ((ByteArray) -> Unit)?
    ) {
        val dictionary = when (type) {
            is RAW -> type.dictionary
            is GZIP -> type.dictionary
            is ZLIB -> type.dictionary
        }

        val state = DeflateState(isLastChunk = false)
        var inputBuffer = dictionary ?: ByteArray(0)
        if (dictionary != null) {
            state.waitIndex = dictionary.size
        }

        val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
        while (true) {
            val read = source.readAtMostTo(readBuffer)
            if (read == -1) {
                break
            }
            if (read == 0) {
                continue
            }
            val chunk = readBuffer.copyOfRange(0, read)
            onInput?.invoke(chunk)
            inputBuffer = appendBytes(inputBuffer, chunk, chunk.size)
            state.isLastChunk = false
            val compressed = deflateWithOptions(inputBuffer, type, 0, 0, state)
            if (compressed.isNotEmpty()) {
                sink.write(compressed)
            }
            inputBuffer = trimDeflateInput(inputBuffer, state)
        }

        state.isLastChunk = true
        val finalOutput = deflateWithOptions(inputBuffer, type, 0, 0, state)
        if (finalOutput.isNotEmpty()) {
            sink.write(finalOutput)
        }
    }

    private fun decompressStreamRaw(type: Raw, source: Source, sink: Sink) {
        val state = InflateState(validationMode = 0)
        var history = type.dictionary ?: ByteArray(0)
        val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
        var inputBuffer = ByteArray(0)
        var sourceExhausted = false
        var sawInput = false

        while (true) {
            if (!sourceExhausted) {
                val read = source.readAtMostTo(readBuffer)
                if (read == -1) {
                    sourceExhausted = true
                } else if (read > 0) {
                    sawInput = true
                    inputBuffer = appendBytes(inputBuffer, readBuffer, read)
                }
            }

            if (inputBuffer.isEmpty()) {
                if (sourceExhausted) {
                    if (!sawInput) {
                        return
                    }
                    createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
                continue
            }

            state.outputOffset = 0
            val output = inflateStreamChunk(inputBuffer, state, history, sourceExhausted) ?: continue
            if (output.isNotEmpty()) {
                sink.write(output)
                history = updateHistory(history, output)
            }

            if (state.isFinalBlock && state.literalMap == null) {
                break
            }

            val consumedBytes = state.inputBitPosition / 8
            val bitRemainder = state.inputBitPosition % 8
            if (consumedBytes > 0) {
                inputBuffer = inputBuffer.copyOfRange(consumedBytes, inputBuffer.size)
                state.inputBitPosition = bitRemainder
            } else if (sourceExhausted) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }
        }
    }

    private fun decompressStreamZlib(type: Zlib, source: Source, sink: Sink) {
        val state = InflateState(validationMode = 0)
        var history = type.dictionary ?: ByteArray(0)
        val adler = Adler32Checksum()
        val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
        var inputBuffer = ByteArray(0)
        var sourceExhausted = false
        var headerParsed = false
        var awaitingTrailer = false

        while (true) {
            if (!sourceExhausted) {
                val read = source.readAtMostTo(readBuffer)
                if (read == -1) {
                    sourceExhausted = true
                } else if (read > 0) {
                    inputBuffer = appendBytes(inputBuffer, readBuffer, read)
                }
            }

            if (!headerParsed) {
                if (inputBuffer.isEmpty()) {
                    if (sourceExhausted) {
                        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                    }
                    continue
                }
                try {
                    val headerSize = writeZlibStart(inputBuffer, type.dictionary != null, type.dictionary)
                    inputBuffer = inputBuffer.copyOfRange(headerSize, inputBuffer.size)
                    headerParsed = true
                } catch (error: com.rafambn.kflate.error.FlateError) {
                    if (error.code == FlateErrorCode.UNEXPECTED_EOF && !sourceExhausted) {
                        continue
                    }
                    throw error
                }
            }

            if (!awaitingTrailer) {
                if (inputBuffer.isEmpty()) {
                    if (sourceExhausted) {
                        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                    }
                    continue
                }

                state.outputOffset = 0
                val output = inflateStreamChunk(inputBuffer, state, history, sourceExhausted) ?: continue
                if (output.isNotEmpty()) {
                    sink.write(output)
                    adler.update(output)
                    history = updateHistory(history, output)
                }

                if (state.isFinalBlock && state.literalMap == null) {
                    awaitingTrailer = true
                } else {
                    val consumedBytes = state.inputBitPosition / 8
                    val bitRemainder = state.inputBitPosition % 8
                    if (consumedBytes > 0) {
                        inputBuffer = inputBuffer.copyOfRange(consumedBytes, inputBuffer.size)
                        state.inputBitPosition = bitRemainder
                    } else if (sourceExhausted) {
                        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                    }
                }
            }

            if (awaitingTrailer) {
                val alignedBytes = (state.inputBitPosition + 7) / 8
                if (inputBuffer.size < alignedBytes + 4) {
                    if (sourceExhausted) {
                        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                    }
                    continue
                }
                val storedAdler = readFourBytesBE(inputBuffer, alignedBytes)
                if (adler.getChecksum() != storedAdler) {
                    createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
                }
                break
            }
        }
    }

    private fun decompressStreamGzip(type: Gzip, source: Source, sink: Sink) {
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
                val read = source.readAtMostTo(readBuffer)
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
                    sink.write(output)
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
    }

    private fun appendBytes(buffer: ByteArray, data: ByteArray, length: Int): ByteArray {
        if (length <= 0) return buffer
        if (buffer.isEmpty()) return data.copyOfRange(0, length)
        val result = ByteArray(buffer.size + length)
        buffer.copyInto(result, destinationOffset = 0, startIndex = 0, endIndex = buffer.size)
        data.copyInto(result, destinationOffset = buffer.size, startIndex = 0, endIndex = length)
        return result
    }

    private fun trimDeflateInput(buffer: ByteArray, state: DeflateState): ByteArray {
        val processed = state.inputOffset
        if (processed <= STREAM_HISTORY_SIZE) return buffer
        val trim = ((processed - STREAM_HISTORY_SIZE) / STREAM_HISTORY_SIZE) * STREAM_HISTORY_SIZE
        if (trim <= 0 || trim >= buffer.size) return buffer
        val result = buffer.copyOfRange(trim, buffer.size)
        state.inputOffset -= trim
        state.waitIndex = maxOf(0, state.waitIndex - trim)
        if (state.inputEndIndex != 0) {
            state.inputEndIndex = maxOf(0, state.inputEndIndex - trim)
        }
        return result
    }

    private fun updateHistory(history: ByteArray, output: ByteArray): ByteArray {
        if (output.isEmpty()) return history
        val keep = minOf(STREAM_HISTORY_SIZE, history.size + output.size)
        val result = ByteArray(keep)
        val keepFromHistory = maxOf(0, keep - output.size)
        val historyStart = maxOf(0, history.size - keepFromHistory)
        if (keepFromHistory > 0) {
            history.copyInto(result, 0, historyStart, historyStart + keepFromHistory)
        }
        val outputStart = maxOf(0, output.size - (keep - keepFromHistory))
        output.copyInto(result, keepFromHistory, outputStart, output.size)
        return result
    }

    private fun inflateStreamChunk(
        input: ByteArray,
        state: InflateState,
        history: ByteArray,
        sourceExhausted: Boolean
    ): ByteArray? {
        val snapshot = state.copy()
        return try {
            inflate(input, state, null, history)
        } catch (e: com.rafambn.kflate.error.FlateError) {
            if (e.code == FlateErrorCode.UNEXPECTED_EOF && !sourceExhausted) {
                restoreInflateState(state, snapshot)
                null
            } else {
                throw e
            }
        }
    }

    private fun restoreInflateState(target: InflateState, snapshot: InflateState) {
        target.literalMap = snapshot.literalMap
        target.distanceMap = snapshot.distanceMap
        target.literalMaxBits = snapshot.literalMaxBits
        target.distanceMaxBits = snapshot.distanceMaxBits
        target.isFinalBlock = snapshot.isFinalBlock
        target.inputBitPosition = snapshot.inputBitPosition
        target.outputOffset = snapshot.outputOffset
        target.validationMode = snapshot.validationMode
    }
}
