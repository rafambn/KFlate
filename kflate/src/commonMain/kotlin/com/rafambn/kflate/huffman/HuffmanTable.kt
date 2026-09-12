package com.rafambn.kflate.huffman

internal data class HuffmanTable(
    val baseLengths: ShortArray,
    val reverseLookup: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HuffmanTable

        if (!baseLengths.contentEquals(other.baseLengths)) return false
        if (!reverseLookup.contentEquals(other.reverseLookup)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = baseLengths.contentHashCode()
        result = 31 * result + reverseLookup.contentHashCode()
        return result
    }
}
