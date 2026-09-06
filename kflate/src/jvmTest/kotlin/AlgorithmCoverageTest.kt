package com.rafambn.kflate

import com.rafambn.kflate.algorithm.deflate
import com.rafambn.kflate.algorithm.deflateWithOptions
import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.algorithm.checkedDeflateInputSize
import com.rafambn.kflate.algorithm.hasThreeByteMatch
import com.rafambn.kflate.algorithm.shouldFlushBlock
import com.rafambn.kflate.algorithm.validateCodeLengthEntry
import com.rafambn.kflate.algorithm.validateCodeLengthTree
import com.rafambn.kflate.algorithm.validateInflateInputSize
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.huffman.buildHuffmanTreeFromFrequencies
import com.rafambn.kflate.huffman.generateLengthCodes
import com.rafambn.kflate.huffman.validateHuffmanCodeLengths
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.InflateState
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AlgorithmCoverageTest {

    @Test
    fun inflateCoversStoredStateAndSizeBoundaries() {
        val finished = InflateState(isFinalBlock = true)
        assertContentEquals(byteArrayOf(), inflate(byteArrayOf(1), finished))
        assertContentEquals(byteArrayOf(), inflate(byteArrayOf(), InflateState()))
        assertFailsWith<FlateError> {
            inflate(byteArrayOf(), InflateState(validationMode = 2))
        }

        val incompleteHeader = InflateState(inputBitPosition = 7)
        assertContentEquals(byteArrayOf(), inflate(byteArrayOf(0), incompleteHeader))
        assertFailsWith<FlateError> {
            inflate(byteArrayOf(0), InflateState(inputBitPosition = 7, validationMode = 2))
        }

        val incompleteStoredHeader = byteArrayOf(1)
        assertContentEquals(byteArrayOf(), inflate(incompleteStoredHeader, InflateState()))
        assertFailsWith<FlateError> {
            inflate(incompleteStoredHeader, InflateState(validationMode = 2))
        }

        val incompleteStoredData = byteArrayOf(1, 2, 0, -3, -1, 42)
        assertContentEquals(byteArrayOf(), inflate(incompleteStoredData, InflateState()))
        assertFailsWith<FlateError> {
            inflate(incompleteStoredData, InflateState(validationMode = 2))
        }

        val oneStoredByte = byteArrayOf(1, 1, 0, -2, -1, 42)
        assertContentEquals(byteArrayOf(42), inflate(oneStoredByte, InflateState(validationMode = 2)))
        val overflowState = InflateState(outputOffset = Int.MAX_VALUE)
        val overflow = assertFailsWith<FlateError> {
            inflate(oneStoredByte, overflowState)
        }
        assertEquals(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED, overflow.code)
    }

    @Test
    fun malformedDeflateInputsFailWithDomainErrors() {
        for (value in 0..255) {
            exerciseMalformed(byteArrayOf(value.toByte()))
        }

        var random = 0x6d2b79f5
        repeat(20_000) {
            random = random xor (random shl 13)
            random = random xor (random ushr 17)
            random = random xor (random shl 5)
            val size = 2 + (random ushr 1) % 47
            val data = ByteArray(size)
            for (index in data.indices) {
                random = random xor (random shl 13)
                random = random xor (random ushr 17)
                random = random xor (random shl 5)
                data[index] = random.toByte()
            }
            exerciseMalformed(data)
        }
    }

    @Test
    fun validDeflatePrefixesExerciseResumableInflate() {
        val inputs = listOf(
            ByteArray(4_096),
            ByteArray(4_096) { it.toByte() },
            "abcdefghij".repeat(1_000).encodeToByteArray(),
        )

        for (input in inputs) {
            val compressed = deflateWithJava(input)
            assertContentEquals(input, inflate(compressed, InflateState(validationMode = 2)))
            for (end in 1..compressed.size) {
                try {
                    inflate(compressed.copyOf(end), InflateState(), maxOutputSize = input.size)
                } catch (_: FlateError) {
                    // A prefix can already prove corruption, while most prefixes preserve resumable state.
                }
            }

            for (index in 0 until minOf(compressed.size, 96)) {
                for (value in 0..255) {
                    val mutated = compressed.copyOf()
                    mutated[index] = value.toByte()
                    exerciseMalformed(mutated)
                }
            }
        }
    }

    @Test
    fun storedHuffmanStateRejectsIncompleteLiteralAndDistanceCodes() {
        val incompleteLiteral = InflateState(
            literalMap = ShortArray(4),
            distanceMap = ShortArray(2),
            literalMaxBits = 2,
            distanceMaxBits = 1,
            inputBitPosition = 7,
            validationMode = 2,
        )
        assertEquals(
            FlateErrorCode.UNEXPECTED_EOF,
            assertFailsWith<FlateError> { inflate(byteArrayOf(0), incompleteLiteral) }.code,
        )
        assertContentEquals(
            byteArrayOf(),
            inflate(byteArrayOf(0), incompleteLiteral.copy(validationMode = 0)),
        )
        assertEquals(
            FlateErrorCode.INVALID_LENGTH_LITERAL,
            assertFailsWith<FlateError> {
                inflate(
                    byteArrayOf(0),
                    incompleteLiteral.copy(inputBitPosition = 0, literalMaxBits = 1),
                )
            }.code,
        )

        val lengthCode = ((257 shl 4) or 1).toShort()
        val incompleteDistance = InflateState(
            literalMap = shortArrayOf(lengthCode, lengthCode),
            distanceMap = ShortArray(256),
            literalMaxBits = 1,
            distanceMaxBits = 8,
            validationMode = 2,
        )
        assertEquals(
            FlateErrorCode.UNEXPECTED_EOF,
            assertFailsWith<FlateError> { inflate(byteArrayOf(0), incompleteDistance) }.code,
        )
        assertContentEquals(
            byteArrayOf(),
            inflate(byteArrayOf(0), incompleteDistance.copy(validationMode = 0)),
        )

        val invalidDistance = InflateState(
            literalMap = shortArrayOf(lengthCode, lengthCode),
            distanceMap = ShortArray(2),
            literalMaxBits = 1,
            distanceMaxBits = 1,
            validationMode = 2,
        )
        assertEquals(
            FlateErrorCode.INVALID_DISTANCE,
            assertFailsWith<FlateError> { inflate(byteArrayOf(0), invalidDistance) }.code,
        )
    }

    @Test
    fun deflateCoversExplicitStateAndOverflowChecks() {
        assertTrue(deflateWithOptions(byteArrayOf(1), prefixSize = 0, suffixSize = 0).isNotEmpty())

        val boundedState = DeflateState(inputEndIndex = 1, isLastChunk = true)
        assertTrue(deflate(byteArrayOf(1, 2), 0, 12, 0, 0, boundedState).isNotEmpty())

        val inputTooLarge = assertFailsWith<FlateError> {
            deflate(byteArrayOf(), 0, 12, 0, 0, DeflateState(inputEndIndex = Int.MAX_VALUE))
        }
        assertEquals(FlateErrorCode.INPUT_TOO_LARGE, inputTooLarge.code)

        val outputTooLarge = assertFailsWith<FlateError> {
            deflate(byteArrayOf(), 0, 12, Int.MAX_VALUE, 1, DeflateState(isLastChunk = true))
        }
        assertEquals(FlateErrorCode.INPUT_TOO_LARGE, outputTooLarge.code)

        assertTrue(deflate(byteArrayOf(), 0, 12, 0, 0, DeflateState()).isEmpty())
        validateInflateInputSize((Int.MAX_VALUE - 64) / 8)
        assertFailsWith<FlateError> {
            validateInflateInputSize((Int.MAX_VALUE - 64) / 8 + 1)
        }
        assertEquals(12, checkedDeflateInputSize(5, 7))
        assertFailsWith<FlateError> { checkedDeflateInputSize(Int.MAX_VALUE, 1) }
        assertTrue(deflate(ByteArray(65_536), 0, 12, 0, 0, DeflateState(isLastChunk = true)).isNotEmpty())

        assertTrue(shouldFlushBlock(7_001, 0, 424, true))
        assertTrue(shouldFlushBlock(0, 24_577, 0, false))
        assertTrue(!shouldFlushBlock(7_000, 24_576, 424, false))
        assertTrue(!shouldFlushBlock(7_001, 0, 423, true))

        val matchData = byteArrayOf(1, 2, 3, 1, 2, 3)
        assertTrue(hasThreeByteMatch(matchData, 3, 3, 3))
        assertTrue(!hasThreeByteMatch(matchData, 3, 3, 2))
        assertTrue(!hasThreeByteMatch(byteArrayOf(1, 2, 3, 0, 2, 3), 3, 3, 3))
        assertTrue(!hasThreeByteMatch(byteArrayOf(1, 2, 3, 1, 0, 3), 3, 3, 3))
        assertTrue(!hasThreeByteMatch(byteArrayOf(1, 2, 3, 1, 2, 0), 3, 3, 3))
    }

    @Test
    fun huffmanConstructionCoversEmptyAndDepthLimitedTrees() {
        assertTrue(validateHuffmanCodeLengths(byteArrayOf(2, 2, 2, 2), 3))
        assertEquals(0, buildHuffmanTreeFromFrequencies(IntArray(4), 3).maxBits)
        assertEquals(1, buildHuffmanTreeFromFrequencies(intArrayOf(0, 7), 3).maxBits)

        val limited = buildHuffmanTreeFromFrequencies(
            intArrayOf(1, 1, 2, 3, 5, 8, 13, 21),
            3,
        )
        assertEquals(3, limited.maxBits)
        assertEquals(8, limited.tree.size)

        buildHuffmanTreeFromFrequencies(intArrayOf(21, 13, 8, 5, 3, 2, 1, 1), 3)
        buildHuffmanTreeFromFrequencies(intArrayOf(1, 100, 100, 100, 100, 100, 100, 100), 3)

        var random = 0x1234abcd
        repeat(10_000) {
            val frequencies = IntArray(8) {
                random = random * 1_103_515_245 + 12_345
                (random ushr 1) % 1_000 + 1
            }
            buildHuffmanTreeFromFrequencies(frequencies, 3)
        }

        val (emptyCodes, maxSymbol) = generateLengthCodes(ByteArray(3))
        assertTrue(emptyCodes.isEmpty())
        assertEquals(0, maxSymbol)
    }

    @Test
    fun codeLengthEntryValidationDistinguishesTruncationFromInvalidTrees() {
        validateCodeLengthEntry(1, 1, 1)
        assertEquals(
            FlateErrorCode.UNEXPECTED_EOF,
            assertFailsWith<FlateError> { validateCodeLengthEntry(2, 1, 2) }.code,
        )
        assertEquals(
            FlateErrorCode.UNEXPECTED_EOF,
            assertFailsWith<FlateError> { validateCodeLengthEntry(0, 1, 2) }.code,
        )
        assertEquals(
            FlateErrorCode.INVALID_HUFFMAN_TREE,
            assertFailsWith<FlateError> { validateCodeLengthEntry(0, 2, 2) }.code,
        )

        validateCodeLengthTree(byteArrayOf(1, 1), 1)
        assertEquals(
            FlateErrorCode.INVALID_HUFFMAN_TREE,
            assertFailsWith<FlateError> { validateCodeLengthTree(ByteArray(19), 0) }.code,
        )
        assertEquals(
            FlateErrorCode.INVALID_HUFFMAN_TREE,
            assertFailsWith<FlateError> { validateCodeLengthTree(byteArrayOf(1, 1, 1), 1) }.code,
        )
    }

    private fun exerciseMalformed(data: ByteArray) {
        for (validationMode in 0..2 step 2) {
            try {
                inflate(
                    data,
                    InflateState(validationMode = validationMode),
                    maxOutputSize = 64,
                )
            } catch (_: FlateError) {
                // Malformed compressed bytes are expected to fail with a domain error.
            }
        }
    }

    private fun deflateWithJava(input: ByteArray): ByteArray {
        val deflater = Deflater(6, true)
        return try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArray(input.size + 512)
            output.copyOf(deflater.deflate(output))
        } finally {
            deflater.end()
        }
    }
}
