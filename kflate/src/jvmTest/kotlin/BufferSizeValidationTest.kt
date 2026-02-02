
package com.rafambn.kflate

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BufferSizeValidationTest {

    @Test
    fun testBufferSizeValidation() {
        // Should fail if bufferSize < 1024
        assertFailsWith<IllegalArgumentException> {
            RAW(bufferSize = 512)
        }
        
        // Should succeed if bufferSize >= 1024
        RAW(bufferSize = 1024)
    }

    @Test
    fun testCompressionWithDifferentBufferSizes() {
        val originalData = "This is a test string that will be compressed with different buffer sizes. ".repeat(10).toByteArray()
        
        val sizes = listOf(1024, 4096, 16384, 65536)
        
        for (size in sizes) {
            val type = RAW(bufferSize = size)
            val compressed = KFlate.compress(originalData, type)
            val decompressed = KFlate.decompress(compressed, Raw())
            assertContentEquals(originalData, decompressed, "Failed for bufferSize $size")
        }
    }
}
