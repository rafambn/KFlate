package com.rafambn.kflate.huffman

internal data class HuffmanTreeResult(
    val tree: ByteArray,
    val maxBits: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HuffmanTreeResult) return false
        return maxBits == other.maxBits && tree.contentEquals(other.tree)
    }

    override fun hashCode(): Int {
        return 31 * tree.contentHashCode() + maxBits
    }
}
