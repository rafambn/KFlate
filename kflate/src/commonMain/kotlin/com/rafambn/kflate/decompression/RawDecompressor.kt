package com.rafambn.kflate.decompression

import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.inflateStreamChunk
import com.rafambn.kflate.streaming.updateHistory
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered

internal fun decompressRaw(data: ByteArray, type: Raw): ByteArray {
    return inflate(data, InflateState(validationMode = 2), type.dictionary, type.maxOutputSize)
}

internal fun decompressStreamRaw(type: Raw, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    val state = InflateState(validationMode = 0)
    var history = type.dictionary ?: ByteArray(0)
    val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
    var inputBuffer = ByteArray(0)
    var remainingOutputSize = type.maxOutputSize

    while (true) {
        val read = bufferedSource.readAtMostTo(readBuffer)
        val sourceExhausted = read == -1
        if (!sourceExhausted) {
            inputBuffer = appendBytes(inputBuffer, readBuffer, read)
        }

        if (inputBuffer.isEmpty()) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
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
            history = updateHistory(history, output)
            remainingOutputSize = remainingOutputSize?.minus(output.size)
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

    bufferedSink.flush()
}
