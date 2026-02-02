
package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GzipConcatenatedMembersTest {

    @Test
    fun `Gzip decompress should handle single member (backward compatibility)`() {
        val originalData = "Hello KFlate World!".encodeToByteArray()
        val compressed = KFlate.compress(originalData, GZIP())

        val decompressed = KFlate.decompress(compressed, Gzip())
        assertEquals(originalData.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should concatenate two members`() {
        val data1 = "Hello ".encodeToByteArray()
        val data2 = "World!".encodeToByteArray()

        val compressed1 = KFlate.compress(data1, GZIP())
        val compressed2 = KFlate.compress(data2, GZIP())

        // Concatenate the two compressed members
        val concatenated = ByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.decompress(concatenated, Gzip())
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should concatenate three members`() {
        val data1 = "First ".encodeToByteArray()
        val data2 = "Second ".encodeToByteArray()
        val data3 = "Third".encodeToByteArray()

        val compressed1 = KFlate.compress(data1, GZIP())
        val compressed2 = KFlate.compress(data2, GZIP())
        val compressed3 = KFlate.compress(data3, GZIP())

        // Concatenate all three compressed members
        val concatenated = ByteArray(compressed1.size + compressed2.size + compressed3.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)
        compressed3.copyInto(concatenated, compressed1.size + compressed2.size)

        val decompressed = KFlate.decompress(concatenated, Gzip())
        val expected = data1 + data2 + data3
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should reject trailing garbage`() {
        val originalData = "Hello KFlate World!".encodeToByteArray()
        val compressed = KFlate.compress(originalData, GZIP())

        // Add garbage bytes at the end
        val withGarbage = ByteArray(compressed.size + 5)
        compressed.copyInto(withGarbage, 0)
        // Fill last 5 bytes with garbage
        for (i in 0 until 5) {
            withGarbage[compressed.size + i] = 0xFF.toByte()
        }

        try {
            KFlate.decompress(withGarbage, Gzip())
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
        val originalData = "Hello KFlate World!".encodeToByteArray()
        val compressed = KFlate.compress(originalData, GZIP())

        val data1 = "Start ".encodeToByteArray()
        val compressed1 = KFlate.compress(data1, GZIP())

        // Create concatenated data with incomplete second member
        val concatenated = ByteArray(compressed1.size + 5)
        compressed1.copyInto(concatenated, 0)
        // Add partial gzip header (only 5 bytes instead of full 10)
        concatenated[compressed1.size] = 31.toByte()
        concatenated[compressed1.size + 1] = 139.toByte()
        concatenated[compressed1.size + 2] = 8.toByte()
        concatenated[compressed1.size + 3] = 0.toByte()
        concatenated[compressed1.size + 4] = 0.toByte()

        try {
            KFlate.decompress(concatenated, Gzip())
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
        val data1 = "".encodeToByteArray()
        val data2 = "Content".encodeToByteArray()
        val compressed1 = KFlate.compress(data1, GZIP())
        val compressed2 = KFlate.compress(data2, GZIP())

        // Concatenate empty member with content member
        val concatenated = ByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.decompress(concatenated, Gzip())
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should reject invalid magic bytes in second member`() {
        val data1 = "First ".encodeToByteArray()
        val compressed1 = KFlate.compress(data1, GZIP())

        // Create invalid second member with wrong magic bytes
        val invalidMember = ByteArray(20)
        invalidMember[0] = 30.toByte()  // Wrong first byte (should be 31)
        invalidMember[1] = 139.toByte()
        invalidMember[2] = 8.toByte()

        val concatenated = ByteArray(compressed1.size + invalidMember.size)
        compressed1.copyInto(concatenated, 0)
        invalidMember.copyInto(concatenated, compressed1.size)

        try {
            KFlate.decompress(concatenated, Gzip())
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
        val data1 = "AAA".encodeToByteArray()
        val data2 = "BBB".encodeToByteArray()
        val data3 = "CCC".encodeToByteArray()
        val compressed1 = KFlate.compress(data1, GZIP())
        val compressed2 = KFlate.compress(data2, GZIP())
        val compressed3 = KFlate.compress(data3, GZIP())

        // Concatenate in specific order
        val concatenated = ByteArray(compressed1.size + compressed2.size + compressed3.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)
        compressed3.copyInto(concatenated, compressed1.size + compressed2.size)

        val decompressed = KFlate.decompress(concatenated, Gzip())
        val expected = "AAABBBCCC".encodeToByteArray()
        assertEquals(expected.toList(), decompressed.toList())
    }

    @Test
    fun `Gzip decompress should handle large concatenated members`() {
        val data1 = "A".repeat(1000).encodeToByteArray()
        val data2 = "B".repeat(2000).encodeToByteArray()
        val compressed1 = KFlate.compress(data1, GZIP())
        val compressed2 = KFlate.compress(data2, GZIP())

        val concatenated = ByteArray(compressed1.size + compressed2.size)
        compressed1.copyInto(concatenated, 0)
        compressed2.copyInto(concatenated, compressed1.size)

        val decompressed = KFlate.decompress(concatenated, Gzip())
        val expected = data1 + data2
        assertEquals(expected.toList(), decompressed.toList())
    }
}
