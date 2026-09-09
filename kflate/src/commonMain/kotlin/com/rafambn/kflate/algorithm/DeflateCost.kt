package com.rafambn.kflate.algorithm

import com.rafambn.kflate.huffman.FIXED_DISTANCE_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_DISTANCE_TREE
import com.rafambn.kflate.huffman.FIXED_LENGTH_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_TREE
import com.rafambn.kflate.huffman.buildHuffmanTreeFromFrequencies
import com.rafambn.kflate.huffman.calculateCodeLength
import com.rafambn.kflate.huffman.generateLengthCodes
import com.rafambn.kflate.util.countCodeLengthCodes

internal fun chooseCostAwarePath(
    data: ByteArray,
    start: Int,
    end: Int,
    matches: IntArray,
    costs: IntArray,
    choices: IntArray,
) {
    chooseCostAwarePath(
        data = data,
        start = start,
        end = end,
        matches = matches,
        costs = costs,
        choices = choices,
        literalCosts = FIXED_LITERAL_COSTS,
        lengthCosts = FIXED_LENGTH_COSTS,
        distanceCosts = FIXED_DISTANCE_COSTS,
    )

    val size = end - start
    if (size < COST_AWARE_DYNAMIC_MIN_WINDOW) return

    val initialChoices = choices.copyOf()
    val initialCosts = costs.copyOf()
    val initialLiteralFrequencies = IntArray(288)
    val initialDistanceFrequencies = IntArray(32)
    collectPathFrequencies(
        data = data,
        start = start,
        matches = matches,
        choices = initialChoices,
        size = size,
        literalFrequencies = initialLiteralFrequencies,
        distanceFrequencies = initialDistanceFrequencies,
    )

    val initialDynamicTrees = buildDynamicTrees(
        initialLiteralFrequencies,
        initialDistanceFrequencies,
    )
    val initialDynamicCosts = dynamicTokenCosts(
        initialDynamicTrees.first,
        initialDynamicTrees.second,
    )
    val initialFixedCost = calculatePathCost(
        data = data,
        start = start,
        matches = matches,
        choices = initialChoices,
        size = size,
        literalCosts = FIXED_LITERAL_COSTS,
        lengthCosts = FIXED_LENGTH_COSTS,
        distanceCosts = FIXED_DISTANCE_COSTS,
    )
    val initialDynamicCost = calculateDynamicPathCost(
        data = data,
        start = start,
        matches = matches,
        choices = initialChoices,
        size = size,
        literalCosts = initialDynamicCosts.first,
        lengthCosts = initialDynamicCosts.second,
        distanceCosts = initialDynamicCosts.third,
        literalTree = initialDynamicTrees.first,
        distanceTree = initialDynamicTrees.second,
    )

    // Skip refinement when this window estimate favors fixed coding.
    if (initialDynamicCost >= initialFixedCost) return

    chooseCostAwarePath(
        data = data,
        start = start,
        end = end,
        matches = matches,
        costs = costs,
        choices = choices,
        literalCosts = initialDynamicCosts.first,
        lengthCosts = initialDynamicCosts.second,
        distanceCosts = initialDynamicCosts.third,
    )

    val refinedChoices = choices.copyOf()
    val refinedLiteralFrequencies = IntArray(288)
    val refinedDistanceFrequencies = IntArray(32)
    collectPathFrequencies(
        data = data,
        start = start,
        matches = matches,
        choices = refinedChoices,
        size = size,
        literalFrequencies = refinedLiteralFrequencies,
        distanceFrequencies = refinedDistanceFrequencies,
    )

    val refinedDynamicTrees = buildDynamicTrees(
        refinedLiteralFrequencies,
        refinedDistanceFrequencies,
    )
    val refinedDynamicCosts = dynamicTokenCosts(
        refinedDynamicTrees.first,
        refinedDynamicTrees.second,
    )
    val refinedFixedCost = calculatePathCost(
        data = data,
        start = start,
        matches = matches,
        choices = refinedChoices,
        size = size,
        literalCosts = FIXED_LITERAL_COSTS,
        lengthCosts = FIXED_LENGTH_COSTS,
        distanceCosts = FIXED_DISTANCE_COSTS,
    )
    val refinedDynamicCost = calculateDynamicPathCost(
        data = data,
        start = start,
        matches = matches,
        choices = refinedChoices,
        size = size,
        literalCosts = refinedDynamicCosts.first,
        lengthCosts = refinedDynamicCosts.second,
        distanceCosts = refinedDynamicCosts.third,
        literalTree = refinedDynamicTrees.first,
        distanceTree = refinedDynamicTrees.second,
    )

    val initialEncodedCost = minOf(initialFixedCost, initialDynamicCost)
    val refinedEncodedCost = minOf(refinedFixedCost, refinedDynamicCost)
    if (refinedEncodedCost >= initialEncodedCost) {
        initialChoices.copyInto(choices)
        initialCosts.copyInto(costs)
    }
}

private fun chooseCostAwarePath(
    data: ByteArray,
    start: Int,
    end: Int,
    matches: IntArray,
    costs: IntArray,
    choices: IntArray,
    literalCosts: IntArray,
    lengthCosts: IntArray,
    distanceCosts: IntArray,
) {
    val size = end - start
    costs[size] = 0

    for (offset in size - 1 downTo 0) {
        val literal = data[start + offset].toInt() and 0xFF
        var bestCost = literalCosts[literal] + costs[offset + 1]
        var bestLength = 1
        val match = matches[offset]
        val distance = match and MATCH_DISTANCE_MASK
        val maximumLength = match ushr MATCH_DISTANCE_BITS
        val containedLength = minOf(maximumLength, size - offset)
        val searchedLength = minOf(containedLength, COST_AWARE_LENGTH_SEARCH)

        for (length in 3..searchedLength) {
            val candidateCost = matchBitCost(length, distance, lengthCosts, distanceCosts) +
                    costs[offset + length]
            if (candidateCost <= bestCost) {
                bestCost = candidateCost
                bestLength = length
            }
        }

        if (maximumLength > searchedLength) {
            val nextCost = if (offset + maximumLength <= size) costs[offset + maximumLength] else 0
            val candidateCost = matchBitCost(maximumLength, distance, lengthCosts, distanceCosts) + nextCost
            if (candidateCost <= bestCost) {
                bestCost = candidateCost
                bestLength = maximumLength
            }
        }

        costs[offset] = bestCost
        choices[offset] = bestLength
    }
}

private fun collectPathFrequencies(
    data: ByteArray,
    start: Int,
    matches: IntArray,
    choices: IntArray,
    size: Int,
    literalFrequencies: IntArray,
    distanceFrequencies: IntArray,
) {
    var offset = 0
    while (offset < size) {
        val length = choices[offset]
        if (length == 1) {
            literalFrequencies[data[start + offset].toInt() and 0xFF]++
        } else {
            val match = matches[offset]
            val distance = match and MATCH_DISTANCE_MASK
            val lengthSymbol = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
            val distanceSymbol = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
            literalFrequencies[257 + lengthSymbol]++
            distanceFrequencies[distanceSymbol]++
        }
        offset += length
    }

    literalFrequencies[256]++
    if (distanceFrequencies.all { it == 0 }) {
        distanceFrequencies[0] = 1
    }
}

private fun buildDynamicTrees(
    literalFrequencies: IntArray,
    distanceFrequencies: IntArray,
): Pair<ByteArray, ByteArray> {
    return Pair(
        buildHuffmanTreeFromFrequencies(literalFrequencies, 15).tree,
        buildHuffmanTreeFromFrequencies(distanceFrequencies, 15).tree,
    )
}

private fun dynamicTokenCosts(
    literalTree: ByteArray,
    distanceTree: ByteArray,
): Triple<IntArray, IntArray, IntArray> {
    val literalCosts = IntArray(288)
    for (symbol in literalCosts.indices) {
        val dynamicCost = literalTree.getOrNull(symbol)?.toInt()?.and(0xFF) ?: 0
        literalCosts[symbol] = if (dynamicCost > 0) dynamicCost else FIXED_LITERAL_COSTS[symbol]
    }

    val lengthCosts = IntArray(31)
    for (symbol in lengthCosts.indices) {
        val dynamicCost = literalTree.getOrNull(257 + symbol)?.toInt()?.and(0xFF) ?: 0
        lengthCosts[symbol] = if (dynamicCost > 0) {
            dynamicCost + (FIXED_LENGTH_EXTRA_BITS[symbol].toInt() and 0xFF)
        } else {
            FIXED_LENGTH_COSTS[symbol]
        }
    }

    val distanceCosts = IntArray(31)
    for (symbol in distanceCosts.indices) {
        val dynamicCost = distanceTree.getOrNull(symbol)?.toInt()?.and(0xFF) ?: 0
        distanceCosts[symbol] = if (dynamicCost > 0) {
            dynamicCost + (FIXED_DISTANCE_EXTRA_BITS[symbol].toInt() and 0xFF)
        } else {
            FIXED_DISTANCE_COSTS[symbol]
        }
    }

    return Triple(literalCosts, lengthCosts, distanceCosts)
}

private fun calculatePathCost(
    data: ByteArray,
    start: Int,
    matches: IntArray,
    choices: IntArray,
    size: Int,
    literalCosts: IntArray,
    lengthCosts: IntArray,
    distanceCosts: IntArray,
): Int {
    var cost = literalCosts[256]
    var offset = 0
    while (offset < size) {
        val length = choices[offset]
        if (length == 1) {
            cost += literalCosts[data[start + offset].toInt() and 0xFF]
        } else {
            val match = matches[offset]
            cost += matchBitCost(
                length,
                match and MATCH_DISTANCE_MASK,
                lengthCosts,
                distanceCosts,
            )
        }
        offset += length
    }
    return cost
}

private fun calculateDynamicPathCost(
    data: ByteArray,
    start: Int,
    matches: IntArray,
    choices: IntArray,
    size: Int,
    literalCosts: IntArray,
    lengthCosts: IntArray,
    distanceCosts: IntArray,
    literalTree: ByteArray,
    distanceTree: ByteArray,
): Int {
    val tokenCost = calculatePathCost(
        data = data,
        start = start,
        matches = matches,
        choices = choices,
        size = size,
        literalCosts = literalCosts,
        lengthCosts = lengthCosts,
        distanceCosts = distanceCosts,
    )
    val (literalCodeLengths, _) = generateLengthCodes(literalTree)
    val (distanceCodeLengths, _) = generateLengthCodes(distanceTree)
    val codeLengthFrequencies = IntArray(19)
    for (code in literalCodeLengths) {
        codeLengthFrequencies[code.toInt() and 31]++
    }
    for (code in distanceCodeLengths) {
        codeLengthFrequencies[code.toInt() and 31]++
    }

    val codeLengthTree = buildHuffmanTreeFromFrequencies(codeLengthFrequencies, 7).tree
    val numCodeLengthCodes = countCodeLengthCodes(codeLengthTree)
    val headerCost = 14 + 3 * numCodeLengthCodes +
            calculateCodeLength(codeLengthFrequencies, codeLengthTree) +
            2 * codeLengthFrequencies[16] +
            3 * codeLengthFrequencies[17] +
            7 * codeLengthFrequencies[18]
    return tokenCost + headerCost
}

private fun matchBitCost(
    length: Int,
    distance: Int,
    lengthCosts: IntArray,
    distanceCosts: IntArray,
): Int {
    val lengthSymbol = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
    val distanceSymbol = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
    return lengthCosts[lengthSymbol] + distanceCosts[distanceSymbol]
}

internal fun fixedLiteralBitCost(literal: Int): Int {
    return FIXED_LENGTH_TREE[literal].toInt() and 0xFF
}

internal fun fixedMatchBitCost(length: Int, distance: Int): Int {
    return matchBitCost(length, distance, FIXED_LENGTH_COSTS, FIXED_DISTANCE_COSTS)
}

// Bound memory without forcing matches to stop at a window boundary.
internal const val COST_AWARE_WINDOW_SIZE = 262_144
private const val COST_AWARE_DYNAMIC_MIN_WINDOW = 1_024
// Price every short match length plus the longest available match.
private const val COST_AWARE_LENGTH_SEARCH = 64

private val FIXED_LITERAL_COSTS = IntArray(288) {
    FIXED_LENGTH_TREE[it].toInt() and 0xFF
}

private val FIXED_LENGTH_COSTS = IntArray(31) { symbol ->
    (FIXED_LENGTH_TREE[257 + symbol].toInt() and 0xFF) +
            (FIXED_LENGTH_EXTRA_BITS[symbol].toInt() and 0xFF)
}

private val FIXED_DISTANCE_COSTS = IntArray(31) { symbol ->
    (FIXED_DISTANCE_TREE[symbol].toInt() and 0xFF) +
            (FIXED_DISTANCE_EXTRA_BITS[symbol].toInt() and 0xFF)
}
