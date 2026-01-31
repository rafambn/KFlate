@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.error.FlateError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GzipHeaderValidationTest {

    private fun ByteArray.toUByteArray(): UByteArray {
        return UByteArray(this.size) { this[it].toUByte() }
    }

    private fun createMinimalGzipHeader(flags: Int): ByteArray {
        val header = ByteArray(10)
        header[0] = 31
        header[1] = 139.toByte()
        header[2] = 8
        header[3] = flags.toByte()
        // MTIME, XFL, OS left as 0
        return header
    }

    @Test
    fun testReservedFlagBits() {
        // Bit 5 is reserved (32)
        val data = createMinimalGzipHeader(32).toUByteArray()
        assertFailsWith<FlateError> {
            writeGzipStart(data)
        }
        
        // Bit 6 is reserved (64)
        val data2 = createMinimalGzipHeader(64).toUByteArray()
        assertFailsWith<FlateError> {
            writeGzipStart(data2)
        }

        // Bit 7 is reserved (128)
        val data3 = createMinimalGzipHeader(128).toUByteArray()
        assertFailsWith<FlateError> {
            writeGzipStart(data3)
        }
    }

    @Test
    fun testTruncatedExtraField() {
        val baseHeader = createMinimalGzipHeader(4) // FEXTRA set
        val extraData = ByteArray(4) // XLEN (2 bytes) + 2 bytes of data (but XLEN says 10)
        extraData[0] = 10 // XLEN low
        extraData[1] = 0  // XLEN high
        // Only providing 2 bytes of data (total 4 bytes for extra part), need 12 bytes total (2 len + 10 data)
        
        val data = (baseHeader + extraData).toUByteArray()
        
        assertFailsWith<FlateError> {
            writeGzipStart(data)
        }
    }

    @Test
    fun testTruncatedFilename() {
        val baseHeader = createMinimalGzipHeader(8) // FNAME set
        val filename = "missing-null-terminator".encodeToByteArray()
        
        val data = (baseHeader + filename).toUByteArray()
        
        assertFailsWith<FlateError> {
            writeGzipStart(data)
        }
    }

    @Test
    fun testTruncatedComment() {
        val baseHeader = createMinimalGzipHeader(16) // FCOMMENT set
        val comment = "missing-null-terminator".encodeToByteArray()
        
        val data = (baseHeader + comment).toUByteArray()
        
        assertFailsWith<FlateError> {
            writeGzipStart(data)
        }
    }
}
