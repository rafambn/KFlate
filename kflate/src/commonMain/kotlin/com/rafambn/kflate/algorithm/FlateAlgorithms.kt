package com.rafambn.kflate.algorithm

import com.rafambn.kflate.compression.CompressionType
import com.rafambn.kflate.compression.Raw
import com.rafambn.kflate.compression.Gzip
import com.rafambn.kflate.compression.Zlib
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.huffman.FIXED_DISTANCE_BASE
import com.rafambn.kflate.huffman.FIXED_DISTANCE_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_MAP
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_BASE
import com.rafambn.kflate.huffman.FIXED_LENGTH_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_MAP
import com.rafambn.kflate.huffman.CODE_LENGTH_INDEX_MAP
import com.rafambn.kflate.huffman.createHuffmanTree
import com.rafambn.kflate.huffman.validateHuffmanCodeLengths
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.util.findMaxValue
import com.rafambn.kflate.util.readBits
import com.rafambn.kflate.util.readBits16
import com.rafambn.kflate.util.readTwoBytes
import com.rafambn.kflate.util.shiftToNextByte
import com.rafambn.kflate.util.writeBlock
import com.rafambn.kflate.util.writeFixedBlock
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

internal fun inflate(
    inputData: ByteArray,
    inflateState: InflateState,
    dictionary: ByteArray? = null,
    maxOutputSize: Int? = null,
): ByteArray {
    val sourceLength = inputData.size
    val dictionaryLength = dictionary?.size ?: 0

    if (inflateState.isFinalBlock && inflateState.literalMap == null) {
        return ByteArray(0)
    }
    if (sourceLength == 0) {
        if (inflateState.validationMode != 0) {
            throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        return ByteArray(0)
    }
    validateInflateInputSize(sourceLength)

    val hasNoStoredState = inflateState.validationMode != 0
    val suggestedCapacity = minOf(
        maxOf(sourceLength.toLong() * 3L, 32_768L),
        1_048_576L,
    ).toInt()
    val initialCapacity = maxOutputSize?.let { minOf(it, suggestedCapacity) } ?: suggestedCapacity
    var workingBuffer = ByteArray(initialCapacity)

    fun ensureCapacity(additionalBytes: Int, bytesWritten: Int) {
        val requiredSizeLong = bytesWritten.toLong() + additionalBytes.toLong()
        if (maxOutputSize != null && requiredSizeLong > maxOutputSize.toLong()) {
            throw FlateError(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED)
        }
        if (requiredSizeLong > Int.MAX_VALUE.toLong()) {
            throw FlateError(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED)
        }
        val requiredSize = requiredSizeLong.toInt()
        val currentBuffer = workingBuffer
        if (requiredSize > currentBuffer.size) {
            val doubledSize = minOf(
                maxOf(currentBuffer.size.toLong() * 2L, 1L),
                Int.MAX_VALUE.toLong(),
            ).toInt()
            val grownSize = maxOf(doubledSize, requiredSize)
            val newSize = maxOutputSize?.let { minOf(grownSize, it) } ?: grownSize
            val newBuffer = ByteArray(newSize)
            currentBuffer.copyInto(newBuffer)
            workingBuffer = newBuffer
        }
    }

    var isFinalBlock = inflateState.isFinalBlock
    var currentBitPosition = inflateState.inputBitPosition
    var bytesWrittenToOutput = inflateState.outputOffset
    var literalLengthMap = inflateState.literalMap
    var distanceMap = inflateState.distanceMap
    var literalMaxBits = inflateState.literalMaxBits
    var distanceMaxBits = inflateState.distanceMaxBits

    val totalAvailableBits = sourceLength * 8

    do {
        if (literalLengthMap == null) {
            // Need at least 3 bits for block header (1 BFINAL + 2 BTYPE)
            if (currentBitPosition + 3 > totalAvailableBits) {
                if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }
            isFinalBlock = readBits(inputData, currentBitPosition, 1) != 0
            val blockType = readBits(inputData, currentBitPosition + 1, 3)
            currentBitPosition += 3

            when (blockType) {
                0 -> {
                    val blockStartByte = shiftToNextByte(currentBitPosition)

                    // Check if at least 4 bytes remain for LEN and NLEN
                    if (blockStartByte + 4 > sourceLength) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val blockLength = readTwoBytes(inputData, blockStartByte)
                    val blockNlen = readTwoBytes(inputData, blockStartByte + 2)

                    // Validate that NLEN is the one's complement of LEN
                    if ((blockLength xor 0xFFFF) != blockNlen) {
                        throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                    }

                    val dataStartByte = blockStartByte + 4
                    val blockEndByte = dataStartByte + blockLength

                    if (blockEndByte > sourceLength) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    ensureCapacity(blockLength, bytesWrittenToOutput)

                    inputData.copyInto(
                        workingBuffer,
                        destinationOffset = bytesWrittenToOutput,
                        startIndex = dataStartByte,
                        endIndex = blockEndByte
                    )

                    bytesWrittenToOutput += blockLength
                    currentBitPosition = blockEndByte * 8

                    inflateState.outputOffset = bytesWrittenToOutput
                    inflateState.inputBitPosition = currentBitPosition
                    inflateState.isFinalBlock = isFinalBlock
                    continue
                }

                1 -> {
                    literalLengthMap = FIXED_LENGTH_REVERSE_MAP
                    distanceMap = FIXED_DISTANCE_REVERSE_MAP
                    literalMaxBits = 9
                    distanceMaxBits = 5
                }

                2 -> {
                    // Check if we have at least 14 bits for the block header
                    if (currentBitPosition + 14 > totalAvailableBits) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val numLiteralCodes = readBits(inputData, currentBitPosition, 31) + 257
                    val numDistanceCodes = readBits(inputData, currentBitPosition + 5, 31) + 1
                    val numCodeLengthCodes = readBits(inputData, currentBitPosition + 10, 15) + 4

                    // RFC 1951: HLIT max is 29 (286 codes), HDIST max is 31 (32 codes)
                    // Distance codes 30-31 are never used in valid data but may appear in the tree
                    if (numLiteralCodes > 286) {
                        throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                    }

                    val totalCodes = numLiteralCodes + numDistanceCodes
                    currentBitPosition += 14

                    // Check if we have enough bits for the code length tree
                    val codeLengthTreeBits = numCodeLengthCodes * 3
                    if (currentBitPosition + codeLengthTreeBits > totalAvailableBits) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val codeLengthTree = ByteArray(19)
                    for (i in 0 until numCodeLengthCodes) {
                        codeLengthTree[CODE_LENGTH_INDEX_MAP[i].toInt()] = readBits(inputData, currentBitPosition + i * 3, 7).toByte()
                    }
                    currentBitPosition += numCodeLengthCodes * 3

                    val codeLengthMaxBits = findMaxValue(codeLengthTree)

                    // Validate code-length tree
                    validateCodeLengthTree(codeLengthTree, codeLengthMaxBits)

                    val codeLengthBitMask = (1 shl codeLengthMaxBits) - 1
                    val codeLengthHuffmanMap = createHuffmanTree(codeLengthTree, codeLengthMaxBits, true)

                    val allCodeLengths = ByteArray(totalCodes)
                    var codeIndex = 0

                    while (codeIndex < totalCodes) {
                        val availableBits = totalAvailableBits - currentBitPosition
                        if (availableBits <= 0) {
                            throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        }

                        val huffmanCode = codeLengthHuffmanMap[readBits(inputData, currentBitPosition, codeLengthBitMask)]
                        val huffmanCodeLength = huffmanCode.toInt() and 15
                        validateCodeLengthEntry(huffmanCodeLength, availableBits, codeLengthMaxBits)
                        currentBitPosition += huffmanCodeLength
                        val symbol = huffmanCode.toInt() shr 4

                        when {
                            symbol < 16 -> {
                                allCodeLengths[codeIndex++] = symbol.toByte()
                            }

                            symbol == 16 -> {
                                if (codeIndex == 0) {
                                    throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                if (currentBitPosition + 2 > totalAvailableBits) {
                                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                                }
                                val repeatCount = 3 + readBits(inputData, currentBitPosition, 3)
                                currentBitPosition += 2
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                val valueToRepeat = allCodeLengths[codeIndex - 1]
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = valueToRepeat }
                            }

                            symbol == 17 -> {
                                if (currentBitPosition + 3 > totalAvailableBits) {
                                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                                }
                                val repeatCount = 3 + readBits(inputData, currentBitPosition, 7)
                                currentBitPosition += 3
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = 0 }
                            }

                            else -> {
                                if (currentBitPosition + 7 > totalAvailableBits) {
                                    throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                                }
                                val repeatCount = 11 + readBits(inputData, currentBitPosition, 127)
                                currentBitPosition += 7
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = 0 }
                            }
                        }
                    }

                    val literalLengthCodeLengths = allCodeLengths.copyOfRange(0, numLiteralCodes)
                    val distanceCodeLengths = allCodeLengths.copyOfRange(numLiteralCodes, totalCodes)

                    // Validate that end-of-block symbol (256) has a non-zero code length
                    if (literalLengthCodeLengths[256].toInt() == 0) {
                        throw FlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                    }

                    literalMaxBits = findMaxValue(literalLengthCodeLengths)
                    distanceMaxBits = findMaxValue(distanceCodeLengths)

                    // Validate literal/length tree
                    if (!validateHuffmanCodeLengths(literalLengthCodeLengths, literalMaxBits)) {
                        throw FlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                    }

                    literalLengthMap = createHuffmanTree(literalLengthCodeLengths, literalMaxBits, true)

                    // Validate distance tree
                    if (distanceMaxBits > 0) {
                        if (!validateHuffmanCodeLengths(distanceCodeLengths, distanceMaxBits)) {
                            throw FlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                        }
                    }

                    distanceMap = createHuffmanTree(distanceCodeLengths, distanceMaxBits, true)
                }

                else -> throw FlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
            }

        }

        val literalBitMask = (1 shl literalMaxBits) - 1
        val distanceBitMask = (1 shl distanceMaxBits) - 1
        var lastBitPosition = currentBitPosition
        val currentLitMap = literalLengthMap
        val currentDistMap = distanceMap!!

        while (true) {
            val availableLiteralBits = totalAvailableBits - currentBitPosition
            if (availableLiteralBits <= 0) {
                if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }
            val literalCode = (currentLitMap[readBits16(inputData, currentBitPosition) and literalBitMask].toInt() and 0xFFFF)
            val literalCodeLength = literalCode and 15
            if (literalCode == 0 && availableLiteralBits < literalMaxBits) {
                if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }
            if (literalCodeLength > availableLiteralBits) {
                if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }
            val symbol = literalCode shr 4
            currentBitPosition += literalCodeLength

            // Symbols 0..285 are valid. Fixed Huffman symbols 286 and 287 are reserved by RFC 1951.
            if (literalCode == 0 || symbol > 285) {
                throw FlateError(FlateErrorCode.INVALID_LENGTH_LITERAL)
            }

            when {
                symbol < 256 -> {
                    ensureCapacity(1, bytesWrittenToOutput)
                    workingBuffer[bytesWrittenToOutput++] = symbol.toByte()
                    lastBitPosition = currentBitPosition
                }

                symbol == 256 -> {
                    lastBitPosition = currentBitPosition
                    literalLengthMap = null
                    break
                }

                else -> {
                    var matchLength = symbol - 254

                    if (symbol > 264) {
                        val lengthIndex = symbol - 257
                        val extraBits = FIXED_LENGTH_EXTRA_BITS[lengthIndex].toInt() and 0xFF
                        if (currentBitPosition + extraBits > totalAvailableBits) {
                            if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                            break
                        }
                        matchLength =
                            readBits(inputData, currentBitPosition, (1 shl extraBits) - 1) + (FIXED_LENGTH_BASE[lengthIndex].toInt() and 0xFFFF)
                        currentBitPosition += extraBits
                    }

                    val availableDistanceBits = totalAvailableBits - currentBitPosition
                    if (availableDistanceBits <= 0) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }
                    val distanceCode = (currentDistMap[readBits16(inputData, currentBitPosition) and distanceBitMask].toInt() and 0xFFFF)
                    val distanceCodeLength = distanceCode and 15
                    if (distanceCode == 0 && availableDistanceBits < distanceMaxBits) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }
                    if (distanceCodeLength > availableDistanceBits) {
                        if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }
                    val distanceSymbol = distanceCode shr 4
                    if (distanceCode == 0) throw FlateError(FlateErrorCode.INVALID_DISTANCE)
                    // RFC 1951: Distance codes 30-31 will never occur in valid compressed data
                    if (distanceSymbol >= 30) throw FlateError(FlateErrorCode.INVALID_DISTANCE)
                    currentBitPosition += distanceCodeLength

                    var matchDistance = FIXED_DISTANCE_BASE[distanceSymbol].toInt() and 0xFFFF
                    if (distanceSymbol > 3) {
                        val extraBits = FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF
                        if (currentBitPosition + extraBits > totalAvailableBits) {
                            if (hasNoStoredState) throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
                            break
                        }
                        matchDistance += readBits16(inputData, currentBitPosition) and ((1 shl extraBits) - 1)
                        currentBitPosition += extraBits
                    }

                    ensureCapacity(matchLength, bytesWrittenToOutput)

                    val copyEndIndex = bytesWrittenToOutput + matchLength
                    val buffer = workingBuffer

                    if (bytesWrittenToOutput < matchDistance) {
                        val dictionaryOffset = dictionaryLength - matchDistance
                        val dictionaryEndIndex = minOf(matchDistance, copyEndIndex)
                        if (dictionaryOffset + bytesWrittenToOutput < 0) {
                            throw FlateError(FlateErrorCode.INVALID_DISTANCE)
                        }

                        dictionary!!.copyInto(
                            buffer,
                            destinationOffset = bytesWrittenToOutput,
                            startIndex = dictionaryOffset + bytesWrittenToOutput,
                            endIndex = dictionaryOffset + dictionaryEndIndex
                        )
                        bytesWrittenToOutput = dictionaryEndIndex
                    }

                    while (bytesWrittenToOutput < copyEndIndex) {
                        buffer[bytesWrittenToOutput] = buffer[bytesWrittenToOutput - matchDistance]
                        bytesWrittenToOutput++
                    }
                    lastBitPosition = currentBitPosition
                }
            }
        }

        inflateState.literalMap = literalLengthMap
        inflateState.inputBitPosition = lastBitPosition
        inflateState.outputOffset = bytesWrittenToOutput
        inflateState.isFinalBlock = isFinalBlock

        if (literalLengthMap != null) {
            isFinalBlock = true
            inflateState.literalMaxBits = literalMaxBits
            inflateState.distanceMap = distanceMap
            inflateState.distanceMaxBits = distanceMaxBits
        }

    } while (!isFinalBlock)

    return workingBuffer.copyOfRange(0, bytesWrittenToOutput)
}

internal fun deflate(
    data: ByteArray,
    level: Int,
    compressionLevel: Int,
    prefixSize: Int,
    postfixSize: Int,
    state: DeflateState
): ByteArray {
    val dataSize = state.inputEndIndex.takeIf { it != 0 } ?: data.size
    // Heuristic: dataSize + 1/8th of dataSize (for expansion) + 256 (for tree/header overhead) + 5 per block
    val bufferMargin = (dataSize.toLong() shr 3) + 256L + 5L * (1L + dataSize / 7_000L)
    val writeBufferSize = dataSize.toLong() + bufferMargin
    if (writeBufferSize > Int.MAX_VALUE.toLong()) {
        throw FlateError(FlateErrorCode.INPUT_TOO_LARGE)
    }
    val writeBuffer = ByteArray(writeBufferSize.toInt())
    val isLastBlock = state.isLastChunk
    var bitPosition: Long = (state.bitBuffer and 7).toLong()

    if (level > 0) {
        if (bitPosition != 0L) {
            writeBuffer[0] = (state.bitBuffer shr 3).toByte()
        }
        val levelOptions = DEFLATE_LEVELS[level]
        val mask = (1 shl compressionLevel) - 1
        val prev = state.prev ?: ShortArray(MATCH_DISTANCE_MASK + 1)
        val head = state.head ?: ShortArray(mask + 1)
        val baseShift1 = ceil(compressionLevel / 3.0).toInt()
        val baseShift2 = 2 * baseShift1

        val symbols = IntArray(65536)
        val literalFrequencies = IntArray(288)
        val distanceFrequencies = IntArray(32)
        var matchCount = 0
        var extraBits = 0
        var i = state.inputOffset
        var symbolIndex = 0
        var waitIndex = state.waitIndex
        var blockStart = maxOf(state.inputOffset, waitIndex)
        var pendingMatch = 0

        if (levelOptions.usesCostAwareParsing) {
            while (i < waitIndex && i + 2 < dataSize) {
                val hashValue = deflateHash(data, i, baseShift1, baseShift2, mask)
                val iMod = i and MATCH_DISTANCE_MASK
                prev[iMod] = head[hashValue]
                head[hashValue] = iMod.toShort()
                i++
            }
            i = maxOf(i, waitIndex)
            blockStart = i

            val costWindowSize = minOf(COST_AWARE_WINDOW_SIZE, maxOf(1, dataSize - i))
            val matches = IntArray(costWindowSize)
            val costs = IntArray(costWindowSize + 1)
            val choices = IntArray(costWindowSize)
            var hashedUntil = i

            while (i < dataSize) {
                val hashEnd = minOf(i, dataSize - 2)
                while (hashedUntil < hashEnd) {
                    val hashValue = deflateHash(data, hashedUntil, baseShift1, baseShift2, mask)
                    val hashIndex = hashedUntil and MATCH_DISTANCE_MASK
                    prev[hashIndex] = head[hashValue]
                    head[hashValue] = hashIndex.toShort()
                    hashedUntil++
                }

                val windowStart = i
                val windowEnd = minOf(dataSize, windowStart + costWindowSize)
                val windowSize = windowEnd - windowStart
                matches.fill(0, 0, windowSize)

                var scanIndex = windowStart
                while (scanIndex < windowEnd) {
                    if (scanIndex + 2 >= dataSize) break
                    val hashValue = deflateHash(data, scanIndex, baseShift1, baseShift2, mask)
                    val scanIndexMod = scanIndex and MATCH_DISTANCE_MASK
                    val previousIndex = head[hashValue].toInt() and 0xFFFF
                    prev[scanIndexMod] = previousIndex.toShort()
                    head[hashValue] = scanIndexMod.toShort()
                    matches[scanIndex - windowStart] =
                        findLongestMatch(data, dataSize, scanIndex, previousIndex, prev, levelOptions)
                    scanIndex++
                }
                hashedUntil = maxOf(hashedUntil, scanIndex)

                chooseCostAwarePath(data, windowStart, windowEnd, matches, costs, choices)

                while (i < windowEnd) {
                    val remaining = dataSize - i
                    if (shouldFlushBlock(matchCount, symbolIndex, remaining, isLastBlock)) {
                        bitPosition = writeBlock(
                            data, writeBuffer, false, symbols, literalFrequencies, distanceFrequencies,
                            extraBits, symbolIndex, blockStart, i - blockStart, bitPosition
                        )
                        symbolIndex = 0
                        matchCount = 0
                        extraBits = 0
                        blockStart = i
                        literalFrequencies.fill(0, 0, 286)
                        distanceFrequencies.fill(0, 0, 30)
                    }

                    val match = matches[i - windowStart]
                    val length = choices[i - windowStart]
                    if (length == 1) {
                        symbols[symbolIndex++] = data[i].toInt() and 0xFF
                        ++literalFrequencies[data[i].toInt() and 0xFF]
                    } else {
                        val distance = match and MATCH_DISTANCE_MASK
                        symbols[symbolIndex++] =
                            268435456 or
                                (FIXED_LENGTH_REVERSE_LOOKUP[length] shl 18) or
                                FIXED_DISTANCE_REVERSE_LOOKUP[distance]
                        val lenIndex = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
                        val distIndex = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
                        extraBits +=
                            (FIXED_LENGTH_EXTRA_BITS[lenIndex].toInt() and 0xFF) +
                                (FIXED_DISTANCE_EXTRA_BITS[distIndex].toInt() and 0xFF)
                        ++literalFrequencies[257 + lenIndex]
                        ++distanceFrequencies[distIndex]
                        ++matchCount
                    }
                    i += length
                }
            }
            waitIndex = i
        } else {
            while (i + 2 < dataSize) {
                val hashValue = deflateHash(data, i, baseShift1, baseShift2, mask)
                val iMod = i and MATCH_DISTANCE_MASK
                val pIMod = head[hashValue].toInt() and 0xFFFF
                prev[iMod] = pIMod.toShort()
                head[hashValue] = iMod.toShort()

                if (waitIndex <= i) {
                    val remaining = dataSize - i
                    if (shouldFlushBlock(matchCount, symbolIndex, remaining, isLastBlock)) {
                        bitPosition = writeBlock(
                            data, writeBuffer, false, symbols, literalFrequencies, distanceFrequencies,
                            extraBits, symbolIndex, blockStart, i - blockStart, bitPosition
                        )
                        symbolIndex = 0
                        matchCount = 0
                        extraBits = 0
                        blockStart = i
                        literalFrequencies.fill(0, 0, 286)
                        distanceFrequencies.fill(0, 0, 30)
                    }

                    val match = if (pendingMatch != 0) {
                        pendingMatch.also { pendingMatch = 0 }
                    } else {
                        findLongestMatch(data, dataSize, i, pIMod, prev, levelOptions)
                    }
                    val length = match ushr MATCH_DISTANCE_BITS
                    val distance = match and MATCH_DISTANCE_MASK

                    val nextMatch = if (shouldSearchLazyMatch(length, levelOptions.maxLazyLength, remaining)) {
                        val nextIndex = i + 1
                        val nextHash = deflateHash(data, nextIndex, baseShift1, baseShift2, mask)
                        findLongestMatch(data, dataSize, nextIndex, head[nextHash].toInt() and 0xFFFF, prev, levelOptions)
                    } else {
                        0
                    }

                    if ((nextMatch ushr MATCH_DISTANCE_BITS) > length) {
                        pendingMatch = nextMatch
                        symbols[symbolIndex++] = data[i].toInt() and 0xFF
                        ++literalFrequencies[data[i].toInt() and 0xFF]
                    } else if (distance != 0) {
                        symbols[symbolIndex++] =
                            268435456 or
                                (FIXED_LENGTH_REVERSE_LOOKUP[length] shl 18) or
                                FIXED_DISTANCE_REVERSE_LOOKUP[distance]
                        val lenIndex = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
                        val distIndex = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
                        extraBits +=
                            (FIXED_LENGTH_EXTRA_BITS[lenIndex].toInt() and 0xFF) +
                                (FIXED_DISTANCE_EXTRA_BITS[distIndex].toInt() and 0xFF)
                        ++literalFrequencies[257 + lenIndex]
                        ++distanceFrequencies[distIndex]
                        waitIndex = i + length
                        ++matchCount
                    } else {
                        symbols[symbolIndex++] = data[i].toInt() and 0xFF
                        ++literalFrequencies[data[i].toInt() and 0xFF]
                    }
                }
                i++
            }

            i = maxOf(i, waitIndex)
        }

        while (i < dataSize) {
            symbols[symbolIndex++] = data[i].toInt() and 0xFF
            literalFrequencies[data[i].toInt() and 0xFF]++
            i++
        }

        bitPosition = writeBlock(
            data, writeBuffer, isLastBlock, symbols, literalFrequencies, distanceFrequencies,
            extraBits, symbolIndex, blockStart, i - blockStart, bitPosition
        )

        if (!isLastBlock) {
            state.bitBuffer = (bitPosition and 7L).toInt() or ((writeBuffer[(bitPosition / 8).toInt()].toInt() and 0xFF) shl 3)
            bitPosition -= 7
            state.head = head
            state.prev = prev
            state.inputOffset = i
            state.waitIndex = waitIndex
        }
    } else {
        var i = maxOf(state.waitIndex, state.inputOffset)
        val lastBlockFlag = if (isLastBlock) 1 else 0
        while (i < dataSize + lastBlockFlag) {
            var end = i + 65535
            if (end >= dataSize) {
                writeBuffer[(bitPosition / 8).toInt()] = lastBlockFlag.toByte()
                end = dataSize
            }
            bitPosition = writeFixedBlock(writeBuffer, bitPosition + 1, data.sliceArray(i until end))
            i += 65535
        }
        state.inputOffset = dataSize
    }
    val compressedSize = shiftToNextByte(bitPosition)
    val outputSize = prefixSize.toLong() + compressedSize.toLong() + postfixSize.toLong()
    if (outputSize > Int.MAX_VALUE.toLong()) {
        throw FlateError(FlateErrorCode.INPUT_TOO_LARGE)
    }
    val output = ByteArray(outputSize.toInt())
    writeBuffer.copyInto(
        output,
        destinationOffset = prefixSize,
        startIndex = 0,
        endIndex = compressedSize,
    )
    return output
}

internal fun deflateWithOptions(
    inputData: ByteArray,
    type: CompressionType = Raw(),
    prefixSize: Int,
    suffixSize: Int,
    deflateState: DeflateState? = null
): ByteArray {
    var workingState = deflateState
    var workingData = inputData

    val level = type.level
    val mem = type.mem
    val dictionary = when (type) {
        is Raw -> type.dictionary
        is Gzip -> null
        is Zlib -> type.dictionary
    }

    if (workingState == null) {
        workingState = DeflateState(isLastChunk = true)

        if (dictionary != null) {
            val combinedData = ByteArray(checkedDeflateInputSize(dictionary.size, inputData.size))

            dictionary.copyInto(combinedData, destinationOffset = 0)

            inputData.copyInto(combinedData, destinationOffset = dictionary.size)

            workingData = combinedData
            workingState.waitIndex = dictionary.size
        }
    }

    val memoryUsage = if (workingState.isLastChunk && mem == 8) {
        minOf(DEFLATE_LEVELS[level].maxHashBits, ceil(max(8.0, min(13.0, ln(workingData.size.toDouble()))) * 1.5).toInt())
    } else {
        mem + 12
    }

    return deflate(
        workingData,
        level,
        memoryUsage,
        prefixSize,
        suffixSize,
        workingState
    )
}

internal fun validateInflateInputSize(sourceLength: Int) {
    if (sourceLength > (Int.MAX_VALUE - 64) / 8) {
        throw FlateError(FlateErrorCode.INPUT_TOO_LARGE)
    }
}

internal fun checkedDeflateInputSize(dictionarySize: Int, inputSize: Int): Int {
    val combinedSize = dictionarySize.toLong() + inputSize.toLong()
    if (combinedSize > Int.MAX_VALUE.toLong()) {
        throw FlateError(FlateErrorCode.INPUT_TOO_LARGE)
    }
    return combinedSize.toInt()
}

internal fun validateCodeLengthEntry(codeLength: Int, availableBits: Int, maxBits: Int) {
    if (codeLength == 0) {
        if (availableBits < maxBits) {
            throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        throw FlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
    }
    if (codeLength > availableBits) {
        throw FlateError(FlateErrorCode.UNEXPECTED_EOF)
    }
}

internal fun validateCodeLengthTree(codeLengths: ByteArray, maxBits: Int) {
    if (maxBits == 0 || !validateHuffmanCodeLengths(codeLengths, maxBits)) {
        throw FlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
    }
}

internal fun shouldFlushBlock(
    matchCount: Int,
    symbolCount: Int,
    remaining: Int,
    isLastBlock: Boolean,
): Boolean {
    return (matchCount > 7_000 || symbolCount > 24_576) && (remaining > 423 || !isLastBlock)
}

internal fun hasThreeByteMatch(data: ByteArray, index: Int, distance: Int, remaining: Int): Boolean {
    return remaining > 2 &&
            data[index] == data[index - distance] &&
            data[index + 1] == data[index - distance + 1] &&
            data[index + 2] == data[index - distance + 2]
}

internal fun shouldSearchLazyMatch(length: Int, maxLazyLength: Int, remaining: Int): Boolean {
    return maxLazyLength > 0 && length in 3 until maxLazyLength && remaining > length + 1
}

private fun deflateHash(data: ByteArray, index: Int, shift1: Int, shift2: Int, mask: Int): Int {
    return ((data[index].toInt() and 0xFF) xor
            ((data[index + 1].toInt() and 0xFF) shl shift1) xor
            ((data[index + 2].toInt() and 0xFF) shl shift2)) and mask
}

private fun findLongestMatch(
    data: ByteArray,
    dataSize: Int,
    index: Int,
    previousIndex: Int,
    previous: ShortArray,
    level: DeflateLevel,
): Int {
    val remaining = dataSize - index
    var currentIndex = index and MATCH_DISTANCE_MASK
    var candidateIndex = previousIndex
    var distance = (currentIndex - candidateIndex) and MATCH_DISTANCE_MASK
    if (!hasThreeByteMatch(data, index, distance, remaining)) return 0

    val niceLength = minOf(level.niceLength, remaining)
    val maxDistance = minOf(MATCH_DISTANCE_MASK, index)
    val maxLength = minOf(MAX_MATCH_LENGTH, remaining)
    var remainingChain = level.chainLength
    var bestLength = 2
    var bestDistance = 0

    while (distance <= maxDistance && --remainingChain != 0 && currentIndex != candidateIndex) {
        if (data[index + bestLength] == data[index + bestLength - distance] &&
            data[index] == data[index - distance] &&
            data[index + 1] == data[index + 1 - distance]
        ) {
            var candidateLength = 2
            while (
                candidateLength < maxLength &&
                data[index + candidateLength] == data[index + candidateLength - distance]
            ) {
                candidateLength++
            }
            if (candidateLength > bestLength) {
                bestLength = candidateLength
                bestDistance = distance
                if (candidateLength >= niceLength) break

                val matchSpan = minOf(distance, candidateLength - 2)
                var largestPreviousDistance = 0
                for (offset in 0 until matchSpan) {
                    val matchIndex = (index - distance + offset) and MATCH_DISTANCE_MASK
                    val previousMatchIndex = previous[matchIndex].toInt() and 0xFFFF
                    val previousDistance = (matchIndex - previousMatchIndex) and MATCH_DISTANCE_MASK
                    if (previousDistance > largestPreviousDistance) {
                        largestPreviousDistance = previousDistance
                        candidateIndex = matchIndex
                        if (largestPreviousDistance >= maxDistance) break
                    }
                }
            }
        }
        currentIndex = candidateIndex
        candidateIndex = previous[currentIndex].toInt() and 0xFFFF
        distance += (currentIndex - candidateIndex) and MATCH_DISTANCE_MASK
    }

    // The extra distance bits make a far three-byte match costlier than three literals in most blocks.
    if (bestLength == 3 && bestDistance > MAX_DISTANCE_FOR_THREE_BYTE_MATCH) return 0
    return (bestLength shl MATCH_DISTANCE_BITS) or bestDistance
}

internal const val MATCH_DISTANCE_BITS = 15
internal const val MATCH_DISTANCE_MASK = 32767
private const val MAX_MATCH_LENGTH = 258
private const val MAX_DISTANCE_FOR_THREE_BYTE_MATCH = 4096
