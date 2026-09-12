package com.rafambn.kflate

import com.rafambn.kflate.decompression.Raw
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReservedLiteralLengthCodeTest {
    @Test
    fun reservedLiteralLengthCodesAreRejected() {
        val compressedBySymbol = listOf(
            286 to byteArrayOf(0x1b, 0x03, 0x00),
            287 to byteArrayOf(0x1b, 0x07, 0x00),
        )

        for ((symbol, compressed) in compressedBySymbol) {
            val error = assertFailsWith<FlateError>("Reserved literal/length symbol $symbol must be rejected") {
                KFlate.decompress(compressed, Raw())
            }

            assertEquals(FlateErrorCode.INVALID_LENGTH_LITERAL, error.code)
        }
    }
}
