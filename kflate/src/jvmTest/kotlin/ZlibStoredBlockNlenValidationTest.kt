@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.options.InflateOptions
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ZlibStoredBlockNlenValidationTest {

    /**
     * Test that a stored block with correct NLEN (one's complement of LEN) is accepted.
     * RFC 1951 §3.2.4: "NLEN is the bitwise complement of LEN"
     */
    @Test
    fun testStoredBlockWithValidNlen() {
        // Create a minimal DEFLATE stream with a stored block
        // BFINAL=1, BTYPE=00 (stored), then LEN=5, NLEN=~5
        val data = "hello".encodeToByteArray().toUByteArray()

        // Build the DEFLATE stream manually
        // Bit 0: BFINAL (1)
        // Bits 1-2: BTYPE (00 for stored)
        // After byte alignment: LEN (16 bits LE), NLEN (16 bits LE), data
        val deflateStream = UByteArray(5 + data.size)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00, 1 padding bit
        deflateStream[1] = 0x05u  // LEN = 5 (little-endian: 0x05 0x00)
        deflateStream[2] = 0x00u
        deflateStream[3] = 0xFAu  // NLEN = 0xFFFA (one's complement of 5: ~0x0005 = 0xFFFA)
        deflateStream[4] = 0xFFu
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header (CMF=0x78, FLG=0x9C)
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Calculate ADLER32 for the data
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + byte.toInt()) % 65521
            b = (b + a) % 65521
        }
        val adler32 = ((b shl 16) or a)

        zlib[2 + deflateStream.size] = (adler32 shr 24).toUByte()
        zlib[3 + deflateStream.size] = ((adler32 shr 16) and 0xFF).toUByte()
        zlib[4 + deflateStream.size] = ((adler32 shr 8) and 0xFF).toUByte()
        zlib[5 + deflateStream.size] = (adler32 and 0xFF).toUByte()

        // This should decompress successfully
        val result = KFlate.Zlib.decompress(zlib, InflateOptions())
        assert(result.contentEquals(data)) { "Decompressed data should match original" }
    }

    /**
     * Test that a stored block with corrupted NLEN is rejected.
     * If NLEN != ~LEN, the block should be rejected.
     */
    @Test
    fun testStoredBlockWithCorruptedNlen() {
        val data = "hello".encodeToByteArray().toUByteArray()

        // Build DEFLATE stream with corrupted NLEN
        val deflateStream = UByteArray(5 + data.size)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05u  // LEN = 5
        deflateStream[2] = 0x00u
        deflateStream[3] = 0xFBu  // NLEN = 0xFFFB (WRONG! Should be 0xFFFA)
        deflateStream[4] = 0xFFu
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32 (doesn't matter since we'll fail before checksum validation)
        zlib[2 + deflateStream.size] = 0x00u
        zlib[3 + deflateStream.size] = 0x00u
        zlib[4 + deflateStream.size] = 0x00u
        zlib[5 + deflateStream.size] = 0x01u

        // This should fail with an error
        assertFailsWith<Exception> {
            KFlate.Zlib.decompress(zlib, InflateOptions())
        }
    }

    /**
     * Test that a truncated stored block header (missing NLEN) is rejected.
     * If there aren't at least 4 bytes for LEN and NLEN, should fail.
     */
    @Test
    fun testStoredBlockTruncatedHeader() {
        // Build DEFLATE stream with incomplete header
        val deflateStream = UByteArray(3)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05u  // LEN = 5
        deflateStream[2] = 0x00u  // But missing NLEN (only 3 bytes total, need 4)

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32
        zlib[2 + deflateStream.size] = 0x00u
        zlib[3 + deflateStream.size] = 0x00u
        zlib[4 + deflateStream.size] = 0x00u
        zlib[5 + deflateStream.size] = 0x01u

        // This should fail with EOF error
        assertFailsWith<Exception> {
            KFlate.Zlib.decompress(zlib, InflateOptions())
        }
    }

    /**
     * Test that a stored block with incorrect data length (LEN mismatch) is rejected.
     */
    @Test
    fun testStoredBlockTruncatedData() {
        // Build DEFLATE stream claiming 5 bytes but providing only 3
        val deflateStream = UByteArray(8)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05u  // LEN = 5
        deflateStream[2] = 0x00u
        deflateStream[3] = 0xFAu  // NLEN = 0xFFFA (correct)
        deflateStream[4] = 0xFFu
        deflateStream[5] = 0x68u  // 'h'
        deflateStream[6] = 0x65u  // 'e'
        deflateStream[7] = 0x6Cu  // 'l' (Only "hel" instead of "hello")

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32
        zlib[2 + deflateStream.size] = 0x00u
        zlib[3 + deflateStream.size] = 0x00u
        zlib[4 + deflateStream.size] = 0x00u
        zlib[5 + deflateStream.size] = 0x01u

        // This should fail with EOF error
        assertFailsWith<Exception> {
            KFlate.Zlib.decompress(zlib, InflateOptions())
        }
    }

    /**
     * Test stored block with LEN=0 (empty block).
     * NLEN should be 0xFFFF (one's complement of 0x0000).
     */
    @Test
    fun testStoredBlockEmpty() {
        // Build DEFLATE stream with empty stored block
        val deflateStream = UByteArray(5)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x00u  // LEN = 0
        deflateStream[2] = 0x00u
        deflateStream[3] = 0xFFu  // NLEN = 0xFFFF (one's complement of 0)
        deflateStream[4] = 0xFFu

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32 for empty data (should be 1)
        zlib[2 + deflateStream.size] = 0x00u
        zlib[3 + deflateStream.size] = 0x00u
        zlib[4 + deflateStream.size] = 0x00u
        zlib[5 + deflateStream.size] = 0x01u

        // This should decompress successfully to empty data
        val result = KFlate.Zlib.decompress(zlib, InflateOptions())
        assert(result.isEmpty()) { "Empty block should decompress to empty data" }
    }

    /**
     * Test stored block with maximum length (65535 bytes).
     */
    @Test
    fun testStoredBlockMaxLength() {
        // Create data of maximum stored block size
        val data = UByteArray(65535) { it.toUByte() }

        // Build DEFLATE stream with max-size stored block
        val deflateStream = UByteArray(5 + data.size)
        deflateStream[0] = 0x01u  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0xFFu  // LEN = 65535 (0xFFFF in little-endian)
        deflateStream[2] = 0xFFu
        deflateStream[3] = 0x00u  // NLEN = 0x0000 (one's complement of 0xFFFF)
        deflateStream[4] = 0x00u
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = UByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78u  // ZLIB header
        zlib[1] = 0x9Cu
        deflateStream.copyInto(zlib, 2)

        // Calculate ADLER32
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + byte.toInt()) % 65521
            b = (b + a) % 65521
        }
        val adler32 = ((b shl 16) or a)

        zlib[2 + deflateStream.size] = (adler32 shr 24).toUByte()
        zlib[3 + deflateStream.size] = ((adler32 shr 16) and 0xFF).toUByte()
        zlib[4 + deflateStream.size] = ((adler32 shr 8) and 0xFF).toUByte()
        zlib[5 + deflateStream.size] = (adler32 and 0xFF).toUByte()

        // This should decompress successfully
        val result = KFlate.Zlib.decompress(zlib, InflateOptions())
        assert(result.contentEquals(data)) { "Decompressed data should match original" }
    }
}
