@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GzipConcatenatedMembersTest {

    @Test
    fun `Gzip decompress should handle single member (backward compatibility)`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        val decompressed = KFlate.Gzip.decompress(compressed)
        assertEquals(originalData.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should concatenate two members`() {
        val data1 = "Hello ".encodeToByteArray().asUByteArray()
        val data2 = "World!".encodeToByteArray().asUByteArray()

        val compressed1 = KFlate.Gzip.compress(data1)
        val compressed2 = KFlate.Gzip.compress(data2)

        // Concatenate the two compressed members
        val concatenated = UByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.Gzip.decompress(concatenated)
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should concatenate three members`() {
        val data1 = "First ".encodeToByteArray().asUByteArray()
        val data2 = "Second ".encodeToByteArray().asUByteArray()
        val data3 = "Third".encodeToByteArray().asUByteArray()

        val compressed1 = KFlate.Gzip.compress(data1)
        val compressed2 = KFlate.Gzip.compress(data2)
        val compressed3 = KFlate.Gzip.compress(data3)

        // Concatenate all three compressed members
        val concatenated = UByteArray(compressed1.size + compressed2.size + compressed3.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)
        compressed3.copyInto(concatenated, compressed1.size + compressed2.size)

        val decompressed = KFlate.Gzip.decompress(concatenated)
        val expected = data1 + data2 + data3
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should reject trailing garbage`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        // Add garbage bytes at the end
        val withGarbage = UByteArray(compressed.size + 5)
        compressed.copyInto(withGarbage, 0)
        // Fill last 5 bytes with garbage
        for (i in 0 until 5) {
            withGarbage[compressed.size + i] = 0xFFu
        }

        try {
            KFlate.Gzip.decompress(withGarbage)
            fail("Should have thrown FlateError for trailing garbage")
        } catch (e: FlateError) {
            assertEquals(FlateErrorCode.TRAILING_GARBAGE, e.code)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
            fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }

    @Test
    fun `Gzip decompress should reject incomplete member`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        val data1 = "Start ".encodeToByteArray().asUByteArray()
        val compressed1 = KFlate.Gzip.compress(data1)

        // Create concatenated data with incomplete second member
        val concatenated = UByteArray(compressed1.size + 5)
        compressed1.copyInto(concatenated, 0)
        // Add partial gzip header (only 5 bytes instead of full 10)
        concatenated[compressed1.size] = 31u
        concatenated[compressed1.size + 1] = 139u
        concatenated[compressed1.size + 2] = 8u
        concatenated[compressed1.size + 3] = 0u
        concatenated[compressed1.size + 4] = 0u

        try {
            KFlate.Gzip.decompress(concatenated)
            fail("Should have thrown FlateError for incomplete member")
        } catch (e: FlateError) {
            // Should be either TRAILING_GARBAGE or UNEXPECTED_EOF
            assertEquals(FlateErrorCode.TRAILING_GARBAGE, e.code)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
            fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }

    @Test
    fun `Gzip decompress should handle empty member concatenation`() {
        val data1 = "".encodeToByteArray().asUByteArray()
        val data2 = "Content".encodeToByteArray().asUByteArray()

        val compressed1 = KFlate.Gzip.compress(data1)
        val compressed2 = KFlate.Gzip.compress(data2)

        // Concatenate empty member with content member
        val concatenated = UByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.Gzip.decompress(concatenated)
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should reject invalid magic bytes in second member`() {
        val data1 = "First ".encodeToByteArray().asUByteArray()
        val compressed1 = KFlate.Gzip.compress(data1)

        // Create invalid second member with wrong magic bytes
        val invalidMember = UByteArray(20)
        invalidMember[0] = 30u  // Wrong first byte (should be 31)
        invalidMember[1] = 139u
        invalidMember[2] = 8u

        val concatenated = UByteArray(compressed1.size + invalidMember.size)
        compressed1.copyInto(concatenated, 0)
        invalidMember.copyInto(concatenated, compressed1.size)

        try {
            KFlate.Gzip.decompress(concatenated)
            fail("Should have thrown FlateError for invalid magic bytes")
        } catch (e: FlateError) {
            assertEquals(FlateErrorCode.TRAILING_GARBAGE, e.code)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
            fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }

    @Test
    fun `Gzip decompress should preserve order of concatenated members`() {
        val data1 = "AAA".encodeToByteArray().asUByteArray()
        val data2 = "BBB".encodeToByteArray().asUByteArray()
        val data3 = "CCC".encodeToByteArray().asUByteArray()

        val compressed1 = KFlate.Gzip.compress(data1)
        val compressed2 = KFlate.Gzip.compress(data2)
        val compressed3 = KFlate.Gzip.compress(data3)

        // Concatenate in specific order
        val concatenated = UByteArray(compressed1.size + compressed2.size + compressed3.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)
        compressed3.copyInto(concatenated, compressed1.size + compressed2.size)

        val decompressed = KFlate.Gzip.decompress(concatenated)
        val expected = "AAABBBCCC".encodeToByteArray().asUByteArray()
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should handle large concatenated members`() {
        val data1 = "A".repeat(1000).encodeToByteArray().asUByteArray()
        val data2 = "B".repeat(2000).encodeToByteArray().asUByteArray()

        val compressed1 = KFlate.Gzip.compress(data1)
        val compressed2 = KFlate.Gzip.compress(data2)

        val concatenated = UByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.Gzip.decompress(concatenated)
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }
}
