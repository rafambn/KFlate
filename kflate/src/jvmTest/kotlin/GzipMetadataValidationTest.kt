package com.rafambn.kflate

import com.rafambn.kflate.compression.Gzip
import com.rafambn.kflate.format.buildExtraFields
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GzipMetadataValidationTest {
    @Test
    fun rejectsNulTerminatedMetadataFromCallers() {
        assertFailsWith<IllegalArgumentException> {
            Gzip(filename = "file\u0000name")
        }
        assertFailsWith<IllegalArgumentException> {
            Gzip(comment = "comment\u0000suffix")
        }
    }

    @Test
    fun rejectsInvalidExtraFieldIds() {
        assertFailsWith<IllegalArgumentException> {
            Gzip(extraFields = mapOf("A€" to byteArrayOf(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            Gzip(extraFields = mapOf("A\u0000" to byteArrayOf(1)))
        }
    }

    @Test
    fun rejectsExtraFieldSizeOverflow() {
        val data = ByteArray(65_535)
        val fields = HashMap<String, ByteArray>(32_767)
        for (first in 0..255) {
            for (second in 1..255) {
                fields[String(charArrayOf(first.toChar(), second.toChar()))] = data
                if (fields.size == 32_767) break
            }
            if (fields.size == 32_767) break
        }

        assertFailsWith<IllegalArgumentException> {
            Gzip(extraFields = fields)
        }
        assertFailsWith<IllegalArgumentException> {
            buildExtraFields(fields)
        }
    }

    @Test
    fun validatesAndWritesUnsignedTimestamp() {
        assertFailsWith<IllegalArgumentException> {
            Gzip(mtime = Instant.fromEpochSeconds(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            Gzip(mtime = Instant.fromEpochSeconds(0x1_0000_0000L))
        }

        val compressed = KFlate.compress(
            byteArrayOf(1),
            Gzip(mtime = Instant.fromEpochSeconds(1)),
        )

        assertEquals(1, compressed[4].toInt())
        assertEquals(0, compressed[5].toInt())
        assertEquals(0, compressed[6].toInt())
        assertEquals(0, compressed[7].toInt())
    }

    @Test
    fun writesTimestampBoundaries() {
        assertMtimeBytes(Instant.fromEpochSeconds(0), byteArrayOf(0, 0, 0, 0))
        assertMtimeBytes(Instant.fromEpochSeconds(1, 999_999_999), byteArrayOf(1, 0, 0, 0))
        assertMtimeBytes(Instant.fromEpochSeconds(0xFFFF_FFFFL), byteArrayOf(-1, -1, -1, -1))
    }

    @Test
    fun writesUnsignedTimestampWhenStreaming() {
        val source = Buffer().apply { write(byteArrayOf(1)) }
        val sink = Buffer()

        KFlate.compress(Gzip(mtime = Instant.fromEpochSeconds(0xFFFF_FFFFL)), source, sink)

        assertContentEquals(byteArrayOf(-1, -1, -1, -1), sink.readByteArray().copyOfRange(4, 8))
    }

    private fun assertMtimeBytes(mtime: Instant, expected: ByteArray) {
        val compressed = KFlate.compress(byteArrayOf(1), Gzip(mtime = mtime))

        assertContentEquals(expected, compressed.copyOfRange(4, 8))
    }
}
