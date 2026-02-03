package com.rafambn.kflate.compression

import com.rafambn.kflate.GZIP
import com.rafambn.kflate.algorithm.deflateWithOptions
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.format.getGzipHeaderSize
import com.rafambn.kflate.format.writeGzipHeader
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.trimDeflateInput
import com.rafambn.kflate.streaming.STREAM_CHUNK_SIZE
import com.rafambn.kflate.util.writeBytes
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.write

internal fun compressGzip(data: ByteArray, type: GZIP): ByteArray {
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

internal fun compressStreamGzip(type: GZIP, source: RawSource, sink: RawSink) {
    val bufferedSource = source.buffered()
    val bufferedSink = sink.buffered()

    val header = ByteArray(getGzipHeaderSize(type))
    writeGzipHeader(header, type)
    bufferedSink.write(header)

    val crc = Crc32Checksum()
    var size = 0L

    deflateStream(type, bufferedSource, bufferedSink) { chunk ->
        crc.update(chunk)
        size += chunk.size.toLong()
    }

    val trailer = ByteArray(8)
    writeBytes(trailer, 0, crc.getChecksum().toLong())
    writeBytes(trailer, 4, size and 0xFFFFFFFFL)
    bufferedSink.write(trailer)

    bufferedSink.flush()
}

private fun deflateStream(
    type: GZIP,
    source: Source,
    sink: Sink,
    onInput: ((ByteArray) -> Unit)?
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
