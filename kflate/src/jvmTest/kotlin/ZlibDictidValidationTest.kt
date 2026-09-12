
package com.rafambn.kflate

import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZlibDictidValidationTest {

    @Test
    fun testValidDictidMatch() {
        val data = "hello world".encodeToByteArray()
        val dictionary = "common".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib(dictionary = dictionary))
        val decompressed = KFlate.decompress(compressed, DecompressionZlib(dictionary = dictionary))

        assertEquals("hello world", decompressed.decodeToString())
    }

    @Test
    fun testMismatchedDictid() {
        val data = "hello world".encodeToByteArray()
        val correctDict = "common".encodeToByteArray()
        val wrongDict = "wrong".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib(dictionary = correctDict))

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed, DecompressionZlib(dictionary = wrongDict))
        }
        assertEquals(FlateErrorCode.CHECKSUM_MISMATCH, error.code)
    }

    @Test
    fun testFdictFlagSetButNoDictionary() {
        val data = "hello world".encodeToByteArray()
        val dictionary = "common".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib(dictionary = dictionary))

        // Try to decompress without providing the required dictionary
        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed, DecompressionZlib())
        }
        assertEquals(FlateErrorCode.INVALID_HEADER, error.code)
    }

    @Test
    fun testNoDictidWhenFdictNotSet() {
        val data = "hello world".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib())
        val decompressed = KFlate.decompress(compressed, DecompressionZlib())

        assertEquals("hello world", decompressed.decodeToString())
    }

    @Test
    fun testTruncatedDictid() {
        val data = "hello world".encodeToByteArray()
        val dictionary = "common".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib(dictionary = dictionary))

        // Truncate to remove the DICTID (it's after the 2-byte header)
        // Header with DICTID is 6 bytes total (2 bytes CMF/FLG + 4 bytes DICTID)
        val truncated = compressed.copyOfRange(0, 5)

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(truncated, DecompressionZlib(dictionary = dictionary))
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, error.code)
    }

    @Test
    fun testCorruptedDictid() {
        val data = "hello world".encodeToByteArray()
        val dictionary = "common".encodeToByteArray()

        val compressed = KFlate.compress(data, CompressionZlib(dictionary = dictionary)).toMutableList()

        // Corrupt the DICTID bytes (bytes 2-5)
        compressed[2] = (compressed[2].toInt() + 1).toByte()

        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed.toByteArray(), DecompressionZlib(dictionary = dictionary))
        }
        assertEquals(FlateErrorCode.CHECKSUM_MISMATCH, error.code)
    }
}
