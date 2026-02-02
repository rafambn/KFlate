
package com.rafambn.kflate

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class HuffmanTreeValidationTest {

    /**
     * Test oversubscribed tree detection.
     * 3 codes at length 2 + 5 codes at length 3
     * Space: 8 units (2^3), Used: 3*2 + 5*1 = 11 > 8 = OVERSUBSCRIBED
     */
    @Test
    fun testValidateOversubscribedTree() {
        val codeLengths = byteArrayOf(2, 2, 2, 3, 3, 3, 3, 3)
        val result = validateHuffmanCodeLengths(codeLengths, 3)
        assert(!result) { "Should detect oversubscribed tree" }
    }

    /**
     * Test incomplete tree detection.
     * Only 2 codes at length 3
     * Space: 8 units (2^3), Used: 2*1 = 2 < 8 = INCOMPLETE
     */
    @Test
    fun testValidateIncompleteTree() {
        val codeLengths = byteArrayOf(3, 3)
        val result = validateHuffmanCodeLengths(codeLengths, 3)
        assert(!result) { "Should detect incomplete tree" }
    }

    /**
     * Test complete valid tree acceptance.
     * 2 codes at length 2 + 4 codes at length 3
     * Space: 8 units (2^3), Used: 2*2 + 4*1 = 8 = COMPLETE
     */
    @Test
    fun testValidateCompleteTree() {
        val codeLengths = byteArrayOf(2, 2, 3, 3, 3, 3)
        val result = validateHuffmanCodeLengths(codeLengths, 3)
        assert(result) { "Should accept complete tree" }
    }

    /**
     * Test single symbol tree acceptance (RFC 1951 special case).
     * A single symbol is allowed even though technically incomplete.
     */
    @Test
    fun testValidateSingleSymbolTree() {
        val codeLengths = byteArrayOf(1)
        val result = validateHuffmanCodeLengths(codeLengths, 1)
        assert(result) { "Should accept single symbol (RFC 1951 special case)" }
    }

    /**
     * Test single symbol with maxBits=2.
     */
    @Test
    fun testValidateSingleSymbolWithHigherMaxBits() {
        val codeLengths = byteArrayOf(2)
        val result = validateHuffmanCodeLengths(codeLengths, 2)
        assert(result) { "Should accept single symbol regardless of maxBits" }
    }

    /**
     * Test empty tree acceptance (all zeros).
     */
    @Test
    fun testValidateEmptyTree() {
        val codeLengths = byteArrayOf(0, 0, 0, 0)
        val result = validateHuffmanCodeLengths(codeLengths, 2)
        assert(result) { "Should accept empty tree" }
    }

    /**
     * Test maxBits=0 edge case.
     */
    @Test
    fun testValidateWithMaxBitsZero() {
        val codeLengths = byteArrayOf(1)
        val result = validateHuffmanCodeLengths(codeLengths, 0)
        assert(result) { "Should skip validation for maxBits <= 0" }
    }

    /**
     * Test invalid code length (exceeds maxBits).
     */
    @Test
    fun testValidateCodeLengthExceedsMaxBits() {
        val codeLengths = byteArrayOf(2, 5)  // 5 > maxBits(3)
        val result = validateHuffmanCodeLengths(codeLengths, 3)
        assert(!result) { "Should reject code length exceeding maxBits" }
    }

    /**
     * Test tree with single code at maximum length.
     * 1 code at length 15 uses 2^(15-15) = 1 unit, leaving 2^15-1 units empty = INCOMPLETE
     * But RFC allows single symbol, so this should be accepted.
     */
    @Test
    fun testValidateSingleCodeAtMaxLength() {
        val codeLengths = byteArrayOf(15)
        val result = validateHuffmanCodeLengths(codeLengths, 15)
        assert(result) { "Should accept single symbol at any length" }
    }

    /**
     * Test two symbols: 1 code at length 1 + 1 code at length 2.
     * Space: 4 units (2^2), Used: 1*2 + 1*1 = 3 < 4 = INCOMPLETE
     */
    @Test
    fun testValidateTwoSymbolsIncomplete() {
        val codeLengths = byteArrayOf(1, 2)
        val result = validateHuffmanCodeLengths(codeLengths, 2)
        assert(!result) { "Should detect incomplete tree with two symbols" }
    }

    /**
     * Test two symbols: 1 code at length 1 + 1 code at length 1.
     * Space: 2 units (2^1), Used: 1*1 + 1*1 = 2 = COMPLETE
     */
    @Test
    fun testValidateTwoSymbolsComplete() {
        val codeLengths = byteArrayOf(1, 1)
        val result = validateHuffmanCodeLengths(codeLengths, 1)
        assert(result) { "Should accept complete two-symbol tree" }
    }

    /**
     * Test realistic literal/length alphabet with many symbols.
     * Create a distribution where all space is used.
     * For maxBits=9 (512 units): 32 codes at length 5 exactly fill space (32 * 16 = 512)
     */
    @Test
    fun testValidateRealisticLiteralLengthTree() {
        // Simplified realistic distribution: 32 codes at length 5
        // This exactly fills 2^9 = 512 units (32 * 2^(9-5) = 32 * 16 = 512)
        val codeLengths = ByteArray(32)
        for (i in 0 until 32) {
            codeLengths[i] = 5
        }
        val result = validateHuffmanCodeLengths(codeLengths, 9)
        assert(result) { "Should validate realistic literal/length tree" }
    }

    /**
     * Integration test: Valid DEFLATE stream from Java's Deflater still works.
     * This is a regression test to ensure our validation doesn't break valid streams.
     */
    @Test
    fun testValidRegressionWithJavaDeflater() {
        val originalData = "The quick brown fox jumps over the lazy dog".repeat(5)
        val deflater = Deflater(6, true)
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalData.encodeToByteArray())
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalData.encodeToByteArray())) {
            "Valid DEFLATE from Java Deflater should still decompress"
        }
        deflater.end()
    }

    /**
     * Integration test: Small data with dynamic Huffman.
     * Ensures validation doesn't break small dynamic blocks.
     */
    @Test
    fun testValidSmallDynamicHuffmanBlock() {
        val originalData = "abc"
        val deflater = Deflater(6, true)
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalData.encodeToByteArray())
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalData.encodeToByteArray())) {
            "Small data with dynamic Huffman should decompress correctly"
        }
        deflater.end()
    }

    /**
     * Integration test: Large diverse data with dynamic Huffman.
     * Exercises full range of literal and distance codes.
     */
    @Test
    fun testValidLargeDiverseData() {
        val originalData = ByteArray(10000) { (it xor (it shr 3) xor (it shr 5)).toByte() }
        val deflater = Deflater(9, true)
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalData)
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Large diverse data should decompress correctly"
        }
        deflater.end()
    }

    /**
     * Integration test: Data with repeating patterns.
     * Highly compressible data exercises distance codes.
     */
    @Test
    fun testValidRepeatingPatternData() {
        val pattern = "ABCDEFGHIJ".encodeToByteArray()
        val originalData = ByteArray(5000) { pattern[it % pattern.size] }
        val deflater = Deflater(9, true)
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalData)
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Repeating pattern should decompress correctly"
        }
        deflater.end()
    }

    /**
     * Integration test: Full byte range (0-255) repeated.
     * Ensures validation works with complete alphabet.
     */
    @Test
    fun testValidFullByteRange() {
        val originalData = ByteArray(256) { it.toByte() }
        val originalDataRepeated = ByteArray(256 * 100) { originalData[it % 256] }

        val deflater = Deflater(6, true)
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalDataRepeated)
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalDataRepeated)) {
            "Full byte range should decompress correctly"
        }
        deflater.end()
    }

    /**
     * Integration test: Multiple different compression levels.
     * Ensures validation works with various tree configurations.
     */
    @Test
    fun testValidMultipleCompressionLevels() {
        val originalData = "The quick brown fox jumps over the lazy dog".repeat(20).encodeToByteArray()

        for (level in 1..9) {
            val deflater = Deflater(level, true)
            val outputStream = ByteArrayOutputStream()
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)

            deflaterStream.write(originalData)
            deflaterStream.finish()
            deflaterStream.close()

            val compressed = outputStream.toByteArray()
            val decompressed = KFlate.decompress(compressed, Raw())

            assert(decompressed.contentEquals(originalData)) {
                "Data compressed at level $level should decompress correctly"
            }
            deflater.end()
        }
    }

    /**
     * Integration test: Verify tree validation doesn't skip fixed Huffman blocks.
     * Fixed blocks should still work.
     */
    @Test
    fun testValidFixedHuffmanBlocks() {
        // Very small data might use fixed Huffman
        val originalData = "hi".encodeToByteArray()
        val deflater = Deflater(0, true)  // Level 0 might use different strategy
        val outputStream = ByteArrayOutputStream()
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(originalData)
        deflaterStream.finish()
        deflaterStream.close()

        val compressed = outputStream.toByteArray()
        val decompressed = KFlate.decompress(compressed, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Fixed Huffman blocks should still decompress correctly"
        }
        deflater.end()
    }

    /**
     * Test edge case: Tree with only a single very long code.
     * Should be accepted as single-symbol special case.
     */
    @Test
    fun testValidateSingleSymbolVeryLongCode() {
        val codeLengths = ByteArray(100)
        codeLengths[50] = 15
        val result = validateHuffmanCodeLengths(codeLengths, 15)
        assert(result) { "Should accept single symbol even with very long code" }
    }

    /**
     * Test tree where code lengths are sparse (many gaps).
     * Should only count actual symbols.
     */
    @Test
    fun testValidateSparseLengthDistribution() {
        val codeLengths = ByteArray(1000)
        codeLengths[0] = 1
        codeLengths[500] = 1
        // Only 2 symbols in 1000-element array
        val result = validateHuffmanCodeLengths(codeLengths, 1)
        assert(result) { "Should correctly count only non-zero lengths" }
    }

    /**
     * Test oversubscribed condition detection at boundaries.
     * 4 codes at length 2 exactly fills space for maxBits=2 (4 units)
     */
    @Test
    fun testValidateBoundaryComplete() {
        val codeLengths = byteArrayOf(2, 2, 2, 2)
        val result = validateHuffmanCodeLengths(codeLengths, 2)
        assert(result) { "4 codes at length 2 should exactly fill 2^2 space" }
    }

    /**
     * Test oversubscribed condition at boundaries.
     * 5 codes at length 2 exceeds space for maxBits=2 (4 units)
     */
    @Test
    fun testValidateBoundaryOversubscribed() {
        val codeLengths = byteArrayOf(2, 2, 2, 2, 2)
        val result = validateHuffmanCodeLengths(codeLengths, 2)
        assert(!result) { "5 codes at length 2 exceeds 2^2 space" }
    }
}
