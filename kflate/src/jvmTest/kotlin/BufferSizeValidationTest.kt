
package com.rafambn.kflate

import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BufferSizeValidationTest {

    @Test
    fun testMemoryLevelValidation() {
        // Should fail if mem < 0
        assertFailsWith<IllegalArgumentException> {
            CompressionRaw(mem = -1)
        }

        // Should fail if mem > 12
        assertFailsWith<IllegalArgumentException> {
            CompressionRaw(mem = 13)
        }

        // Should succeed if mem is in valid range
        CompressionRaw(mem = 0)
        CompressionRaw(mem = 8)
        CompressionRaw(mem = 12)
    }

    @Test
    fun testCompressionWithDifferentMemoryLevels() {
        val originalData = "This is a test string that will be compressed with different memory levels. ".repeat(10).toByteArray()

        val memLevels = listOf(0, 4, 8, 12)

        for (memLevel in memLevels) {
            val type = CompressionRaw(mem = memLevel)
            val compressed = KFlate.compress(originalData, type)
            val decompressed = KFlate.decompress(compressed, DecompressionRaw())
            assertContentEquals(originalData, decompressed, "Failed for mem $memLevel")
        }
    }
}
