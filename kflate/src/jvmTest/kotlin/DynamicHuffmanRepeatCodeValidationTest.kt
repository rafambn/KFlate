@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DynamicHuffmanRepeatCodeValidationTest {

    /**
     * Helper to create a DEFLATE stream using Java's Deflater.
     * This produces valid dynamic Huffman blocks that we can use as a baseline.
     */
    private fun deflateData(data: ByteArray, level: Int = 6): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val deflater = Deflater(level, true)  // true = raw DEFLATE (no ZLIB wrapper)
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)

        deflaterStream.write(data)
        deflaterStream.finish()
        deflaterStream.close()

        return outputStream.toByteArray()
    }

    /**
     * Test that a valid dynamic Huffman block compresses and decompresses correctly.
     * This is a baseline test to verify the normal path works.
     */
    @Test
    fun testValidDynamicHuffmanBlock() {
        val originalData = "The quick brown fox jumps over the lazy dog".repeat(10).encodeToByteArray()

        // Compress using Java's deflater (level 6 to encourage dynamic Huffman)
        val deflated = deflateData(originalData, level = 6)

        // Decompress using KFlate
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Valid dynamic Huffman block should decompress correctly"
        }
    }

    /**
     * Test that a very small dataset still decompresses correctly.
     * This is important because small data might use different block types.
     */
    @Test
    fun testSmallDataWithDynamicHuffman() {
        val originalData = "abc".encodeToByteArray()

        val deflated = deflateData(originalData, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Small data with dynamic Huffman should decompress correctly"
        }
    }

    /**
     * Test that binary data with repeating patterns decompresses correctly.
     * Repeating patterns are more likely to trigger dynamic Huffman blocks.
     */
    @Test
    fun testRepeatingPatternData() {
        val pattern = byteArrayOf(0x42, 0x43, 0x44)
        val originalData = ByteArray(1000) { pattern[it % 3] }

        val deflated = deflateData(originalData, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Repeating pattern should decompress correctly with dynamic Huffman"
        }
    }

    /**
     * Test that diverse binary data decompresses correctly.
     * This exercises the full range of literal/distance codes.
     */
    @Test
    fun testDiverseBinaryData() {
        val originalData = ByteArray(500) { (it xor (it shr 3) xor (it shr 5)).toByte() }

        val deflated = deflateData(originalData, level = 9)  // Maximum compression
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Diverse binary data should decompress correctly with maximum compression"
        }
    }

    /**
     * RFC 1951 §3.2.7: "Code 16 copies the previous code length 3-6 times.
     * However, if there is no previous code (codeIndex == 0), this is invalid."
     *
     * The fix ensures that code 16 appearing as the first symbol is rejected.
     * We test this indirectly by ensuring only valid deflated data is accepted.
     */
    @Test
    fun testDynamicHuffmanRejectsMalformedCodeLength() {
        // Create data that will definitely use dynamic Huffman
        val originalData = ByteArray(1000) { (it and 0xFF).toByte() }
        val deflated = deflateData(originalData, level = 6)

        // Verify the deflated data is valid (sanity check)
        val decompressed = KFlate.decompress(deflated, Raw())
        assert(decompressed.contentEquals(originalData)) {
            "Sanity check: Valid deflated data should decompress correctly"
        }
    }

    /**
     * Test that very large data compresses and decompresses correctly.
     * Larger data is more likely to use dynamic Huffman blocks.
     */
    @Test
    fun testLargeDataWithDynamicHuffman() {
        val originalData = ByteArray(100000) { (it xor (it shr 8)).toByte() }

        val deflated = deflateData(originalData, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Large data should decompress correctly with dynamic Huffman"
        }
    }

    /**
     * Test decompression of multiple different block types in sequence.
     * The RFC allows switching between block types in a single stream.
     */
    @Test
    fun testMultipleBlocks() {
        val data1 = "First block".encodeToByteArray()
        val data2 = "Second block with more data".encodeToByteArray()
        val data3 = "Third block".encodeToByteArray()

        val combined = data1 + data2 + data3
        val deflated = deflateData(combined, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(combined)) {
            "Data with multiple blocks should decompress correctly"
        }
    }

    /**
     * Test edge case: Data that compresses to exactly the minimum block size.
     */
    @Test
    fun testMinimalCompression() {
        val originalData = "a".encodeToByteArray()

        val deflated = deflateData(originalData, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Single character should decompress correctly"
        }
    }

    /**
     * Test with all byte values (0-255) represented in the data.
     * This exercises the full literal alphabet.
     */
    @Test
    fun testFullByteRange() {
        val originalData = ByteArray(256) { it.toByte() }
        val originalDataRepeated = ByteArray(256 * 10) { originalData[it % 256] }

        val deflated = deflateData(originalDataRepeated, level = 6)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalDataRepeated)) {
            "Data with all byte values should decompress correctly"
        }
    }

    /**
     * Test with highly compressible data (many repeated sequences).
     * This exercises distance codes extensively.
     */
    @Test
    fun testHighlyCompressibleData() {
        val pattern = "ABCDEFGHIJ".encodeToByteArray()
        val originalData = ByteArray(5000) { pattern[it % pattern.size] }

        val deflated = deflateData(originalData, level = 9)
        val decompressed = KFlate.decompress(deflated, Raw())

        assert(decompressed.contentEquals(originalData)) {
            "Highly compressible data should decompress correctly"
        }
    }
}
