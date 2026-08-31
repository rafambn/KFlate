package com.rafambn.kflate

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ConfigurationIsolationTest {
    @Test
    fun dictionaryOptionsDoNotRetainCallerOwnedArrays() {
        val dictionary = "shared dictionary content".encodeToByteArray()
        val compression = ZlibCompression(dictionary = dictionary)
        val decompression = ZlibDecompression(dictionary = dictionary)
        dictionary.fill(0)
        compression.dictionary?.fill(1)
        decompression.dictionary?.fill(2)
        val original = "shared dictionary content repeated".repeat(20).encodeToByteArray()

        val compressed = KFlate.compress(original, compression)

        assertContentEquals(original, KFlate.decompress(compressed, decompression))
    }

    @Test
    fun gzipExtraFieldsDoNotRetainCallerOwnedArrays() {
        val fieldData = byteArrayOf(1, 2, 3)
        val options = GzipCompression(extraFields = mapOf("AB" to fieldData))
        fieldData[0] = 9
        options.extraFields?.get("AB")?.set(0, 8)

        val compressed = KFlate.compress(byteArrayOf(1), options)

        assertEquals(1, compressed[16].toInt())
    }
}
