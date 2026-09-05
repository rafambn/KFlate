package com.rafambn.kflate.huffman

internal data class HuffmanTreeResult(
    val tree: ByteArray,
    val maxBits: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HuffmanTreeResult

        if (maxBits != other.maxBits) return false
        if (!tree.contentEquals(other.tree)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = maxBits
        result = 31 * result + tree.contentHashCode()
        return result
    }
}
