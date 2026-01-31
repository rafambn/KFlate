package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalUnsignedTypes::class)
class GzipTrailerValidationTest {

    @Test
    fun `Gzip decompress should fail when CRC32 is invalid`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        // Corrupt CRC32 (located at index size-8 to size-5)
        // Gzip trailer: CRC32 (4 bytes) + ISIZE (4 bytes)
        val corrupted = compressed.copyOf()
        val crcOffset = corrupted.size - 8
        corrupted[crcOffset] = (corrupted[crcOffset] + 1u).toUByte()

        try {
            KFlate.Gzip.decompress(corrupted)
            // If we reach here, validation failed to catch the error
             fail("Should have thrown FlateError for invalid CRC32")
        } catch (e: FlateError) {
             // Expected error
             assertEquals(FlateErrorCode.CRC_MISMATCH, e.code)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
             fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }

    @Test
    fun `Gzip decompress should fail when ISIZE is invalid`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        // Corrupt ISIZE (located at index size-4 to size-1)
        val corrupted = compressed.copyOf()
        val isizeOffset = corrupted.size - 4
        // Changing ISIZE might cause allocation issues or just silent acceptance if we don't check
        corrupted[isizeOffset] = (corrupted[isizeOffset] + 1u).toUByte()

        try {
            KFlate.Gzip.decompress(corrupted)
             fail("Should have thrown FlateError for invalid ISIZE")
        } catch (e: FlateError) {
             // Expected
             assertEquals(FlateErrorCode.ISIZE_MISMATCH, e.code)
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
             // It might currently throw an exception due to array copy range if ISIZE is too big/small vs actual content
             // But we want a specific FlateError
             fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }
    
    @Test
    fun `Gzip decompress should fail when trailer is truncated`() {
        val originalData = "Hello KFlate World!".encodeToByteArray().asUByteArray()
        val compressed = KFlate.Gzip.compress(originalData)

        // Truncate the last byte
        val truncated = compressed.copyOfRange(0, compressed.size - 1)

        try {
            KFlate.Gzip.decompress(truncated)
            fail("Should have thrown FlateError for truncated data")
        } catch (e: FlateError) {
            // Expected
        } catch (e: AssertionError) {
            throw e
        } catch (e: Exception) {
             // fail("Unexpected exception type: ${e::class.simpleName}")
        }
    }
}
