package com.rafambn.kflate.streaming

internal data class DeflateState(
    var head: ShortArray? = null,
    var prev: ShortArray? = null,
    var inputOffset: Int = 0,
    var inputEndIndex: Int = 0,
    var waitIndex: Int = 0,
    var bitBuffer: Int = 0,
    var isLastChunk: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DeflateState

        if (inputOffset != other.inputOffset) return false
        if (inputEndIndex != other.inputEndIndex) return false
        if (waitIndex != other.waitIndex) return false
        if (bitBuffer != other.bitBuffer) return false
        if (isLastChunk != other.isLastChunk) return false
        if (!head.contentEquals(other.head)) return false
        if (!prev.contentEquals(other.prev)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputOffset
        result = 31 * result + inputEndIndex
        result = 31 * result + waitIndex
        result = 31 * result + bitBuffer
        result = 31 * result + isLastChunk.hashCode()
        result = 31 * result + (head?.contentHashCode() ?: 0)
        result = 31 * result + (prev?.contentHashCode() ?: 0)
        return result
    }
}