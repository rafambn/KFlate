
package com.rafambn.kflate

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BufferSizeValidationTest {

    @Test
    fun testMemoryLevelValidation() {
        // Should fail if mem < 0
        assertFailsWith<IllegalArgumentException> {
            RawCompression(mem = -1)
        }

        // Should fail if mem > 12
        assertFailsWith<IllegalArgumentException> {
            RawCompression(mem = 13)
        }

        // Should succeed if mem is in valid range
        RawCompression(mem = 0)
        RawCompression(mem = 8)
        RawCompression(mem = 12)
    }

    @Test
    fun testCompressionWithDifferentMemoryLevels() {
        val originalData = "This is a test string that will be compressed with different memory levels. ".repeat(10).toByteArray()

        val memLevels = listOf(0, 4, 8, 12)

        for (memLevel in memLevels) {
            val type = RawCompression(mem = memLevel)
            val compressed = KFlate.compress(originalData, type)
            val decompressed = KFlate.decompress(compressed, RawDecompression())
            assertContentEquals(originalData, decompressed, "Failed for mem $memLevel")
        }
    }
}
