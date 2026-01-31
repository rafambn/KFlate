@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.options.DeflateOptions
import com.rafambn.kflate.options.InflateOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZlibDictidValidationTest {

    private fun ByteArray.toUByteArray(): UByteArray = UByteArray(this.size) { this[it].toUByte() }

    @Test
    fun testValidDictidMatch() {
        val data = "hello world".encodeToByteArray().toUByteArray()
        val dictionary = "common".encodeToByteArray().toUByteArray()
        val options = DeflateOptions(dictionary = dictionary)

        val compressed = KFlate.Zlib.compress(data, options)
        val decompressed = KFlate.Zlib.decompress(compressed, InflateOptions(dictionary = dictionary))

        assertEquals("hello world", decompressed.toByteArray().decodeToString())
    }

    @Test
    fun testMismatchedDictid() {
        val data = "hello world".encodeToByteArray().toUByteArray()
        val correctDict = "common".encodeToByteArray().toUByteArray()
        val wrongDict = "wrong".encodeToByteArray().toUByteArray()

        val options = DeflateOptions(dictionary = correctDict)
        val compressed = KFlate.Zlib.compress(data, options)

        val error = assertFailsWith<FlateError> {
            KFlate.Zlib.decompress(compressed, InflateOptions(dictionary = wrongDict))
        }
        assertEquals(FlateErrorCode.CHECKSUM_MISMATCH, error.code)
    }

    @Test
    fun testFdictFlagSetButNoDictionary() {
        val data = "hello world".encodeToByteArray().toUByteArray()
        val dictionary = "common".encodeToByteArray().toUByteArray()

        val options = DeflateOptions(dictionary = dictionary)
        val compressed = KFlate.Zlib.compress(data, options)

        // Try to decompress without providing the required dictionary
        val error = assertFailsWith<FlateError> {
            KFlate.Zlib.decompress(compressed, InflateOptions())
        }
        assertEquals(FlateErrorCode.INVALID_HEADER, error.code)
    }

    @Test
    fun testNoDictidWhenFdictNotSet() {
        val data = "hello world".encodeToByteArray().toUByteArray()

        val compressed = KFlate.Zlib.compress(data)
        val decompressed = KFlate.Zlib.decompress(compressed)

        assertEquals("hello world", decompressed.toByteArray().decodeToString())
    }

    @Test
    fun testTruncatedDictid() {
        val data = "hello world".encodeToByteArray().toUByteArray()
        val dictionary = "common".encodeToByteArray().toUByteArray()

        val options = DeflateOptions(dictionary = dictionary)
        val compressed = KFlate.Zlib.compress(data, options)

        // Truncate to remove the DICTID (it's after the 2-byte header)
        // Header with DICTID is 6 bytes total (2 bytes CMF/FLG + 4 bytes DICTID)
        val truncated = compressed.copyOfRange(0, 5)

        val error = assertFailsWith<FlateError> {
            KFlate.Zlib.decompress(truncated, InflateOptions(dictionary = dictionary))
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, error.code)
    }

    @Test
    fun testCorruptedDictid() {
        val data = "hello world".encodeToByteArray().toUByteArray()
        val dictionary = "common".encodeToByteArray().toUByteArray()

        val options = DeflateOptions(dictionary = dictionary)
        val compressed = KFlate.Zlib.compress(data, options).toMutableList()

        // Corrupt the DICTID bytes (bytes 2-5)
        compressed[2] = (compressed[2] + 1u).toUByte()

        val error = assertFailsWith<FlateError> {
            KFlate.Zlib.decompress(compressed.toUByteArray(), InflateOptions(dictionary = dictionary))
        }
        assertEquals(FlateErrorCode.CHECKSUM_MISMATCH, error.code)
    }
}
