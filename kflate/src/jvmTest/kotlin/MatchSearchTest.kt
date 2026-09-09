package com.rafambn.kflate.algorithm

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchSearchTest {

    @Test
    fun walksPastHashCollisionToFindOlderMatch() {
        val data = byteArrayOf(1, 2, 3, 17, 3, 3, 1, 2, 3)
        val previous = ShortArray(MATCH_DISTANCE_MASK + 1)
        previous[3] = 0

        val match = findLongestMatch(
            data = data,
            dataSize = data.size,
            index = 6,
            previousIndex = 3,
            previous = previous,
            level = DeflateLevel(
                niceLength = 258,
                chainLength = 4,
                maxLazyLength = 0,
                maxHashBits = 12,
            ),
        )

        assertEquals(3, match ushr MATCH_DISTANCE_BITS)
        assertEquals(6, match and MATCH_DISTANCE_MASK)
    }
}
