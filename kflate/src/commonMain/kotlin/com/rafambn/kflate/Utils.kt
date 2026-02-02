package com.rafambn.kflate

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

    fun safe(index: Int): Int = if (index < data.size) data[index].toInt() and 0xFF else 0

    return ((safe(byteOffset) or (safe(byteOffset + 1) shl 8)) shr (bitPosition and 7)) and bitMask
}

internal fun readBits16(data: ByteArray, bitPosition: Int): Int {
    val byteOffset = bitPosition / 8

    fun safe(index: Int): Int =
        if (index < data.size) data[index].toInt() and 0xFF else 0

    return ((safe(byteOffset)) or
            (safe(byteOffset + 1) shl 8) or
            (safe(byteOffset + 2) shl 16)) shr (bitPosition and 7)
}

internal fun shiftToNextByte(bitPosition: Int): Int {
    return (bitPosition + 7) / 8
}

internal fun writeBits(data: ByteArray, bitPosition: Int, value: Int) {
    val shiftedValue = value shl (bitPosition and 7)
    val byteIndex = bitPosition / 8
    data[byteIndex] = ((data[byteIndex].toInt() and 0xFF) or shiftedValue).toByte()
    data[byteIndex + 1] = ((data[byteIndex + 1].toInt() and 0xFF) or (shiftedValue shr 8)).toByte()
}

internal fun writeBits16(data: ByteArray, bitPosition: Int, value: Int) {
    val shiftedValue = value shl (bitPosition and 7)
    val byteIndex = bitPosition / 8
    data[byteIndex] = ((data[byteIndex].toInt() and 0xFF) or shiftedValue).toByte()
    data[byteIndex + 1] = ((data[byteIndex + 1].toInt() and 0xFF) or (shiftedValue shr 8)).toByte()
    data[byteIndex + 2] = ((data[byteIndex + 2].toInt() and 0xFF) or (shiftedValue shr 16)).toByte()
}

internal fun writeFixedBlock(output: ByteArray, bitPosition: Int, data: ByteArray): Int {
    val dataLength = data.size
    val bytePosition = shiftToNextByte(bitPosition + 2)
    output[bytePosition] = (dataLength and 255).toByte()
    output[bytePosition + 1] = (dataLength shr 8).toByte()
    output[bytePosition + 2] = (output[bytePosition].toInt() xor 255).toByte()
    output[bytePosition + 3] = (output[bytePosition + 1].toInt() xor 255).toByte()
    for (i in data.indices) {
        output[bytePosition + i + 4] = data[i]
    }
    return (bytePosition + 4 + dataLength) * 8
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
    bitPosition: Int
): Int {
    var currentBitPosition = bitPosition
    writeBits(output, currentBitPosition++, if (isFinal) 1 else 0)
    literalFrequencies[256]++
    var hasDistance = false
    for (frequency in distanceFrequencies) {
        if (frequency > 0) {
            hasDistance = true
            break
        }
    }
    if (!hasDistance) {
        distanceFrequencies[0] = 1
    }

    val (dynamicLiteralTree, maxLiteralBits) = buildHuffmanTreeFromFrequencies(literalFrequencies, 15)
    val (dynamicDistanceTree, maxDistanceBits) = buildHuffmanTreeFromFrequencies(distanceFrequencies, 15)
    val (literalCodeLengths, numLiteralCodes) = generateLengthCodes(dynamicLiteralTree)
    val (distanceCodeLengths, numDistanceCodes) = generateLengthCodes(dynamicDistanceTree)

    val codeLengthFrequencies = IntArray(19)
    for (i in literalCodeLengths.indices) {
        codeLengthFrequencies[(literalCodeLengths[i].toInt() and 31)]++
    }
    for (i in distanceCodeLengths.indices) {
        codeLengthFrequencies[(distanceCodeLengths[i].toInt() and 31)]++
    }

    val (codeLengthTree, maxCodeLengthBits) = buildHuffmanTreeFromFrequencies(codeLengthFrequencies, 7)
    var numCodeLengthCodes = 19
    while (numCodeLengthCodes > 4 && codeLengthTree[CODE_LENGTH_INDEX_MAP[numCodeLengthCodes - 1].toInt()].toInt() == 0) {
        numCodeLengthCodes--
    }

    val fixedBlockLength = (blockLength + 5) shl 3
    val fixedTypedLength = calculateCodeLength(literalFrequencies, FIXED_LENGTH_TREE) +
            calculateCodeLength(distanceFrequencies, FIXED_DISTANCE_TREE) + extraBits
    val dynamicTypedLength = calculateCodeLength(literalFrequencies, dynamicLiteralTree) +
            calculateCodeLength(distanceFrequencies, dynamicDistanceTree) + extraBits + 14 + 3 * numCodeLengthCodes +
            calculateCodeLength(codeLengthFrequencies, codeLengthTree) + 2 * codeLengthFrequencies[16] +
            3 * codeLengthFrequencies[17] + 7 * codeLengthFrequencies[18]

    if (blockStart >= 0 && fixedBlockLength <= fixedTypedLength && fixedBlockLength <= dynamicTypedLength) {
        return writeFixedBlock(output, currentBitPosition, data.sliceArray(blockStart until blockStart + blockLength))
    }

    var literalMap: ShortArray
    var literalLengths: ByteArray
    var distanceMap: ShortArray
    var distanceLengths: ByteArray

    writeBits(output, currentBitPosition, 1 + if (dynamicTypedLength < fixedTypedLength) 1 else 0)
    currentBitPosition += 2

    if (dynamicTypedLength < fixedTypedLength) {
        literalMap = createHuffmanTree(dynamicLiteralTree, maxLiteralBits, false)
        literalLengths = dynamicLiteralTree
        distanceMap = createHuffmanTree(dynamicDistanceTree, maxDistanceBits, false)
        distanceLengths = dynamicDistanceTree

        val codeLengthMap = createHuffmanTree(codeLengthTree, maxCodeLengthBits, false)
        writeBits(output, currentBitPosition, numLiteralCodes - 257)
        writeBits(output, currentBitPosition + 5, numDistanceCodes - 1)
        writeBits(output, currentBitPosition + 10, numCodeLengthCodes - 4)
        currentBitPosition += 14

        for (i in 0 until numCodeLengthCodes)
            writeBits(output, currentBitPosition + 3 * i, codeLengthTree.getOrNull(CODE_LENGTH_INDEX_MAP[i].toInt())?.toInt() ?: 0)
        currentBitPosition += 3 * numCodeLengthCodes

        val codeLengthTrees = arrayOf(literalCodeLengths, distanceCodeLengths)
        for (tree in codeLengthTrees) {
            for (i in tree.indices) {
                val len = tree[i].toInt() and 31
                writeBits(output, currentBitPosition, (codeLengthMap[len].toInt() and 0xFFFF))
                currentBitPosition += codeLengthTree[len].toInt() and 0xFF
                if (len > 15) {
                    writeBits(output, currentBitPosition, (tree[i].toInt() shr 5) and 127)
                    currentBitPosition += (tree[i].toInt() shr 12)
                }
            }
        }
    } else {
        literalMap = FIXED_LENGTH_MAP
        literalLengths = FIXED_LENGTH_TREE
        distanceMap = FIXED_DISTANCE_MAP
        distanceLengths = FIXED_DISTANCE_TREE
    }

    for (i in 0 until symbolCount) {
        val symbol = symbols[i]
        if (symbol > 255) {
            val lengthSymbol = (symbol shr 18) and 31
            writeBits16(output, currentBitPosition, (literalMap[lengthSymbol + 257].toInt() and 0xFFFF))
            currentBitPosition += literalLengths[lengthSymbol + 257].toInt() and 0xFF
            if (lengthSymbol > 7) {
                writeBits(output, currentBitPosition, (symbol shr 23) and 31)
                currentBitPosition += FIXED_LENGTH_EXTRA_BITS[lengthSymbol].toInt() and 0xFF
            }
            val distanceSymbol = symbol and 31
            writeBits16(output, currentBitPosition, (distanceMap[distanceSymbol].toInt() and 0xFFFF))
            currentBitPosition += distanceLengths[distanceSymbol].toInt() and 0xFF
            if (distanceSymbol > 3) {
                writeBits16(output, currentBitPosition, (symbol shr 5) and 8191)
                currentBitPosition += FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF
            }
        } else {
            writeBits16(output, currentBitPosition, (literalMap[symbol].toInt() and 0xFFFF))
            currentBitPosition += literalLengths[symbol].toInt() and 0xFF
        }
    }

    writeBits16(output, currentBitPosition, (literalMap[256].toInt() and 0xFFFF))
    return currentBitPosition + (literalLengths[256].toInt() and 0xFF)
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
