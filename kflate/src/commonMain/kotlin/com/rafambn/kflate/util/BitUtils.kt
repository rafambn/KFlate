package com.rafambn.kflate.util

import com.rafambn.kflate.huffman.FIXED_DISTANCE_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_DISTANCE_TREE
import com.rafambn.kflate.huffman.FIXED_LENGTH_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_LENGTH_BASE
import com.rafambn.kflate.huffman.FIXED_DISTANCE_MAP
import com.rafambn.kflate.huffman.FIXED_LENGTH_MAP
import com.rafambn.kflate.huffman.FIXED_LENGTH_TREE
import com.rafambn.kflate.huffman.CODE_LENGTH_INDEX_MAP
import com.rafambn.kflate.huffman.createHuffmanTree
import com.rafambn.kflate.huffman.buildHuffmanTreeFromFrequencies
import com.rafambn.kflate.huffman.generateLengthCodes
import com.rafambn.kflate.huffman.calculateCodeLength

internal fun findMaxValue(array: ByteArray): Int {
    if (array.isEmpty()) return 0
    var maxValue = array[0].toInt() and 0xFF
    for (index in 1 until array.size) {
        val value = array[index].toInt() and 0xFF
        if (value > maxValue) maxValue = value
    }
    return maxValue
}

internal fun String.toIsoStringBytes(): ByteArray {
    val bytes = ByteArray(length)
    for (i in indices) {
        val code = this[i].code
        require(code <= 255) { "Non-ISO-8859-1 character found: '${this[i]}' (code: $code)" }
        bytes[i] = code.toByte()
    }
    return bytes
}

internal fun readBits(data: ByteArray, bitPosition: Int, bitMask: Int): Int {
    val byteOffset = bitPosition / 8
    val size = data.size

    val b0 = if (byteOffset < size) data[byteOffset].toInt() and 0xFF else 0
    val b1 = if (byteOffset + 1 < size) data[byteOffset + 1].toInt() and 0xFF else 0

    return ((b0 or (b1 shl 8)) shr (bitPosition and 7)) and bitMask
}

internal fun readBits16(data: ByteArray, bitPosition: Int): Int {
    val byteOffset = bitPosition / 8
    val size = data.size

    val b0 = if (byteOffset < size) data[byteOffset].toInt() and 0xFF else 0
    val b1 = if (byteOffset + 1 < size) data[byteOffset + 1].toInt() and 0xFF else 0
    val b2 = if (byteOffset + 2 < size) data[byteOffset + 2].toInt() and 0xFF else 0

    return ((b0) or
            (b1 shl 8) or
            (b2 shl 16)) shr (bitPosition and 7)
}

internal fun shiftToNextByte(bitPosition: Int): Int {
    return (bitPosition + 7) / 8
}

internal fun shiftToNextByte(bitPosition: Long): Int {
    return ((bitPosition + 7) / 8).toInt()
}

internal fun writeBits(data: ByteArray, bitPosition: Long, value: Int) {
    val shiftedValue = value shl (bitPosition and 7L).toInt()
    val byteIndex = (bitPosition / 8).toInt()
    data[byteIndex] = ((data[byteIndex].toInt() and 0xFF) or shiftedValue).toByte()
    data[byteIndex + 1] = ((data[byteIndex + 1].toInt() and 0xFF) or (shiftedValue shr 8)).toByte()
}

internal fun writeBits16(data: ByteArray, bitPosition: Long, value: Int) {
    val shiftedValue = value shl (bitPosition and 7L).toInt()
    val byteIndex = (bitPosition / 8).toInt()
    data[byteIndex] = ((data[byteIndex].toInt() and 0xFF) or shiftedValue).toByte()
    data[byteIndex + 1] = ((data[byteIndex + 1].toInt() and 0xFF) or (shiftedValue shr 8)).toByte()
    data[byteIndex + 2] = ((data[byteIndex + 2].toInt() and 0xFF) or (shiftedValue shr 16)).toByte()
}

internal fun writeFixedBlock(output: ByteArray, bitPosition: Long, data: ByteArray): Long {
    val dataLength = data.size
    val bytePosition = shiftToNextByte(bitPosition + 2)
    output[bytePosition] = (dataLength and 255).toByte()
    output[bytePosition + 1] = (dataLength shr 8).toByte()
    output[bytePosition + 2] = (output[bytePosition].toInt() xor 255).toByte()
    output[bytePosition + 3] = (output[bytePosition + 1].toInt() xor 255).toByte()
    for (i in data.indices) {
        output[bytePosition + i + 4] = data[i]
    }
    return (bytePosition.toLong() + 4 + dataLength) * 8L
}

internal fun writeBlock(
    data: ByteArray,
    output: ByteArray,
    isFinal: Boolean,
    symbols: IntArray,
    literalFrequencies: IntArray,
    distanceFrequencies: IntArray,
    extraBits: Int,
    symbolCount: Int,
    blockStart: Int,
    blockLength: Int,
    bitPosition: Long
): Long {
    // Compare one midpoint split using the exact bit end position. The split
    // candidate uses only two small frequency sets and never copies output.
    val fullPlan = buildDeflateBlockPlan(
        literalFrequencies,
        distanceFrequencies,
        extraBits,
        blockStart,
        blockLength,
        bitPosition,
    )
    val fullEnd = deflateBlockEndBitPosition(fullPlan, bitPosition, blockLength)

    if (blockStart < 0 || symbolCount < 2) {
        return writeDeflateBlock(
            data,
            output,
            isFinal,
            symbols,
            symbolStart = 0,
            symbolCount = symbolCount,
            blockStart = blockStart,
            blockLength = blockLength,
            bitPosition = bitPosition,
            plan = fullPlan,
        )
    }

    val firstSymbolCount = symbolCount / 2
    var firstBlockLength = 0
    for (index in 0 until firstSymbolCount) {
        firstBlockLength += encodedSymbolByteLength(symbols[index])
    }
    if (firstBlockLength <= 0 || firstBlockLength >= blockLength) {
        return writeDeflateBlock(
            data,
            output,
            isFinal,
            symbols,
            symbolStart = 0,
            symbolCount = symbolCount,
            blockStart = blockStart,
            blockLength = blockLength,
            bitPosition = bitPosition,
            plan = fullPlan,
        )
    }

    val secondBlockLength = blockLength - firstBlockLength
    val firstLiteralFrequencies = IntArray(288)
    val firstDistanceFrequencies = IntArray(32)
    val firstExtraBits = populateBlockFrequencies(
        symbols,
        symbolStart = 0,
        symbolCount = firstSymbolCount,
        literalFrequencies = firstLiteralFrequencies,
        distanceFrequencies = firstDistanceFrequencies,
    )
    val secondLiteralFrequencies = IntArray(288)
    val secondDistanceFrequencies = IntArray(32)
    val secondExtraBits = populateBlockFrequencies(
        symbols,
        symbolStart = firstSymbolCount,
        symbolCount = symbolCount - firstSymbolCount,
        literalFrequencies = secondLiteralFrequencies,
        distanceFrequencies = secondDistanceFrequencies,
    )
    val firstPlan = buildDeflateBlockPlan(
        firstLiteralFrequencies,
        firstDistanceFrequencies,
        firstExtraBits,
        blockStart,
        firstBlockLength,
        bitPosition,
    )
    val firstEnd = deflateBlockEndBitPosition(firstPlan, bitPosition, firstBlockLength)
    val secondPlan = buildDeflateBlockPlan(
        secondLiteralFrequencies,
        secondDistanceFrequencies,
        secondExtraBits,
        blockStart + firstBlockLength,
        secondBlockLength,
        firstEnd,
    )
    val splitEnd = deflateBlockEndBitPosition(secondPlan, firstEnd, secondBlockLength)

    if (splitEnd < fullEnd) {
        val writtenFirstEnd = writeDeflateBlock(
            data,
            output,
            false,
            symbols,
            symbolStart = 0,
            symbolCount = firstSymbolCount,
            blockStart = blockStart,
            blockLength = firstBlockLength,
            bitPosition = bitPosition,
            plan = firstPlan,
        )
        return writeDeflateBlock(
            data,
            output,
            isFinal,
            symbols,
            symbolStart = firstSymbolCount,
            symbolCount = symbolCount - firstSymbolCount,
            blockStart = blockStart + firstBlockLength,
            blockLength = secondBlockLength,
            bitPosition = writtenFirstEnd,
            plan = secondPlan,
        )
    }

    return writeDeflateBlock(
        data,
        output,
        isFinal,
        symbols,
        symbolStart = 0,
        symbolCount = symbolCount,
        blockStart = blockStart,
        blockLength = blockLength,
        bitPosition = bitPosition,
        plan = fullPlan,
    )
}

private fun buildDeflateBlockPlan(
    literalFrequencies: IntArray,
    distanceFrequencies: IntArray,
    extraBits: Int,
    blockStart: Int,
    blockLength: Int,
    bitPosition: Long,
): DeflateBlockPlan {
    var hasDistance = false
    for (frequency in distanceFrequencies) {
        if (frequency > 0) {
            hasDistance = true
            break
        }
    }
    literalFrequencies[256]++
    if (!hasDistance) {
        distanceFrequencies[0] = 1
    }

    val dynamicLiteralTree = buildHuffmanTreeFromFrequencies(literalFrequencies, 15)
    val dynamicDistanceTree = buildHuffmanTreeFromFrequencies(distanceFrequencies, 15)
    val (literalCodeLengths, numLiteralCodes) = generateLengthCodes(dynamicLiteralTree.tree)
    val (distanceCodeLengths, numDistanceCodes) = generateLengthCodes(dynamicDistanceTree.tree)

    val codeLengthFrequencies = IntArray(19)
    for (code in literalCodeLengths) {
        codeLengthFrequencies[code.toInt() and 31]++
    }
    for (code in distanceCodeLengths) {
        codeLengthFrequencies[code.toInt() and 31]++
    }

    val codeLengthTree = buildHuffmanTreeFromFrequencies(codeLengthFrequencies, 7)
    val numCodeLengthCodes = countCodeLengthCodes(codeLengthTree.tree)
    val codeLengthCodes = ShortArray(literalCodeLengths.size + distanceCodeLengths.size)
    literalCodeLengths.copyInto(codeLengthCodes, endIndex = literalCodeLengths.size)
    distanceCodeLengths.copyInto(
        codeLengthCodes,
        destinationOffset = literalCodeLengths.size,
        endIndex = distanceCodeLengths.size,
    )
    val fixedTypedLength = calculateCodeLength(literalFrequencies, FIXED_LENGTH_TREE) +
            calculateCodeLength(distanceFrequencies, FIXED_DISTANCE_TREE) + extraBits
    val dynamicTypedLength = calculateCodeLength(literalFrequencies, dynamicLiteralTree.tree) +
            calculateCodeLength(distanceFrequencies, dynamicDistanceTree.tree) + extraBits +
            14 + 3 * numCodeLengthCodes +
            calculateCodeLength(codeLengthFrequencies, codeLengthTree.tree) +
            2 * codeLengthFrequencies[16] + 3 * codeLengthFrequencies[17] +
            7 * codeLengthFrequencies[18]

    val usesStoredBlock = shouldUseStoredBlock(
        blockStart,
        storedBlockBitLength(blockLength, bitPosition),
        fixedTypedLength,
        dynamicTypedLength,
    )
    val blockType = when {
        usesStoredBlock -> DeflateBlockType.STORED
        dynamicTypedLength < fixedTypedLength -> DeflateBlockType.DYNAMIC
        else -> DeflateBlockType.FIXED
    }
    val typedLength = when (blockType) {
        DeflateBlockType.DYNAMIC -> dynamicTypedLength
        DeflateBlockType.FIXED -> fixedTypedLength
        DeflateBlockType.STORED -> 0
    }
    // A distance symbol is synthesized for an otherwise empty distance tree,
    // but literal-only blocks do not emit that symbol in their token stream.
    val syntheticDistanceBitLength = if (hasDistance || blockType == DeflateBlockType.STORED) {
        0
    } else {
        val distanceTree = if (blockType == DeflateBlockType.DYNAMIC) {
            dynamicDistanceTree.tree
        } else {
            FIXED_DISTANCE_TREE
        }
        calculateCodeLength(distanceFrequencies, distanceTree)
    }

    return DeflateBlockPlan(
        literalTree = dynamicLiteralTree,
        distanceTree = dynamicDistanceTree,
        codeLengthTree = codeLengthTree,
        codeLengthCodes = codeLengthCodes,
        numLiteralCodes = numLiteralCodes,
        numDistanceCodes = numDistanceCodes,
        fixedTypedLength = fixedTypedLength,
        dynamicTypedLength = dynamicTypedLength,
        blockType = blockType,
        emittedTypedLength = typedLength - syntheticDistanceBitLength,
    )
}

private fun deflateBlockEndBitPosition(
    plan: DeflateBlockPlan,
    bitPosition: Long,
    blockLength: Int,
): Long {
    if (plan.blockType == DeflateBlockType.STORED) {
        val bytePosition = shiftToNextByte(bitPosition + 3L)
        return (bytePosition.toLong() + 4L + blockLength.toLong()) * 8L
    }
    return bitPosition + 3L + plan.emittedTypedLength.toLong()
}

private fun writeDeflateBlock(
    data: ByteArray,
    output: ByteArray,
    isFinal: Boolean,
    symbols: IntArray,
    symbolStart: Int,
    symbolCount: Int,
    blockStart: Int,
    blockLength: Int,
    bitPosition: Long,
    plan: DeflateBlockPlan,
): Long {
    var currentBitPosition = bitPosition
    writeBits(output, currentBitPosition++, if (isFinal) 1 else 0)

    if (plan.blockType == DeflateBlockType.STORED) {
        return writeFixedBlock(output, currentBitPosition, data.sliceArray(blockStart until blockStart + blockLength))
    }

    val usesDynamicTree = plan.blockType == DeflateBlockType.DYNAMIC
    writeBits(output, currentBitPosition, 1 + if (usesDynamicTree) 1 else 0)
    currentBitPosition += 2

    val literalMap: ShortArray
    val literalLengths: ByteArray
    val distanceMap: ShortArray
    val distanceLengths: ByteArray
    if (usesDynamicTree) {
        literalMap = createHuffmanTree(plan.literalTree.tree, plan.literalTree.maxBits, false)
        literalLengths = plan.literalTree.tree
        distanceMap = createHuffmanTree(plan.distanceTree.tree, plan.distanceTree.maxBits, false)
        distanceLengths = plan.distanceTree.tree

        val codeLengthMap = createHuffmanTree(
            plan.codeLengthTree.tree,
            plan.codeLengthTree.maxBits,
            false,
        )
        val numCodeLengthCodes = countCodeLengthCodes(plan.codeLengthTree.tree)
        writeBits(output, currentBitPosition, plan.numLiteralCodes - 257)
        writeBits(output, currentBitPosition + 5, plan.numDistanceCodes - 1)
        writeBits(output, currentBitPosition + 10, numCodeLengthCodes - 4)
        currentBitPosition += 14

        for (i in 0 until numCodeLengthCodes) {
            writeBits(
                output,
                currentBitPosition + 3 * i,
                plan.codeLengthTree.tree.getOrNull(CODE_LENGTH_INDEX_MAP[i].toInt())?.toInt() ?: 0,
            )
        }
        currentBitPosition += 3 * numCodeLengthCodes

        for (code in plan.codeLengthCodes) {
            val len = code.toInt() and 31
            writeBits(output, currentBitPosition, codeLengthMap[len].toInt() and 0xFFFF)
            currentBitPosition += plan.codeLengthTree.tree[len].toInt() and 0xFF
            if (len > 15) {
                writeBits(output, currentBitPosition, (code.toInt() shr 5) and 127)
                currentBitPosition += code.toInt() shr 12
            }
        }
    } else {
        literalMap = FIXED_LENGTH_MAP
        literalLengths = FIXED_LENGTH_TREE
        distanceMap = FIXED_DISTANCE_MAP
        distanceLengths = FIXED_DISTANCE_TREE
    }

    for (i in symbolStart until symbolStart + symbolCount) {
        val symbol = symbols[i]
        if (symbol > 255) {
            val lengthSymbol = (symbol shr 18) and 31
            writeBits16(output, currentBitPosition, literalMap[lengthSymbol + 257].toInt() and 0xFFFF)
            currentBitPosition += literalLengths[lengthSymbol + 257].toInt() and 0xFF
            if (lengthSymbol > 7) {
                writeBits(output, currentBitPosition, (symbol shr 23) and 31)
                currentBitPosition += FIXED_LENGTH_EXTRA_BITS[lengthSymbol].toInt() and 0xFF
            }
            val distanceSymbol = symbol and 31
            writeBits16(output, currentBitPosition, distanceMap[distanceSymbol].toInt() and 0xFFFF)
            currentBitPosition += distanceLengths[distanceSymbol].toInt() and 0xFF
            if (distanceSymbol > 3) {
                writeBits16(output, currentBitPosition, (symbol shr 5) and 8191)
                currentBitPosition += FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF
            }
        } else {
            writeBits16(output, currentBitPosition, literalMap[symbol].toInt() and 0xFFFF)
            currentBitPosition += literalLengths[symbol].toInt() and 0xFF
        }
    }

    writeBits16(output, currentBitPosition, literalMap[256].toInt() and 0xFFFF)
    return currentBitPosition + (literalLengths[256].toInt() and 0xFF)
}

private fun populateBlockFrequencies(
    symbols: IntArray,
    symbolStart: Int,
    symbolCount: Int,
    literalFrequencies: IntArray,
    distanceFrequencies: IntArray,
): Int {
    var extraBits = 0
    for (index in symbolStart until symbolStart + symbolCount) {
        val symbol = symbols[index]
        if (symbol > 255) {
            val lengthSymbol = (symbol shr 18) and 31
            val distanceSymbol = symbol and 31
            literalFrequencies[257 + lengthSymbol]++
            distanceFrequencies[distanceSymbol]++
            extraBits += (FIXED_LENGTH_EXTRA_BITS[lengthSymbol].toInt() and 0xFF)
            extraBits += (FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF)
        } else {
            literalFrequencies[symbol]++
        }
    }
    return extraBits
}

private fun encodedSymbolByteLength(symbol: Int): Int {
    if (symbol <= 255) return 1
    val lengthSymbol = (symbol shr 18) and 31
    return (FIXED_LENGTH_BASE[lengthSymbol].toInt() and 0xFFFF) + ((symbol shr 23) and 31)
}

internal fun readTwoBytes(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}

internal fun readFourBytes(data: ByteArray, offset: Int): Long {
    return (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}

internal fun readFourBytesBE(data: ByteArray, offset: Int): Int {
    return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
}

internal fun readEightBytes(data: ByteArray, offset: Int): Long {
    return readFourBytes(data, offset) + (readFourBytes(data, offset + 4) * 4294967296L)
}

internal fun writeBytes(data: ByteArray, offset: Int, value: Long) {
    data[offset] = (value and 0xFF).toByte()
    data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    data[offset + 2] = ((value shr 16) and 0xFF).toByte()
    data[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

internal fun writeBytesBE(data: ByteArray, offset: Int, value: Int) {
    data[offset] = ((value shr 24) and 0xFF).toByte()
    data[offset + 1] = ((value shr 16) and 0xFF).toByte()
    data[offset + 2] = ((value shr 8) and 0xFF).toByte()
    data[offset + 3] = (value and 0xFF).toByte()
}

internal fun countCodeLengthCodes(codeLengthTree: ByteArray): Int {
    var count = 19
    while (count > 4 && codeLengthTree[CODE_LENGTH_INDEX_MAP[count - 1].toInt()].toInt() == 0) {
        count--
    }
    return count
}

internal fun shouldUseStoredBlock(
    blockStart: Int,
    storedLength: Int,
    fixedLength: Int,
    dynamicLength: Int,
): Boolean {
    return blockStart >= 0 && storedLength <= fixedLength && storedLength <= dynamicLength
}

/**
 * Returns the stored block cost after the three bit block header.
 *
 * The block type comparison excludes the common three bit header, while a stored block
 * includes the padding and four byte length header that follow it.
 */
internal fun storedBlockBitLength(blockLength: Int, bitPosition: Long): Int {
    val headerEnd = bitPosition + 3L
    val padding = ((8L - (headerEnd and 7L)) and 7L).toInt()
    return (blockLength shl 3) + 32 + padding
}
