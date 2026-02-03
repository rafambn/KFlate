@file:OptIn(ExperimentalTime::class)

package com.rafambn.kflate.algorithm

import com.rafambn.kflate.CompressionType
import com.rafambn.kflate.RAW
import com.rafambn.kflate.GZIP
import com.rafambn.kflate.ZLIB
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.huffman.FIXED_DISTANCE_BASE
import com.rafambn.kflate.huffman.FIXED_DISTANCE_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_MAP
import com.rafambn.kflate.huffman.FIXED_DISTANCE_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_BASE
import com.rafambn.kflate.huffman.FIXED_LENGTH_EXTRA_BITS
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_LOOKUP
import com.rafambn.kflate.huffman.FIXED_LENGTH_REVERSE_MAP
import com.rafambn.kflate.huffman.DEFLATE_OPTIONS
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
import kotlin.time.ExperimentalTime

internal fun inflate(
    inputData: ByteArray,
    inflateState: InflateState,
    outputBuffer: ByteArray? = null,
    dictionary: ByteArray? = null
): ByteArray {
    val sourceLength = inputData.size
    val dictionaryLength = dictionary?.size ?: 0

    if (sourceLength == 0 || (inflateState.isFinalBlock && inflateState.literalMap == null)) {
        return outputBuffer ?: ByteArray(0)
    }

    var workingBuffer = outputBuffer
    val isBufferProvided = workingBuffer != null

    val needsResize = !isBufferProvided || inflateState.validationMode != 2
    val hasNoStoredState = inflateState.validationMode != 0

    if (!isBufferProvided)
        workingBuffer = ByteArray(sourceLength * 3)

    val ensureBufferCapacity = { requiredSize: Int ->
        val currentSize = workingBuffer!!.size
        if (requiredSize > currentSize) {
            val newSize = maxOf(currentSize * 2, requiredSize)
            val newBuffer = ByteArray(newSize)
            workingBuffer!!.copyInto(newBuffer)
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
            isFinalBlock = readBits(inputData, currentBitPosition, 1) != 0
            val blockType = readBits(inputData, currentBitPosition + 1, 3)
            currentBitPosition += 3

            when (blockType) {
                0 -> {
                    val blockStartByte = shiftToNextByte(currentBitPosition)

                    // Check if at least 4 bytes remain for LEN and NLEN
                    if (blockStartByte + 4 > sourceLength) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val blockLength = readTwoBytes(inputData, blockStartByte)
                    val blockNlen = readTwoBytes(inputData, blockStartByte + 2)

                    // Validate that NLEN is the one's complement of LEN
                    if ((blockLength xor 0xFFFF) != blockNlen) {
                        createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                    }

                    val dataStartByte = blockStartByte + 4
                    val blockEndByte = dataStartByte + blockLength

                    if (blockEndByte > sourceLength) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    if (needsResize) ensureBufferCapacity(bytesWrittenToOutput + blockLength)

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
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val numLiteralCodes = readBits(inputData, currentBitPosition, 31) + 257
                    val numDistanceCodes = readBits(inputData, currentBitPosition + 5, 31) + 1
                    val numCodeLengthCodes = readBits(inputData, currentBitPosition + 10, 15) + 4

                    // RFC 1951: HLIT max is 29 (286 codes), HDIST max is 31 (32 codes)
                    // Distance codes 30-31 are never used in valid data but may appear in the tree
                    if (numLiteralCodes > 286 || numDistanceCodes > 32) {
                        createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                    }

                    val totalCodes = numLiteralCodes + numDistanceCodes
                    currentBitPosition += 14

                    // Check if we have enough bits for the code length tree
                    val codeLengthTreeBits = numCodeLengthCodes * 3
                    if (currentBitPosition + codeLengthTreeBits > totalAvailableBits) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val codeLengthTree = ByteArray(19)
                    for (i in 0 until numCodeLengthCodes) {
                        codeLengthTree[CODE_LENGTH_INDEX_MAP[i].toInt()] = readBits(inputData, currentBitPosition + i * 3, 7).toByte()
                    }
                    currentBitPosition += numCodeLengthCodes * 3

                    val codeLengthMaxBits = findMaxValue(codeLengthTree)

                    // Validate code-length tree
                    if (codeLengthMaxBits > 0) {
                        if (!validateHuffmanCodeLengths(codeLengthTree, codeLengthMaxBits)) {
                            createFlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                        }
                    }

                    val codeLengthBitMask = (1 shl codeLengthMaxBits) - 1
                    val codeLengthHuffmanMap = createHuffmanTree(codeLengthTree, codeLengthMaxBits, true)

                    val allCodeLengths = ByteArray(totalCodes)
                    var codeIndex = 0

                    while (codeIndex < totalCodes) {
                        if (currentBitPosition > totalAvailableBits) {
                            if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                            break
                        }

                        val huffmanCode = codeLengthHuffmanMap[readBits(inputData, currentBitPosition, codeLengthBitMask)]
                        currentBitPosition += (huffmanCode.toInt() and 15)
                        val symbol = huffmanCode.toInt() shr 4

                        when {
                            symbol < 16 -> {
                                allCodeLengths[codeIndex++] = symbol.toByte()
                            }

                            symbol == 16 -> {
                                if (codeIndex == 0) {
                                    createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                val repeatCount = 3 + readBits(inputData, currentBitPosition, 3)
                                currentBitPosition += 2
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                val valueToRepeat = allCodeLengths[codeIndex - 1]
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = valueToRepeat }
                            }

                            symbol == 17 -> {
                                val repeatCount = 3 + readBits(inputData, currentBitPosition, 7)
                                currentBitPosition += 3
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = 0 }
                            }

                            symbol == 18 -> {
                                val repeatCount = 11 + readBits(inputData, currentBitPosition, 127)
                                currentBitPosition += 7
                                val remainingSlots = totalCodes - codeIndex
                                if (repeatCount > remainingSlots) {
                                    createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
                                }
                                repeat(repeatCount) { allCodeLengths[codeIndex++] = 0 }
                            }
                        }
                    }

                    if (currentBitPosition > totalAvailableBits) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    if (codeIndex < totalCodes) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    val literalLengthCodeLengths = allCodeLengths.copyOfRange(0, numLiteralCodes)
                    val distanceCodeLengths = allCodeLengths.copyOfRange(numLiteralCodes, totalCodes)

                    // Validate that end-of-block symbol (256) has a non-zero code length
                    if (numLiteralCodes > 256 && literalLengthCodeLengths[256].toInt() == 0) {
                        createFlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                    }

                    literalMaxBits = findMaxValue(literalLengthCodeLengths)
                    distanceMaxBits = findMaxValue(distanceCodeLengths)

                    // Validate literal/length tree
                    if (literalMaxBits > 0) {
                        if (!validateHuffmanCodeLengths(literalLengthCodeLengths, literalMaxBits)) {
                            createFlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                        }
                    }

                    literalLengthMap = createHuffmanTree(literalLengthCodeLengths, literalMaxBits, true)

                    // Validate distance tree
                    if (distanceMaxBits > 0) {
                        if (!validateHuffmanCodeLengths(distanceCodeLengths, distanceMaxBits)) {
                            createFlateError(FlateErrorCode.INVALID_HUFFMAN_TREE)
                        }
                    }

                    distanceMap = createHuffmanTree(distanceCodeLengths, distanceMaxBits, true)
                }

                else -> createFlateError(FlateErrorCode.INVALID_BLOCK_TYPE)
            }

            if (currentBitPosition > totalAvailableBits) {
                if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }
        }

        if (needsResize) ensureBufferCapacity(bytesWrittenToOutput + 131072)

        val literalBitMask = (1 shl literalMaxBits) - 1
        val distanceBitMask = (1 shl distanceMaxBits) - 1
        var lastBitPosition = currentBitPosition

        while (true) {
            val literalCode = (literalLengthMap!![readBits16(inputData, currentBitPosition) and literalBitMask].toInt() and 0xFFFF)
            val symbol = literalCode shr 4
            currentBitPosition += (literalCode and 15)

            if (currentBitPosition > totalAvailableBits) {
                if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                break
            }

            if (literalCode == 0) createFlateError(FlateErrorCode.INVALID_LENGTH_LITERAL)

            when {
                symbol < 256 -> {
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
                        matchLength =
                            readBits(inputData, currentBitPosition, (1 shl extraBits) - 1) + (FIXED_LENGTH_BASE[lengthIndex].toInt() and 0xFFFF)
                        currentBitPosition += extraBits
                    }

                    val distanceCode = (distanceMap!![readBits16(inputData, currentBitPosition) and distanceBitMask].toInt() and 0xFFFF)
                    val distanceSymbol = distanceCode shr 4
                    if (distanceCode == 0) createFlateError(FlateErrorCode.INVALID_DISTANCE)
                    // RFC 1951: Distance codes 30-31 will never occur in valid compressed data
                    if (distanceSymbol >= 30) createFlateError(FlateErrorCode.INVALID_DISTANCE)
                    currentBitPosition += (distanceCode and 15)

                    var matchDistance = FIXED_DISTANCE_BASE[distanceSymbol].toInt() and 0xFFFF
                    if (distanceSymbol > 3) {
                        val extraBits = FIXED_DISTANCE_EXTRA_BITS[distanceSymbol].toInt() and 0xFF
                        matchDistance += readBits16(inputData, currentBitPosition) and ((1 shl extraBits) - 1)
                        currentBitPosition += extraBits
                    }

                    if (currentBitPosition > totalAvailableBits) {
                        if (hasNoStoredState) createFlateError(FlateErrorCode.UNEXPECTED_EOF)
                        break
                    }

                    if (needsResize) ensureBufferCapacity(bytesWrittenToOutput + 131072)

                    val copyEndIndex = bytesWrittenToOutput + matchLength

                    if (bytesWrittenToOutput < matchDistance) {
                        val dictionaryOffset = dictionaryLength - matchDistance
                        val dictionaryEndIndex = minOf(matchDistance, copyEndIndex)
                        if (dictionaryOffset + bytesWrittenToOutput < 0) {
                            createFlateError(FlateErrorCode.INVALID_DISTANCE)
                        }

                        while (bytesWrittenToOutput < dictionaryEndIndex) {
                            workingBuffer[bytesWrittenToOutput] = dictionary!![dictionaryOffset + bytesWrittenToOutput]
                            bytesWrittenToOutput++
                        }
                    }

                    while (bytesWrittenToOutput < copyEndIndex) {
                        workingBuffer[bytesWrittenToOutput] = workingBuffer[bytesWrittenToOutput - matchDistance]
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
    val bufferMargin = (dataSize shr 3) + 256 + 5 * (1 + (dataSize / 7000))
    val output = ByteArray(prefixSize + dataSize + bufferMargin + postfixSize)
    val writeBuffer = ByteArray(output.size - prefixSize - postfixSize)
    val isLastBlock = state.isLastChunk
    var bitPosition = state.bitBuffer and 7

    if (level > 0) {
        if (bitPosition != 0) {
            writeBuffer[0] = (state.bitBuffer shr 3).toByte()
        }
        val option = DEFLATE_OPTIONS[level - 1]
        val niceLength = option shr 13
        val chainLength = option and 8191
        val mask = (1 shl compressionLevel) - 1
        val prev = state.prev ?: ShortArray(32768)
        val head = state.head ?: ShortArray(mask + 1)
        val baseShift1 = ceil(compressionLevel / 3.0).toInt()
        val baseShift2 = 2 * baseShift1
        val hash = { i: Int -> ((data[i].toInt() and 0xFF) xor ((data[i + 1].toInt() and 0xFF) shl baseShift1) xor ((data[i + 2].toInt() and 0xFF) shl baseShift2)) and mask }

        val symbols = IntArray(65536)
        val literalFrequencies = IntArray(288)
        val distanceFrequencies = IntArray(32)
        var literalCount = 0
        var extraBits = 0
        var i = state.inputOffset
        var symbolIndex = 0
        var waitIndex = state.waitIndex
        var blockStart = maxOf(state.inputOffset, waitIndex)

        while (i + 2 < dataSize) {
            val hashValue = hash(i)
            var iMod = i and 32767
            var pIMod = head[hashValue].toInt() and 0xFFFF
            prev[iMod] = pIMod.toShort()
            head[hashValue] = iMod.toShort()

            if (waitIndex <= i) {
                val remaining = dataSize - i
                if ((literalCount > 7000 || symbolIndex > 24576) && (remaining > 423 || !isLastBlock)) {
                    bitPosition = writeBlock(
                        data, writeBuffer, false, symbols, literalFrequencies, distanceFrequencies,
                        extraBits, symbolIndex, blockStart, i - blockStart, bitPosition
                    )
                    symbolIndex = 0
                    literalCount = 0
                    extraBits = 0
                    blockStart = i
                    literalFrequencies.fill(0, 0, 286)
                    distanceFrequencies.fill(0, 0, 30)
                }

                var length = 2
                var distance = 0
                var currentChain = chainLength
                var diff = (iMod - pIMod) and 32767

                if (remaining > 2 && hashValue == hash(i - diff)) {
                    val maxN = minOf(niceLength, remaining) - 1
                    val maxD = minOf(32767, i)
                    val maxLength = minOf(258, remaining)

                    while (diff <= maxD && --currentChain != 0 && iMod != pIMod) {
                        if ((data[i + length].toInt() and 0xFF) == (data[i + length - diff].toInt() and 0xFF)) {
                            var newLength = 0
                            while (newLength < maxLength && (data[i + newLength].toInt() and 0xFF) == (data[i + newLength - diff].toInt() and 0xFF)) {
                                newLength++
                            }
                            if (newLength > length) {
                                length = newLength
                                distance = diff
                                if (newLength > maxN) break

                                val minMatchDiff = minOf(diff, newLength - 2)
                                var maxDiff = 0
                                for (j in 0 until minMatchDiff) {
                                    val tI = (i - diff + j) and 32767
                                    val pTI = prev[tI].toInt() and 0xFFFF
                                    val cD = (tI - pTI) and 32767
                                    if (cD > maxDiff) {
                                        maxDiff = cD
                                        pIMod = tI
                                    }
                                }
                            }
                        }
                        iMod = pIMod
                        pIMod = prev[iMod].toInt() and 0xFFFF
                        diff += (iMod - pIMod) and 32767
                    }
                }

                if (distance != 0) {
                    symbols[symbolIndex++] = 268435456 or (FIXED_LENGTH_REVERSE_LOOKUP[length] shl 18) or FIXED_DISTANCE_REVERSE_LOOKUP[distance]
                    val lenIndex = FIXED_LENGTH_REVERSE_LOOKUP[length] and 31
                    val distIndex = FIXED_DISTANCE_REVERSE_LOOKUP[distance] and 31
                    extraBits += (FIXED_LENGTH_EXTRA_BITS[lenIndex].toInt() and 0xFF) + (FIXED_DISTANCE_EXTRA_BITS[distIndex].toInt() and 0xFF)
                    ++literalFrequencies[257 + lenIndex]
                    ++distanceFrequencies[distIndex]
                    waitIndex = i + length
                    ++literalCount
                } else {
                    symbols[symbolIndex++] = data[i].toInt() and 0xFF
                    ++literalFrequencies[data[i].toInt() and 0xFF]
                }
            }
            i++
        }

        i = maxOf(i, waitIndex)
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
            state.bitBuffer = (bitPosition and 7) or ((writeBuffer[bitPosition / 8].toInt() and 0xFF) shl 3)
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
                writeBuffer[bitPosition / 8] = lastBlockFlag.toByte()
                end = dataSize
            }
            bitPosition = writeFixedBlock(writeBuffer, bitPosition + 1, data.sliceArray(i until end))
            i += 65535
        }
        state.inputOffset = dataSize
    }
    writeBuffer.copyInto(output, destinationOffset = prefixSize)
    return output.sliceArray(0 until prefixSize + shiftToNextByte(bitPosition) + postfixSize)
}

internal fun deflateWithOptions(
    inputData: ByteArray,
    type: CompressionType = RAW(),
    prefixSize: Int,
    suffixSize: Int,
    deflateState: DeflateState? = null
): ByteArray {
    var workingState = deflateState
    var workingData = inputData

    val level = when (type) {
        is RAW -> type.level
        is GZIP -> type.level
        is ZLIB -> type.level
    }
    val bufferSize = when (type) {
        is RAW -> type.bufferSize
        is GZIP -> type.bufferSize
        is ZLIB -> type.bufferSize
    }
    val dictionary = when (type) {
        is RAW -> type.dictionary
        is GZIP -> type.dictionary
        is ZLIB -> type.dictionary
    }

    if (workingState == null) {
        workingState = DeflateState(isLastChunk = true)

        if (dictionary != null) {
            val combinedData = ByteArray(dictionary.size + inputData.size)

            dictionary.copyInto(combinedData, destinationOffset = 0)

            inputData.copyInto(combinedData, destinationOffset = dictionary.size)

            workingData = combinedData
            workingState.waitIndex = dictionary.size
        }
    }

        val compressionLevel = level

        val memoryUsage = if (workingState.isLastChunk && bufferSize == 4096) {
            ceil(max(8.0, min(13.0, ln(workingData.size.toDouble()))) * 1.5).toInt()
        } else {
            var bits = 0
            var valTemp = bufferSize - 1
            while (valTemp > 0) {
                valTemp = valTemp shr 1
                bits++
            }
            bits
        }

    return deflate(
        workingData,
        compressionLevel,
        memoryUsage,
        prefixSize,
        suffixSize,
        workingState
    )
}
