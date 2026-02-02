@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.options.GzipOptions
import com.rafambn.kflate.options.DeflateOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun testHeaderGenerationWithIso() {
        val filename = "caf\u00E9.txt" // café.txt
        val gzip = GZIP(filename = filename)

        // Convert to internal GzipOptions for testing header functions
        val options = GzipOptions(
            level = gzip.level,
            bufferSize = gzip.bufferSize,
            dictionary = gzip.dictionary?.let { UByteArray(it.size) { i -> it[i].toUByte() } },
            filename = gzip.filename,
            mtime = gzip.mtime,
            comment = gzip.comment,
            extraFields = gzip.extraFields?.mapValues { (_, v) ->
                UByteArray(v.size) { i -> v[i].toUByte() }
            },
            includeHeaderCrc = gzip.includeHeaderCrc
        )

        // Calculate expected size
        // 10 bytes header
        // + filename ("caf\u00E9.txt" is 8 bytes in ISO-8859-1) + 1 null terminator
        // = 19 bytes

        val headerSize = getGzipHeaderSize(options)
        assertEquals(19, headerSize)

        val output = UByteArray(headerSize)
        writeGzipHeader(output, options)

        // Verify FNAME starts at index 10
        // 'c', 'a', 'f', 'é' (0xE9 = 233), '.', 't', 'x', 't', 0
        assertEquals('c'.code.toUByte(), output[10])
        assertEquals('a'.code.toUByte(), output[11])
        assertEquals('f'.code.toUByte(), output[12])
        assertEquals(0xE9.toUByte(), output[13]) // The special char
        assertEquals('.'.code.toUByte(), output[14])
        assertEquals(0u, output[18]) // Null terminator
    }
}
