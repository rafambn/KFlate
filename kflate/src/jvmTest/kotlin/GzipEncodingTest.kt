package com.rafambn.kflate

import com.rafambn.kflate.format.getGzipHeaderSize
import com.rafambn.kflate.format.writeGzipHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GzipEncodingTest {

    @Test
    fun testValidAscii() {
        val gzip = GZIP(filename = "test.txt", comment = "Just a test")
        assertEquals("test.txt", gzip.filename)
        assertEquals("Just a test", gzip.comment)
    }

    @Test
    fun testValidIso88591() {
        // \u00E9 is 'é' (233), \u00F1 is 'ñ' (241)
        val filename = "t\u00E9st.txt"
        val comment = "Se\u00F1or"
        val gzip = GZIP(filename = filename, comment = comment)
        assertEquals(filename, gzip.filename)
        assertEquals(comment, gzip.comment)
    }

    @Test
    fun testInvalidFilename() {
        // \u0100 is 256, just outside ISO-8859-1
        assertFailsWith<IllegalArgumentException> {
            GZIP(filename = "test\u0100.txt")
        }
    }

    @Test
    fun testInvalidComment() {
        // \u2603 is Snowman
        assertFailsWith<IllegalArgumentException> {
            GZIP(comment = "Snowman \u2603")
        }
    }

    
    @Test
    fun testHeaderGenerationWithIso() {
        val filename = "caf\u00E9.txt" // café.txt
        val gzip = GZIP(filename = filename)

        // Calculate expected size
        // 10 bytes header
        // + filename ("caf\u00E9.txt" is 8 bytes in ISO-8859-1) + 1 null terminator
        // = 19 bytes

        val headerSize = getGzipHeaderSize(gzip)
        assertEquals(19, headerSize)

        val output = ByteArray(headerSize)
        writeGzipHeader(output, gzip)

        // Verify FNAME starts at index 10
        // 'c', 'a', 'f', 'é' (0xE9 = 233), '.', 't', 'x', 't', 0
        assertEquals('c'.code.toByte(), output[10])
        assertEquals('a'.code.toByte(), output[11])
        assertEquals('f'.code.toByte(), output[12])
        assertEquals(0xE9.toByte(), output[13]) // The special char
        assertEquals('.'.code.toByte(), output[14])
        assertEquals(0, output[18]) // Null terminator
    }
}
