package com.rafambn.kflate.algorithm

import com.rafambn.kflate.huffman.FIXED_DISTANCE_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_DISTANCE_TREE
import com.rafambn.kflate.huffman.FIXED_LENGTH_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_TREE

internal fun chooseCostAwarePath(
    data: ByteArray,
    start: Int,
    end: Int,
    matches: IntArray,
    costs: IntArray,
    choices: IntArray,
) {
    // Fixed-tree widths provide stable prices before token frequencies can define a dynamic tree.
    val size = end - start
    costs[size] = 0

    for (offset in size - 1 downTo 0) {
        val literal = data[start + offset].toInt() and 0xFF
        var bestCost = fixedLiteralBitCost(literal) + costs[offset + 1]
        var bestLength = 1
        val match = matches[offset]
        val distance = match and MATCH_DISTANCE_MASK
        val maximumLength = match ushr MATCH_DISTANCE_BITS
        val containedLength = minOf(maximumLength, size - offset)
        val searchedLength = minOf(containedLength, COST_AWARE_LENGTH_SEARCH)

        for (length in 3..searchedLength) {
            val candidateCost = fixedMatchBitCost(length, distance) + costs[offset + length]
            if (candidateCost <= bestCost) {
                bestCost = candidateCost
                bestLength = length
            }
        }

        if (maximumLength > searchedLength) {
            val nextCost = if (offset + maximumLength <= size) costs[offset + maximumLength] else 0
            val candidateCost = fixedMatchBitCost(maximumLength, distance) + nextCost
            if (candidateCost <= bestCost) {
                bestCost = candidateCost
                bestLength = maximumLength
            }
        }

        costs[offset] = bestCost
        choices[offset] = bestLength
    }
}

internal fun fixedLiteralBitCost(literal: Int): Int {
    return FIXED_LENGTH_TREE[literal].toInt() and 0xFF
}

internal fun fixedMatchBitCost(length: Int, distance: Int): Int {
    val lengthSymbol = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
    val distanceSymbol = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
    return (FIXED_LENGTH_TREE[257 + lengthSymbol].toInt() and 0xFF) +
            (FIXED_LENGTH_EXTRA_BITS[lengthSymbol].toInt() and 0xFF) +
            (FIXED_DISTANCE_TREE[distanceSymbol].toInt() and 0xFF) +
            (FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF)
}

// Bound memory without forcing matches to stop at a window boundary.
internal const val COST_AWARE_WINDOW_SIZE = 262_144
// Price every short match length plus the longest available match.
private const val COST_AWARE_LENGTH_SEARCH = 64
