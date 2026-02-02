@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

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
        val data = "hello".encodeToByteArray()
        // Build the DEFLATE stream manually
        // Bit 0: BFINAL (1)
        // Bits 1-2: BTYPE (00 for stored)
        // After byte alignment: LEN (16 bits LE), NLEN (16 bits LE), data
        val deflateStream = ByteArray(5 + data.size)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00, 1 padding bit
        deflateStream[1] = 0x05.toByte()  // LEN = 5 (little-endian: 0x05 0x00)
        deflateStream[2] = 0x00.toByte()
        deflateStream[3] = 0xFA.toByte()  // NLEN = 0xFFFA (one's complement of 5: ~0x0005 = 0xFFFA)
        deflateStream[4] = 0xFF.toByte()
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header (CMF=0x78, FLG=0x9C)
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Calculate ADLER32 for the data
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        val adler32 = ((b shl 16) or a)

        zlib[2 + deflateStream.size] = (adler32 shr 24).toByte()
        zlib[3 + deflateStream.size] = ((adler32 shr 16) and 0xFF).toByte()
        zlib[4 + deflateStream.size] = ((adler32 shr 8) and 0xFF).toByte()
        zlib[5 + deflateStream.size] = (adler32 and 0xFF).toByte()

        // This should decompress successfully
        val result = KFlate.decompress(zlib, Zlib())
        assert(result.contentEquals(data)) { "Decompressed data should match original" }
    }

    /**
     * Test that a stored block with corrupted NLEN is rejected.
     * If NLEN != ~LEN, the block should be rejected.
     */
    @Test
    fun testStoredBlockWithCorruptedNlen() {
        val data = "hello".encodeToByteArray()
        // Build DEFLATE stream with corrupted NLEN
        val deflateStream = ByteArray(5 + data.size)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05.toByte()  // LEN = 5
        deflateStream[2] = 0x00.toByte()
        deflateStream[3] = 0xFB.toByte()  // NLEN = 0xFFFB (WRONG! Should be 0xFFFA)
        deflateStream[4] = 0xFF.toByte()
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32 (doesn't matter since we'll fail before checksum validation)
        zlib[2 + deflateStream.size] = 0x00.toByte()
        zlib[3 + deflateStream.size] = 0x00.toByte()
        zlib[4 + deflateStream.size] = 0x00.toByte()
        zlib[5 + deflateStream.size] = 0x01.toByte()

        // This should fail with an error
        assertFailsWith<Exception> {
            KFlate.decompress(zlib, Zlib())
        }
    }

    /**
     * Test that a truncated stored block header (missing NLEN) is rejected.
     * If there aren't at least 4 bytes for LEN and NLEN, should fail.
     */
    @Test
    fun testStoredBlockTruncatedHeader() {
        // Build DEFLATE stream with incomplete header
        val deflateStream = ByteArray(3)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05.toByte()  // LEN = 5
        deflateStream[2] = 0x00.toByte()  // But missing NLEN (only 3 bytes total, need 4)

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32
        zlib[2 + deflateStream.size] = 0x00.toByte()
        zlib[3 + deflateStream.size] = 0x00.toByte()
        zlib[4 + deflateStream.size] = 0x00.toByte()
        zlib[5 + deflateStream.size] = 0x01.toByte()

        // This should fail with EOF error
        assertFailsWith<Exception> {
            KFlate.decompress(zlib, Zlib())
        }
    }

    /**
     * Test that a stored block with incorrect data length (LEN mismatch) is rejected.
     */
    @Test
    fun testStoredBlockTruncatedData() {
        // Build DEFLATE stream claiming 5 bytes but providing only 3
        val deflateStream = ByteArray(8)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x05.toByte()  // LEN = 5
        deflateStream[2] = 0x00.toByte()
        deflateStream[3] = 0xFA.toByte()  // NLEN = 0xFFFA (correct)
        deflateStream[4] = 0xFF.toByte()
        deflateStream[5] = 0x68.toByte()  // 'h'
        deflateStream[6] = 0x65.toByte()  // 'e'
        deflateStream[7] = 0x6C.toByte()  // 'l' (Only "hel" instead of "hello")

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32
        zlib[2 + deflateStream.size] = 0x00.toByte()
        zlib[3 + deflateStream.size] = 0x00.toByte()
        zlib[4 + deflateStream.size] = 0x00.toByte()
        zlib[5 + deflateStream.size] = 0x01.toByte()

        // This should fail with EOF error
        assertFailsWith<Exception> {
            KFlate.decompress(zlib, Zlib())
        }
    }

    /**
     * Test stored block with LEN=0 (empty block).
     * NLEN should be 0xFFFF (one's complement of 0x0000).
     */
    @Test
    fun testStoredBlockEmpty() {
        // Build DEFLATE stream with empty stored block
        val deflateStream = ByteArray(5)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0x00.toByte()  // LEN = 0
        deflateStream[2] = 0x00.toByte()
        deflateStream[3] = 0xFF.toByte()  // NLEN = 0xFFFF (one's complement of 0)
        deflateStream[4] = 0xFF.toByte()

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Add ADLER32 for empty data (should be 1)
        zlib[2 + deflateStream.size] = 0x00.toByte()
        zlib[3 + deflateStream.size] = 0x00.toByte()
        zlib[4 + deflateStream.size] = 0x00.toByte()
        zlib[5 + deflateStream.size] = 0x01.toByte()

        // This should decompress successfully to empty data
        val result = KFlate.decompress(zlib, Zlib())
        assert(result.isEmpty()) { "Empty block should decompress to empty data" }
    }

    /**
     * Test stored block with maximum length (65535 bytes).
     */
    @Test
    fun testStoredBlockMaxLength() {
        // Create data of maximum stored block size
        val data = ByteArray(65535) { it.toByte() }

        // Build DEFLATE stream with max-size stored block
        val deflateStream = ByteArray(5 + data.size)
        deflateStream[0] = 0x01.toByte()  // BFINAL=1, BTYPE=00
        deflateStream[1] = 0xFF.toByte()  // LEN = 65535 (0xFFFF in little-endian)
        deflateStream[2] = 0xFF.toByte()
        deflateStream[3] = 0x00.toByte()  // NLEN = 0x0000 (one's complement of 0xFFFF)
        deflateStream[4] = 0x00.toByte()
        data.copyInto(deflateStream, 5)

        // Wrap in ZLIB
        val zlib = ByteArray(2 + deflateStream.size + 4)
        zlib[0] = 0x78.toByte()  // ZLIB header
        zlib[1] = 0x9C.toByte()
        deflateStream.copyInto(zlib, 2)

        // Calculate ADLER32
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        val adler32 = ((b shl 16) or a)

        zlib[2 + deflateStream.size] = (adler32 shr 24).toByte()
        zlib[3 + deflateStream.size] = ((adler32 shr 16) and 0xFF).toByte()
        zlib[4 + deflateStream.size] = ((adler32 shr 8) and 0xFF).toByte()
        zlib[5 + deflateStream.size] = (adler32 and 0xFF).toByte()

        // This should decompress successfully
        val result = KFlate.decompress(zlib, Zlib())
        assert(result.contentEquals(data)) { "Decompressed data should match original" }
    }
}
