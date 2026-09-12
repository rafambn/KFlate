package com.rafambn.kflate

import com.rafambn.kflate.algorithm.MATCH_DISTANCE_MASK
import com.rafambn.kflate.algorithm.deflate
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.streaming.DeflateState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DeflateScratchSizingTest {

    @Test
    fun tinyAndEmptyFinalInputsRoundTripAtEveryCompressionLevel() {
        val inputs = listOf(ByteArray(0), byteArrayOf(1, 2, 3))

        for (level in 1..9) {
            for (input in inputs) {
                val compressed = KFlate.compress(input, CompressionRaw(level = level))
                assertContentEquals(
                    input,
                    KFlate.decompress(compressed, DecompressionRaw()),
                    "level $level",
                )
            }
        }
    }

    @Test
    fun finalDictionaryInputRoundTripsWithSmallScratchBuffers() {
        val dictionary = "shared prefix ".repeat(8).encodeToByteArray()
        val input = "shared prefix payload".encodeToByteArray()

        for (level in listOf(1, 6, 9)) {
            val compressed = KFlate.compress(
                input,
                CompressionRaw(level = level, dictionary = dictionary),
            )
            assertContentEquals(
                input,
                KFlate.decompress(compressed, DecompressionRaw(dictionary = dictionary)),
                "level $level",
            )
        }
    }

    @Test
    fun nonFinalStateRetainsTheFullHistoryRingAndReusesItOnFinalCall() {
        val state = DeflateState(isLastChunk = false)
        val input = byteArrayOf(1, 2, 3)

        deflate(input, level = 6, hashBits = 12, prefixSize = 0, postfixSize = 0, state = state)

        val history = state.prev
        assertEquals(MATCH_DISTANCE_MASK + 1, history!!.size)

        state.isLastChunk = true
        deflate(input, level = 6, hashBits = 12, prefixSize = 0, postfixSize = 0, state = state)

        assertSame(history, state.prev)
    }
}
