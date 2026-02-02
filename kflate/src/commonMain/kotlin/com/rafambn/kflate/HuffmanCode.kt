
package com.rafambn.kflate

internal data class HuffmanTable(
    val baseLengths: ShortArray,
    val reverseLookup: IntArray
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

internal fun generateHuffmanTable(extraBits: ByteArray, startValue: Int): HuffmanTable {
    val baseLengths = ShortArray(31)
    var currentStart = startValue
    for (i in 0 until 31) {
        val extraBit = if (i == 0) 0 else extraBits[i - 1].toInt() and 0xFF
        currentStart += 1 shl extraBit
        baseLengths[i] = currentStart.toShort()
    }

    val reverseLookup = IntArray((baseLengths[30].toInt() and 0xFFFF))
    for (i in 1 until 30) {
        for (j in (baseLengths[i].toInt() and 0xFFFF) until (baseLengths[i + 1].toInt() and 0xFFFF)) {
            reverseLookup[j] = ((j - (baseLengths[i].toInt() and 0xFFFF)) shl 5) or i
        }
    }
    return HuffmanTable(baseLengths, reverseLookup)
}

internal fun createHuffmanTree(codeLengths: ByteArray, maxBits: Int, isReversed: Boolean): ShortArray {
    val codeLengthSize = codeLengths.size
    val lengths = IntArray(maxBits)

    for (i in 0 until codeLengthSize) {
        val len = codeLengths[i].toInt() and 0xFF
        if (len != 0) {
            lengths[len - 1]++
        }
    }

    val minCodes = IntArray(maxBits)
    for (i in 1 until maxBits) {
        minCodes[i] = (minCodes[i - 1] + lengths[i - 1]) shl 1
    }

    val codes: ShortArray
    if (isReversed) {
        codes = ShortArray(1 shl maxBits)
        val reverseBits = 15 - maxBits

        for (i in 0 until codeLengthSize) {
            val codeLength = codeLengths[i].toInt() and 0xFF
            if (codeLength != 0) {
                val symbolAndBits = (i shl 4) or codeLength
                val remainingBits = maxBits - codeLength

                val startValue = minCodes[codeLength - 1]
                minCodes[codeLength - 1]++
                var value = startValue shl remainingBits

                val endValue = value or ((1 shl remainingBits) - 1)
                while (value <= endValue) {
                    codes[(REVERSE_TABLE[value].toInt() and 0xFFFF) shr reverseBits] = symbolAndBits.toShort()
                    value++
                }
            }
        }
    } else {
        codes = ShortArray(codeLengthSize)
        for (i in 0 until codeLengthSize) {
            val codeLength = codeLengths[i].toInt() and 0xFF
            if (codeLength != 0) {
                val currentCode = minCodes[codeLength - 1]
                minCodes[codeLength - 1]++
                codes[i] = ((REVERSE_TABLE[currentCode].toInt() and 0xFFFF) shr (15 - codeLength)).toShort()
            }
        }
    }
    return codes
}

/**
 * Validates Huffman code lengths form a complete, non-oversubscribed tree per RFC 1951.
 * Uses code space tracking: sum of 2^(-L_i) must equal 1 where L_i is code length.
 *
 * Per Mark Adler (zlib maintainer): RFC 1951 requires complete codes since they are
 * referred to as "Huffman codes" - by definition, a Huffman code is complete.
 * The only exception is single-symbol trees, explicitly allowed by the RFC.
 *
 * @param codeLengths Array of code lengths for each symbol (0 = unused)
 * @param maxBits Maximum code length
 * @return true if valid, false if oversubscribed or incomplete
 */
internal fun validateHuffmanCodeLengths(codeLengths: ByteArray, maxBits: Int): Boolean {
    if (maxBits <= 0) return true  // Empty tree

    // Count symbols at each code length
    val lengthCounts = IntArray(maxBits + 1)
    var totalSymbols = 0

    for (length in codeLengths) {
        val len = length.toInt() and 0xFF
        if (len > 0) {
            if (len > maxBits) return false  // Invalid code length
            lengthCounts[len]++
            totalSymbols++
        }
    }

    // Special cases per RFC 1951
    if (totalSymbols == 0) return true   // Empty tree
    if (totalSymbols == 1) return true   // Single symbol (explicit RFC exception)

    // Validate using code space tracking (units = 2^maxBits)
    var codeSpace = 1 shl maxBits

    for (bitLength in 1..maxBits) {
        val count = lengthCounts[bitLength]
        if (count > 0) {
            // Each code at this length uses 2^(maxBits - bitLength) units
            val spacePerCode = 1 shl (maxBits - bitLength)
            codeSpace -= count * spacePerCode

            if (codeSpace < 0) return false  // Oversubscribed
        }
    }

    // Valid only if all space used (complete tree)
    return codeSpace == 0
}

internal data class HuffmanNode(
    val symbol: Int,
    val frequency: Int,
    var leftChild: HuffmanNode? = null,
    var rightChild: HuffmanNode? = null
)

internal data class HuffmanTreeResult(val tree: ByteArray, val maxBits: Int) {
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

internal fun buildHuffmanTreeFromFrequencies(frequencies: IntArray, maxBits: Int): HuffmanTreeResult {
    val nodes = mutableListOf<HuffmanNode>()
    for (i in frequencies.indices) {
        if (frequencies[i] > 0) {
            nodes.add(HuffmanNode(symbol = i, frequency = frequencies[i]))
        }
    }

    val nodeCount = nodes.size
    val originalNodes = ArrayList(nodes)

    if (nodeCount == 0) {
        return HuffmanTreeResult(ByteArray(0), 0)
    }
    if (nodeCount == 1) {
        val codeLengths = ByteArray(nodes[0].symbol + 1)
        codeLengths[nodes[0].symbol] = 1
        return HuffmanTreeResult(codeLengths, 1)
    }

    val maxSymbol = originalNodes.maxOf { it.symbol }
    val codeLengths = IntArray(maxSymbol + 1)

    nodes.sortBy { it.frequency }

    val combinedNodes = ArrayList(nodes)
    combinedNodes.add(HuffmanNode(symbol = -1, frequency = 25001))

    var lowFreqIndex = 0
    var highFreqIndex = 1
    var combinedIndex = 2

    val firstNode = combinedNodes[0]
    val secondNode = combinedNodes[1]
    combinedNodes[0] = HuffmanNode(
        symbol = -1,
        frequency = firstNode.frequency + secondNode.frequency,
        leftChild = firstNode,
        rightChild = secondNode
    )

    while (highFreqIndex != nodeCount - 1) {
        val node1 =
            if (combinedNodes[lowFreqIndex].frequency < combinedNodes[combinedIndex].frequency) combinedNodes[lowFreqIndex++] else combinedNodes[combinedIndex++]
        val node2 =
            if (lowFreqIndex != highFreqIndex && combinedNodes[lowFreqIndex].frequency < combinedNodes[combinedIndex].frequency) combinedNodes[lowFreqIndex++] else combinedNodes[combinedIndex++]
        combinedNodes[highFreqIndex++] = HuffmanNode(
            symbol = -1,
            frequency = node1.frequency + node2.frequency,
            leftChild = node1,
            rightChild = node2
        )
    }

    var currentMaxBits = assignCodeLengthsAndGetMaxDepth(combinedNodes[highFreqIndex - 1], codeLengths, 0)

    if (currentMaxBits > maxBits) {
        var debt = 0
        val costShift = currentMaxBits - maxBits
        val cost = 1 shl costShift

        originalNodes.sortWith(compareByDescending<HuffmanNode> { codeLengths[it.symbol] }
            .thenBy { it.frequency })

        var i = 0
        for (nodeIndex in 0 until nodeCount) {
            val symbol = originalNodes[nodeIndex].symbol
            if (codeLengths[symbol] > maxBits) {
                debt += cost - (1 shl (currentMaxBits - codeLengths[symbol]))
                codeLengths[symbol] = maxBits
            } else {
                i = nodeIndex
                break
            }
        }

        debt = debt shr costShift

        while (debt > 0) {
            val symbol = originalNodes[i].symbol
            if (codeLengths[symbol] < maxBits) {
                debt -= 1 shl (maxBits - codeLengths[symbol] - 1)
                codeLengths[symbol]++
            } else {
                i++
            }
        }

        i = nodeCount - 1
        while (i >= 0 && debt != 0) {
            val symbol = originalNodes[i].symbol
            if (codeLengths[symbol] == maxBits) {
                codeLengths[symbol]--
                debt++
            }
            i--
        }
        currentMaxBits = maxBits
    }

    return HuffmanTreeResult(ByteArray(codeLengths.size) { codeLengths[it].toByte() }, currentMaxBits)
}

internal fun assignCodeLengthsAndGetMaxDepth(node: HuffmanNode, lengths: IntArray, depth: Int): Int {
    return if (node.symbol != -1) {
        lengths[node.symbol] = depth
        depth
    } else {
        maxOf(
            assignCodeLengthsAndGetMaxDepth(node.leftChild!!, lengths, depth + 1),
            assignCodeLengthsAndGetMaxDepth(node.rightChild!!, lengths, depth + 1)
        )
    }
}

internal fun generateLengthCodes(codeLengths: ByteArray): Pair<ShortArray, Int> {
    var maxSymbol = codeLengths.size
    while (maxSymbol > 0 && codeLengths[maxSymbol - 1].toInt() == 0) {
        maxSymbol--
    }

    val compactCodes = ShortArray(maxSymbol)
    var compactCodeIndex = 0
    var currentCode = codeLengths[0].toInt() and 0xFF
    var runLength = 1

    val writeCode = { value: Int -> compactCodes[compactCodeIndex++] = value.toShort() }

    for (i in 1..maxSymbol) {
        val nextCode = if (i < maxSymbol) codeLengths[i].toInt() and 0xFF else -1
        if (i < maxSymbol && nextCode == currentCode) {
            runLength++
        } else {
            if (currentCode == 0 && runLength > 2) {
                while (runLength > 138) {
                    writeCode(32754)
                    runLength -= 138
                }
                if (runLength > 2) {
                    writeCode(if (runLength > 10) ((runLength - 11) shl 5) or 28690 else ((runLength - 3) shl 5) or 12305)
                    runLength = 0
                }
            } else if (runLength > 3) {
                writeCode(currentCode)
                runLength--
                while (runLength > 6) {
                    writeCode(8304)
                    runLength -= 6
                }
                if (runLength > 2) {
                    writeCode(((runLength - 3) shl 5) or 8208)
                    runLength = 0
                }
            }
            while (runLength-- > 0) {
                writeCode(currentCode)
            }
            runLength = 1
            if (i < maxSymbol) {
                currentCode = nextCode
            }
        }
    }
    return Pair(compactCodes.sliceArray(0..compactCodeIndex), maxSymbol)
}

internal fun calculateCodeLength(codeFrequencies: IntArray, codeLengths: ByteArray): Int {
    var length = 0
    for (i in codeLengths.indices) {
        length += codeFrequencies[i] * (codeLengths[i].toInt() and 0xFF)
    }
    return length
}
