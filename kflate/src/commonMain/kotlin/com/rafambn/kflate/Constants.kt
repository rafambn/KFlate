@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

/**
 * For a given length symbol, this table stores the number of extra bits to read
 * to determine the final match length.
 */
internal val FIXED_LENGTH_EXTRA_BITS = byteArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0, 0, 0, 0
)

/**
 * For a given distance symbol, this table stores the number of extra bits to read
 * to determine the final match distance.
 */
internal val FIXED_DISTANCE_EXTRA_BITS = byteArrayOf(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 0, 0
)

/**
 * Maps the order of code length codes in the stream to their actual values (0-18).
 * Per RFC 1951, section 3.2.7.
 */
internal val CODE_LENGTH_INDEX_MAP = byteArrayOf(
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
)

internal val FIXED_LENGTH_BASE = generateHuffmanTable(FIXED_LENGTH_EXTRA_BITS, 2).baseLengths.apply {
    this[28] = 258u
}
internal val FIXED_LENGTH_REVERSE_LOOKUP = generateHuffmanTable(FIXED_LENGTH_EXTRA_BITS, 2).reverseLookup.apply {
    this[258] = 28
}
internal val FIXED_DISTANCE_BASE = generateHuffmanTable(FIXED_DISTANCE_EXTRA_BITS, 0).baseLengths

internal val FIXED_DISTANCE_REVERSE_LOOKUP = generateHuffmanTable(FIXED_DISTANCE_EXTRA_BITS, 0).reverseLookup

internal val REVERSE_TABLE = UShortArray(32768).apply {
    for (value in this.indices) {
        var reversedBits = ((value and 0xAAAA) shr 1) or ((value and 0x5555) shl 1)
        reversedBits = ((reversedBits and 0xCCCC) shr 2) or ((reversedBits and 0x3333) shl 2)
        reversedBits = ((reversedBits and 0xF0F0) shr 4) or ((reversedBits and 0x0F0F) shl 4)
        this[value] = ((((reversedBits and 0xFF00) shr 8) or ((reversedBits and 0x00FF) shl 8)) shr 1).toUShort()
    }
}

internal val FIXED_LENGTH_TREE = ByteArray(288).apply {
    for (i in 0 until 144) this[i] = 8
    for (i in 144 until 256) this[i] = 9
    for (i in 256 until 280) this[i] = 7
    for (i in 280 until 288) this[i] = 8
}

internal val FIXED_DISTANCE_TREE = ByteArray(32).apply {
    for (i in 0 until 32) this[i] = 5
}

internal val FIXED_LENGTH_MAP = createHuffmanTree(FIXED_LENGTH_TREE, 9, false)

internal val FIXED_DISTANCE_MAP = createHuffmanTree(FIXED_DISTANCE_TREE, 5, false)

internal val FIXED_LENGTH_REVERSE_MAP = createHuffmanTree(FIXED_LENGTH_TREE, 9, true)

internal val FIXED_DISTANCE_REVERSE_MAP = createHuffmanTree(FIXED_DISTANCE_TREE, 5, true)

internal val DEFLATE_OPTIONS = intArrayOf(65540, 131080, 131088, 131104, 262176, 1048704, 1048832, 2114560, 2117632)
