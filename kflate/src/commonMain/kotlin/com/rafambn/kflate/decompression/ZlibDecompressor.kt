package com.rafambn.kflate.decompression

import com.rafambn.kflate.Zlib
import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.format.writeZlibStart
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.inflateStreamChunk
import com.rafambn.kflate.streaming.updateHistory
import com.rafambn.kflate.util.readFourBytesBE
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.write

internal fun decompressZlib(data: ByteArray, type: Zlib): ByteArray {
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

internal fun decompressStreamZlib(type: Zlib, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

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
                bufferedSink.write(output)
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

    bufferedSink.flush()
}
