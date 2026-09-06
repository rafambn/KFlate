package com.rafambn.kflate

import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.error.FlateError
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.format.buildExtraFields
import com.rafambn.kflate.format.getGzipHeaderSize
import com.rafambn.kflate.format.getGzipUncompressedSize
import com.rafambn.kflate.format.processSingleGzipMember
import com.rafambn.kflate.format.writeGzipHeader
import com.rafambn.kflate.format.writeGzipStart
import com.rafambn.kflate.format.writeZlibHeader
import com.rafambn.kflate.format.writeZlibStart
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.streaming.STREAM_HISTORY_SIZE
import com.rafambn.kflate.streaming.appendBytes
import com.rafambn.kflate.streaming.inflateStreamChunk
import com.rafambn.kflate.streaming.trimDeflateInput
import com.rafambn.kflate.streaming.updateHistory
import com.rafambn.kflate.util.findMaxValue
import com.rafambn.kflate.util.countCodeLengthCodes
import com.rafambn.kflate.util.readBits
import com.rafambn.kflate.util.readBits16
import com.rafambn.kflate.util.readEightBytes
import com.rafambn.kflate.util.readFourBytes
import com.rafambn.kflate.util.readFourBytesBE
import com.rafambn.kflate.util.readTwoBytes
import com.rafambn.kflate.util.shiftToNextByte
import com.rafambn.kflate.util.shouldUseStoredBlock
import com.rafambn.kflate.util.toIsoStringBytes
import com.rafambn.kflate.util.writeBits
import com.rafambn.kflate.util.writeBits16
import com.rafambn.kflate.util.writeBytes
import com.rafambn.kflate.util.writeBytesBE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class InternalHelpersCoverageTest {

    @Test
    fun bitAndByteHelpersCoverBoundaryReads() {
        assertEquals(0, findMaxValue(byteArrayOf()))
        assertEquals(255, findMaxValue(byteArrayOf(2, 1, -1, 3)))
        assertContentEquals(byteArrayOf(0, -1), "\u0000\u00ff".toIsoStringBytes())
        assertFailsWith<IllegalArgumentException> { "\u0100".toIsoStringBytes() }

        assertEquals(0, readBits(byteArrayOf(), 0, 0xff))
        assertEquals(0xab, readBits(byteArrayOf(0xab.toByte()), 0, 0xff))
        assertEquals(0x12, readBits(byteArrayOf(0x24), 1, 0xff))
        assertEquals(0, readBits16(byteArrayOf(), 0))
        assertEquals(0xab, readBits16(byteArrayOf(0xab.toByte()), 0))
        assertEquals(0xcdab, readBits16(byteArrayOf(0xab.toByte(), 0xcd.toByte()), 0))
        assertEquals(0x01cdab, readBits16(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 1), 0))

        assertEquals(2, shiftToNextByte(9))
        assertEquals(2, shiftToNextByte(9L))
        val bits = ByteArray(4)
        writeBits(bits, 3, 0x1f)
        writeBits16(bits, 9, 0x1234)
        assertTrue(bits.any { it != 0.toByte() })

        val littleEndian = ByteArray(8)
        writeBytes(littleEndian, 0, 0x1234_5678)
        writeBytes(littleEndian, 4, 0x0102_0304)
        assertEquals(0x5678, readTwoBytes(littleEndian, 0))
        assertEquals(0x1234_5678, readFourBytes(littleEndian, 0))
        assertEquals(0x0102_0304_1234_5678, readEightBytes(littleEndian, 0))

        val bigEndian = ByteArray(4)
        writeBytesBE(bigEndian, 0, 0x1234_5678)
        assertEquals(0x1234_5678, readFourBytesBE(bigEndian, 0))

        assertEquals(4, countCodeLengthCodes(ByteArray(19)))
        assertEquals(19, countCodeLengthCodes(ByteArray(19).also { it[15] = 1 }))
        assertTrue(shouldUseStoredBlock(0, 1, 1, 1))
        assertTrue(!shouldUseStoredBlock(-1, 1, 1, 1))
        assertTrue(!shouldUseStoredBlock(0, 2, 1, 2))
        assertTrue(!shouldUseStoredBlock(0, 2, 2, 1))
    }

    @Test
    fun streamingBuffersCoverBoundaryCases() {
        val original = byteArrayOf(1, 2)
        assertSame(original, appendBytes(original, byteArrayOf(3), 0))
        assertContentEquals(byteArrayOf(3), appendBytes(byteArrayOf(), byteArrayOf(3, 4), 1))
        assertContentEquals(byteArrayOf(1, 2, 3), appendBytes(original, byteArrayOf(3, 4), 1))

        val unchangedState = DeflateState(inputOffset = STREAM_HISTORY_SIZE)
        assertSame(original, trimDeflateInput(original, unchangedState))
        val tooShortState = DeflateState(inputOffset = STREAM_HISTORY_SIZE * 2)
        assertSame(original, trimDeflateInput(original, tooShortState))

        val input = ByteArray(STREAM_HISTORY_SIZE * 3) { it.toByte() }
        val state = DeflateState(
            inputOffset = STREAM_HISTORY_SIZE * 2 + 7,
            inputEndIndex = STREAM_HISTORY_SIZE * 2 + 5,
            waitIndex = STREAM_HISTORY_SIZE + 3,
        )
        val trimmed = trimDeflateInput(input, state)
        assertEquals(STREAM_HISTORY_SIZE * 2, trimmed.size)
        assertEquals(STREAM_HISTORY_SIZE + 7, state.inputOffset)
        assertEquals(STREAM_HISTORY_SIZE + 5, state.inputEndIndex)
        assertEquals(3, state.waitIndex)

        val zeroEndState = DeflateState(
            inputOffset = STREAM_HISTORY_SIZE * 2,
            inputEndIndex = 0,
        )
        trimDeflateInput(input, zeroEndState)
        assertEquals(0, zeroEndState.inputEndIndex)

        val history = byteArrayOf(1, 2)
        assertSame(history, updateHistory(history, byteArrayOf()))
        assertContentEquals(byteArrayOf(1, 2, 3), updateHistory(history, byteArrayOf(3)))
        assertContentEquals(
            ByteArray(STREAM_HISTORY_SIZE) { 4 },
            updateHistory(history, ByteArray(STREAM_HISTORY_SIZE + 1) { 4 }),
        )
    }

    @Test
    fun incompleteStreamingInflateRestoresState() {
        val state = InflateState(validationMode = 7, inputBitPosition = 3, outputOffset = 4)

        assertNull(inflateStreamChunk(byteArrayOf(), state, byteArrayOf(), false, null))
        assertEquals(3, state.inputBitPosition)
        assertEquals(4, state.outputOffset)
        assertEquals(7, state.validationMode)

        val invalidBlock = assertFailsWith<FlateError> {
            inflateStreamChunk(byteArrayOf(6), InflateState(), byteArrayOf(), false, null)
        }
        assertEquals(FlateErrorCode.INVALID_BLOCK_TYPE, invalidBlock.code)
        val exhausted = assertFailsWith<FlateError> {
            inflateStreamChunk(byteArrayOf(), InflateState(validationMode = 2), byteArrayOf(), true, null)
        }
        assertEquals(FlateErrorCode.UNEXPECTED_EOF, exhausted.code)
    }

    @Test
    fun gzipHeaderParsingCoversEveryOptionalField() {
        val options = CompressionGzip(
            level = 9,
            filename = "file",
            mtime = Instant.fromEpochSeconds(0),
            comment = "comment",
            extraFields = mapOf("AB" to byteArrayOf(1, 2)),
            includeHeaderCrc = true,
        )
        val header = ByteArray(getGzipHeaderSize(options))
        writeGzipHeader(header, options)
        assertEquals(header.size, writeGzipStart(header))

        assertFailsWith<FlateError> { writeGzipStart(ByteArray(9)) }
        for (index in 0..2) {
            val invalid = ByteArray(10)
            invalid[0] = 31
            invalid[1] = 139.toByte()
            invalid[2] = 8
            invalid[index] = 0
            assertFailsWith<FlateError> { writeGzipStart(invalid) }
        }

        val missingExtraLength = header.copyOf(11).also { it[3] = 4 }
        assertFailsWith<FlateError> { writeGzipStart(missingExtraLength) }
        val missingHeaderCrc = header.copyOf(11).also { it[3] = 2 }
        assertFailsWith<FlateError> { writeGzipStart(missingHeaderCrc) }

        val badCrc = header.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertFailsWith<FlateError> { writeGzipStart(badCrc) }
        assertTrue(buildExtraFields(mapOf("AB" to byteArrayOf(1))).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { buildExtraFields(mapOf("A" to byteArrayOf())) }
        assertFailsWith<IllegalArgumentException> { buildExtraFields(mapOf("A\u0000" to byteArrayOf())) }
        assertFailsWith<IllegalArgumentException> { buildExtraFields(mapOf("AB" to ByteArray(65_536))) }
        assertFailsWith<IllegalArgumentException> {
            buildExtraFields(mapOf("AB" to ByteArray(40_000), "CD" to ByteArray(40_000)))
        }
    }

    @Test
    fun gzipMemberHelpersCoverDefaultsAndTrailerReads() {
        val original = "member".encodeToByteArray()
        val compressed = KFlate.compress(original, CompressionGzip())
        val member = processSingleGzipMember(compressed, 0)

        assertContentEquals(original, member.decompressed)
        assertEquals(compressed.size, member.bytesConsumed)
        assertEquals(original.size.toLong(), getGzipUncompressedSize(compressed))
        assertFailsWith<FlateError> { processSingleGzipMember(ByteArray(19), 0) }
        assertFailsWith<FlateError> {
            KFlate.decompress(compressed.copyOf(compressed.size - 7), DecompressionGzip())
        }
    }

    @Test
    fun zlibHeadersCoverCompressionFlagsAndValidation() {
        for (level in listOf(0, 1, 6, 9)) {
            val header = ByteArray(2)
            writeZlibHeader(header, com.rafambn.kflate.compression.Zlib(level = level))
            assertEquals(2, writeZlibStart(header, false))
        }

        assertFailsWith<FlateError> { writeZlibStart(byteArrayOf(), false) }
        assertFailsWith<FlateError> { writeZlibStart(byteArrayOf(0, 0), false) }
        assertFailsWith<FlateError> { writeZlibStart(byteArrayOf(0x88.toByte(), 0), false) }
        assertFailsWith<FlateError> { writeZlibStart(byteArrayOf(0x78, 0), false) }

        val dictionary = "dictionary".encodeToByteArray()
        val header = ByteArray(6)
        writeZlibHeader(header, com.rafambn.kflate.compression.Zlib(dictionary = dictionary))
        assertEquals(6, writeZlibStart(header, true, dictionary))
        assertFailsWith<FlateError> { writeZlibStart(header.copyOf(5), true, dictionary) }
        assertFailsWith<FlateError> { writeZlibStart(header, true, null) }
        assertFailsWith<FlateError> { writeZlibStart(header, true, byteArrayOf(1)) }
        assertFailsWith<FlateError> { writeZlibStart(header, false) }
    }

    @Test
    fun exposesEveryErrorCodeNumber() {
        assertEquals((0..11).toList(), FlateErrorCode.entries.map { it.code })
    }
}
