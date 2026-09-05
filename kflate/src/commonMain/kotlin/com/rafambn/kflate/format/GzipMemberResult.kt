package com.rafambn.kflate.format

internal data class GzipMemberResult(
    val decompressed: ByteArray,
    val bytesConsumed: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GzipMemberResult) return false
        if (!decompressed.contentEquals(other.decompressed)) return false
        return bytesConsumed == other.bytesConsumed
    }

    override fun hashCode(): Int {
        return 31 * decompressed.contentHashCode() + bytesConsumed
    }
}
