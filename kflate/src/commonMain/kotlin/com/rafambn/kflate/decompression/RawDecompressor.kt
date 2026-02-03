package com.rafambn.kflate.decompression

import com.rafambn.kflate.Raw
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
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.write

internal fun decompressRaw(data: ByteArray, type: Raw): ByteArray {
    return inflate(data, InflateState(validationMode = 2), null, type.dictionary)
}

internal fun decompressStreamRaw(type: Raw, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    val state = InflateState(validationMode = 0)
    var history = type.dictionary ?: ByteArray(0)
    val readBuffer = ByteArray(STREAM_CHUNK_SIZE)
    var inputBuffer = ByteArray(0)
    var sourceExhausted = false
    var sawInput = false

    while (true) {
        if (!sourceExhausted) {
            val read = bufferedSource.readAtMostTo(readBuffer)
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
            bufferedSink.write(output)
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

    bufferedSink.flush()
}
