package com.rafambn.kflate.algorithm

import kotlin.test.Test
import kotlin.test.assertContentEquals

class MatchCopyTest {

    @Test
    fun copiesNonOverlappingMatchInOneBulkOperation() {
        val buffer = ByteArray(16)
        buffer[0] = 1
        buffer[1] = 2
        buffer[2] = 3
        buffer[3] = 4
        buffer[4] = 5

        copyMatch(buffer, destinationOffset = 8, distance = 8, length = 5)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), buffer.copyOfRange(8, 13))
        assertContentEquals(byteArrayOf(0, 0, 0), buffer.copyOfRange(13, 16))
    }

    @Test
    fun expandsOverlappingMatchWithoutReadingUnwrittenBytes() {
        val buffer = ByteArray(16)
        buffer[0] = 1
        buffer[1] = 2
        buffer[2] = 3

        copyMatch(buffer, destinationOffset = 3, distance = 3, length = 13)

        assertContentEquals(
            byteArrayOf(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1),
            buffer,
        )
    }

    @Test
    fun fillsSingleByteBackReferenceAcrossOverlap() {
        val buffer = ByteArray(12)
        buffer[0] = 7

        copyMatch(buffer, destinationOffset = 1, distance = 1, length = 11)

        assertContentEquals(ByteArray(12) { 7 }, buffer)
    }
}
