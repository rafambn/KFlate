package com.rafambn.kflate.decompression

import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.format.writeZlibStart
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.inflateStreamChunk
import com.rafambn.kflate.streaming.updateHistory
import com.rafambn.kflate.util.readFourBytesBE
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered

internal fun decompressZlib(data: ByteArray, type: Zlib): ByteArray {
    if (data.size < 6) {
        throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
    }

    val start = writeZlibStart(data, type.dictionary != null, type.dictionary)

    val storedAdler32 = readFourBytesBE(data, data.size - 4)

    val inputData = data.copyOfRange(start, data.size - 4)
    val decompressedData = inflate(
        inputData,
        InflateState(validationMode = 2),
        type.dictionary,
        type.maxOutputSize,
    )

    val computedAdler32 = Adler32Checksum().apply {
        update(decompressedData)
    }.getChecksum()

    if (computedAdler32 != storedAdler32) {
        throw FlateError(FlateErrorCode.CHECKSUM_MISMATCH)
    }

    return decompressedData
}

internal fun decompressStreamZlib(type: Zlib, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    val state = InflateState(validationMode = 0)
    var history = type.dictionary ?: ByteArray(0)
    val adler = Adler32Checksum()
    val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
    var inputBuffer = ByteArray(0)
    var headerParsed = false
    var awaitingTrailer = false
    var remainingOutputSize = type.maxOutputSize

    while (true) {
        val read = bufferedSource.readAtMostTo(readBuffer)
        val sourceExhausted = read == -1
        if (!sourceExhausted) {
            inputBuffer = appendBytes(inputBuffer, readBuffer, read)
        }

        if (!headerParsed) {
            if (inputBuffer.isEmpty()) {
                throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
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
                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
                continue
            }

            state.outputOffset = 0
            val output = inflateStreamChunk(
                inputBuffer,
                state,
                history,
                sourceExhausted,
                remainingOutputSize,
            ) ?: continue
            if (output.isNotEmpty()) {
                bufferedSink.write(output)
                adler.update(output)
                history = updateHistory(history, output)
                remainingOutputSize = remainingOutputSize?.minus(output.size)
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
                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
            }
        }

        if (awaitingTrailer) {
            val alignedBytes = (state.inputBitPosition + 7) / 8
            if (inputBuffer.size < alignedBytes + 4) {
                if (sourceExhausted) {
                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                }
                continue
            }
            val storedAdler = readFourBytesBE(inputBuffer, alignedBytes)
            if (adler.getChecksum() != storedAdler) {
                throw FlateError(FlateErrorCode.CHECKSUM_MISMATCH)
            }
            break
        }
    }

    bufferedSink.flush()
}
