package com.rafambn.kflate.streaming

internal data class InflateState(
    var literalMap: ShortArray? = null,
    var distanceMap: ShortArray? = null,
    var literalMaxBits: Int = 0,
    var distanceMaxBits: Int = 0,
    var isFinalBlock: Boolean = false,
    var inputBitPosition: Int = 0,
    var outputOffset: Int = 0,
    var validationMode: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as InflateState

        if (literalMaxBits != other.literalMaxBits) return false
        if (distanceMaxBits != other.distanceMaxBits) return false
        if (isFinalBlock != other.isFinalBlock) return false
        if (inputBitPosition != other.inputBitPosition) return false
        if (outputOffset != other.outputOffset) return false
        if (validationMode != other.validationMode) return false
        if (!literalMap.contentEquals(other.literalMap)) return false
        if (!distanceMap.contentEquals(other.distanceMap)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = literalMaxBits
        result = 31 * result + distanceMaxBits
        result = 31 * result + isFinalBlock.hashCode()
        result = 31 * result + inputBitPosition
        result = 31 * result + outputOffset
        result = 31 * result + validationMode
        result = 31 * result + (literalMap?.contentHashCode() ?: 0)
        result = 31 * result + (distanceMap?.contentHashCode() ?: 0)
        return result
    }
}