package com.rafambn.kflate.compression

import com.rafambn.kflate.algorithm.deflateWithOptions
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.trimDeflateInput
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered

internal fun compressRaw(data: ByteArray, type: Raw): ByteArray {
    return deflateWithOptions(data, type, 0, 0)
}

internal fun compressStreamRaw(type: Raw, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    deflateStream(type, bufferedSource, bufferedSink)

    bufferedSink.flush()
}

private fun deflateStream(
    type: Raw,
    source: Source,
    sink: Sink,
) {
    val dictionary = type.dictionary

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
        val chunk = readBuffer.copyOfRange(0, read)
        inputBuffer = appendBytes(inputBuffer, chunk, chunk.size)
        state.isLastChunk = false
        val compressed = deflateWithOptions(inputBuffer, type, 0, 0, state)
        sink.write(compressed)
        inputBuffer = trimDeflateInput(inputBuffer, state)
    }

    state.isLastChunk = true
    val finalOutput = deflateWithOptions(inputBuffer, type, 0, 0, state)
    sink.write(finalOutput)
}
