package com.rafambn.kflate

import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.DecompressionType
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecompressionOutputLimitTest {
    @Test
    fun blockingLimitIsEnforcedForEveryFormat() {
        val original = ByteArray(4_096)

        assertOutputLimit(
            KFlate.compress(original, CompressionRaw()),
            DecompressionRaw(maxOutputSize = 1_024),
        )
        assertOutputLimit(
            KFlate.compress(original, CompressionGzip()),
            DecompressionGzip(maxOutputSize = 1_024),
        )
        assertOutputLimit(
            KFlate.compress(original, CompressionZlib()),
            DecompressionZlib(maxOutputSize = 1_024),
        )
    }

    @Test
    fun streamingLimitIsEnforcedForEveryFormat() {
        val original = ByteArray(4_096)

        assertStreamingOutputLimit(
            KFlate.compress(original, CompressionRaw()),
            DecompressionRaw(maxOutputSize = 1_024),
            1_024,
        )
        assertStreamingOutputLimit(
            KFlate.compress(original, CompressionGzip()),
            DecompressionGzip(maxOutputSize = 1_024),
            1_024,
        )
        assertStreamingOutputLimit(
            KFlate.compress(original, CompressionZlib()),
            DecompressionZlib(maxOutputSize = 1_024),
            1_024,
        )
    }

    @Test
    fun exactAndZeroLimitsAreAccepted() {
        val original = ByteArray(4_096) { (it and 7).toByte() }
        val raw = KFlate.compress(original, CompressionRaw())
        val gzip = KFlate.compress(original, CompressionGzip())
        val zlib = KFlate.compress(original, CompressionZlib())

        assertContentEquals(
            original,
            KFlate.decompress(raw, DecompressionRaw(maxOutputSize = original.size)),
        )
        assertContentEquals(
            original,
            KFlate.decompress(gzip, DecompressionGzip(maxOutputSize = original.size)),
        )
        assertContentEquals(
            original,
            KFlate.decompress(zlib, DecompressionZlib(maxOutputSize = original.size)),
        )
        assertStreamingRoundTrip(original, raw, DecompressionRaw(maxOutputSize = original.size))
        assertStreamingRoundTrip(original, gzip, DecompressionGzip(maxOutputSize = original.size))
        assertStreamingRoundTrip(original, zlib, DecompressionZlib(maxOutputSize = original.size))

        val empty = ByteArray(0)
        val emptyRaw = KFlate.compress(empty, CompressionRaw())
        val emptyGzip = KFlate.compress(empty, CompressionGzip())
        val emptyZlib = KFlate.compress(empty, CompressionZlib())

        assertContentEquals(empty, KFlate.decompress(emptyRaw, DecompressionRaw(maxOutputSize = 0)))
        assertContentEquals(empty, KFlate.decompress(emptyGzip, DecompressionGzip(maxOutputSize = 0)))
        assertContentEquals(empty, KFlate.decompress(emptyZlib, DecompressionZlib(maxOutputSize = 0)))
        assertStreamingRoundTrip(empty, emptyRaw, DecompressionRaw(maxOutputSize = 0))
        assertStreamingRoundTrip(empty, emptyGzip, DecompressionGzip(maxOutputSize = 0))
        assertStreamingRoundTrip(empty, emptyZlib, DecompressionZlib(maxOutputSize = 0))
    }

    @Test
    fun concatenatedGzipMembersShareOneLimit() {
        val first = KFlate.compress(ByteArray(700) { 1 }, CompressionGzip())
        val second = KFlate.compress(ByteArray(700) { 2 }, CompressionGzip())
        val compressed = first + second

        assertOutputLimit(compressed, DecompressionGzip(maxOutputSize = 1_000))
        assertStreamingOutputLimit(compressed, DecompressionGzip(maxOutputSize = 1_000), 1_000)
    }

    @Test
    fun dictionariesRespectTheLimit() {
        val dictionary = "shared dictionary content".repeat(100).encodeToByteArray()
        val original = "shared dictionary content plus payload".repeat(200).encodeToByteArray()
        val raw = KFlate.compress(original, CompressionRaw(dictionary = dictionary))
        val zlib = KFlate.compress(original, CompressionZlib(dictionary = dictionary))
        val rawExact = DecompressionRaw(dictionary = dictionary, maxOutputSize = original.size)
        val zlibExact = DecompressionZlib(dictionary = dictionary, maxOutputSize = original.size)

        assertContentEquals(original, KFlate.decompress(raw, rawExact))
        assertContentEquals(original, KFlate.decompress(zlib, zlibExact))
        assertStreamingRoundTrip(original, raw, rawExact)
        assertStreamingRoundTrip(original, zlib, zlibExact)

        val rawTooSmall = DecompressionRaw(dictionary = dictionary, maxOutputSize = original.size - 1)
        val zlibTooSmall = DecompressionZlib(dictionary = dictionary, maxOutputSize = original.size - 1)
        assertOutputLimit(raw, rawTooSmall)
        assertOutputLimit(zlib, zlibTooSmall)
        assertStreamingOutputLimit(raw, rawTooSmall, original.size - 1)
        assertStreamingOutputLimit(zlib, zlibTooSmall, original.size - 1)
    }

    @Test
    fun rejectsNegativeLimits() {
        assertFailsWith<IllegalArgumentException> { DecompressionRaw(maxOutputSize = -1) }
        assertFailsWith<IllegalArgumentException> { DecompressionGzip(maxOutputSize = -1) }
        assertFailsWith<IllegalArgumentException> { DecompressionZlib(maxOutputSize = -1) }
    }

    private fun assertOutputLimit(compressed: ByteArray, type: DecompressionType) {
        val error = assertFailsWith<FlateError> {
            KFlate.decompress(compressed, type)
        }
        assertEquals(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED, error.code)
    }

    private fun assertStreamingOutputLimit(
        compressed: ByteArray,
        type: DecompressionType,
        maxOutputSize: Int,
    ) {
        val output = Buffer()
        val error = assertFailsWith<FlateError> {
            KFlate.decompress(
                type,
                Buffer().apply { write(compressed) },
                output,
            )
        }

        assertEquals(FlateErrorCode.OUTPUT_LIMIT_EXCEEDED, error.code)
        assertTrue(output.size <= maxOutputSize.toLong())
    }

    private fun assertStreamingRoundTrip(
        original: ByteArray,
        compressed: ByteArray,
        type: DecompressionType,
    ) {
        val output = Buffer()
        KFlate.decompress(
            type,
            Buffer().apply { write(compressed) },
            output,
        )

        assertContentEquals(original, output.readByteArray())
    }
}
