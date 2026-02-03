package com.rafambn.kflate.streaming

import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError

internal const val STREAM_CHUNK_SIZE = 65536
internal const val STREAM_HISTORY_SIZE = 32768

internal fun appendBytes(buffer: ByteArray, data: ByteArray, length: Int): ByteArray {
    if (length <= 0) return buffer
    if (buffer.isEmpty()) return data.copyOfRange(0, length)
    val result = ByteArray(buffer.size + length)
    buffer.copyInto(result, destinationOffset = 0, startIndex = 0, endIndex = buffer.size)
    data.copyInto(result, destinationOffset = buffer.size, startIndex = 0, endIndex = length)
    return result
}

internal fun trimDeflateInput(buffer: ByteArray, state: DeflateState): ByteArray {
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

internal fun updateHistory(history: ByteArray, output: ByteArray): ByteArray {
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

internal fun inflateStreamChunk(
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

internal fun restoreInflateState(target: InflateState, snapshot: InflateState) {
    target.literalMap = snapshot.literalMap
    target.distanceMap = snapshot.distanceMap
    target.literalMaxBits = snapshot.literalMaxBits
    target.distanceMaxBits = snapshot.distanceMaxBits
    target.isFinalBlock = snapshot.isFinalBlock
    target.inputBitPosition = snapshot.inputBitPosition
    target.outputOffset = snapshot.outputOffset
    target.validationMode = snapshot.validationMode
}
