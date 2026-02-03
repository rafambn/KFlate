package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.format.getGzipHeaderSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GzipFhcrcValidationTest {


    @Test
    fun testValidFhcrc() {
        val data = "Hello World".encodeToByteArray()
        val gzip = GZIP(includeHeaderCrc = true)
        val compressed = KFlate.compress(data, gzip)

        val decompressed = KFlate.decompress(compressed, Gzip())
        assertEquals("Hello World", decompressed.decodeToString())
    }

    @Test
    fun testCorruptedHeaderWithFhcrc() {
        val data = "Hello World".encodeToByteArray()
        val gzip = GZIP(includeHeaderCrc = true)
        val compressed = KFlate.compress(data, gzip).toMutableList()

        // Corrupt a byte in the header (e.g., byte 9 is OS, byte 8 is XFL)
        // Let's corrupt the modification time (bytes 4-7)
        compressed[5] = (compressed[5] + 1).toByte()

        assertFailsWith<FlateError> {
            KFlate.decompress(compressed.toByteArray(), Gzip())
        }
    }

    @Test
    fun testCorruptedFhcrcValue() {
        val data = "Hello World".encodeToByteArray()
        val gzip = GZIP(includeHeaderCrc = true)
        val compressed = KFlate.compress(data, gzip).toMutableList()

        // The FHCRC is the last 2 bytes of the header.
        // We need to find where the header ends.
        // Basic header is 10 bytes. FHCRC adds 2 bytes. Total 12 bytes if no other fields.

        // Verify header size
        assertEquals(12, getGzipHeaderSize(gzip))

        // Corrupt the FHCRC (last byte of the header, index 11)
        compressed[11] = (compressed[11] + 1).toByte()

        assertFailsWith<FlateError> {
            KFlate.decompress(compressed.toByteArray(), Gzip())
        }
    }

    @Test
    fun testTruncatedFhcrc() {
        val data = "Hello World".encodeToByteArray()
        val gzip = GZIP(includeHeaderCrc = true)
        val compressed = KFlate.compress(data, gzip)

        // Truncate the last byte of the FHCRC
        // Header size is 12 bytes.
        // We slice up to 11 bytes (0..10), so we are missing the last byte of CRC.
        // But wait, decompress needs the whole payload too.
        // If we truncate the header, it should fail at header parsing.

        val truncated = compressed.copyOfRange(0, 11)

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(truncated, Gzip())
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, error.code)
    }
}
