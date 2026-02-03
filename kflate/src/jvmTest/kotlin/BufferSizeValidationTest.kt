
package com.rafambn.kflate

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BufferSizeValidationTest {

    @Test
    fun testMemoryLevelValidation() {
        // Should fail if mem < 0
        assertFailsWith<IllegalArgumentException> {
            RAW(mem = -1)
        }

        // Should fail if mem > 12
        assertFailsWith<IllegalArgumentException> {
            RAW(mem = 13)
        }

        // Should succeed if mem is in valid range
        RAW(mem = 0)
        RAW(mem = 8)
        RAW(mem = 12)
    }

    @Test
    fun testCompressionWithDifferentMemoryLevels() {
        val originalData = "This is a test string that will be compressed with different memory levels. ".repeat(10).toByteArray()

        val memLevels = listOf(0, 4, 8, 12)

        for (memLevel in memLevels) {
            val type = RAW(mem = memLevel)
            val compressed = KFlate.compress(originalData, type)
            val decompressed = KFlate.decompress(compressed, Raw())
            assertContentEquals(originalData, decompressed, "Failed for mem $memLevel")
        }
    }
}
