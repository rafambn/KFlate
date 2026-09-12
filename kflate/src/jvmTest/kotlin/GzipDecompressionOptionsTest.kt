package com.rafambn.kflate

import com.rafambn.kflate.decompression.Gzip
import kotlin.test.Test
import kotlin.test.assertEquals

class GzipDecompressionOptionsTest {

    @Test
    fun testEquivalentInstancesHaveValueSemantics() {
        val first = Gzip()
        val second = Gzip()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, setOf(first, second).size)
    }
}
