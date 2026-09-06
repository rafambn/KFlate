package com.rafambn.kflate.compression

import com.rafambn.kflate.util.toIsoStringBytes
import kotlin.collections.iterator
import kotlin.time.Instant

private const val MAX_GZIP_TIMESTAMP = 0xFFFF_FFFFL

/**
 * Base interface for compression configuration options.
 */
sealed interface CompressionType {
    /**
     * The level of compression to use, ranging from 0-9.
     *
     * 0 will store the data without compression.
     * Levels 1-3 greedily select matches with progressively larger search budgets.
     * Levels 4-9 also look one byte ahead before accepting short matches and use progressively larger search budgets.
     * Level 1 is usually fastest, while level 9 usually produces the smallest output.
     * The default level is 6.
     *
     * Typically, binary data benefits much more from higher values than text data.
     * In both cases, higher values usually take disproportionately longer than the reduction in final size that results.
     *
     * For example, a 1 MB text file could:
     * - become 1.01 MB with level 0 in 1ms
     * - become 400 kB with level 1 in 10ms
     * - become 320 kB with level 9 in 100ms
     */
    val level: Int

    /**
     * The memory level to use, ranging from 0-12. Increasing this increases speed and compression ratio at the cost of memory.
     *
     * Note that this is exponential: while level 0 uses 4 kB, level 4 uses 64 kB, level 8 uses 1 MB, and level 12 uses 16 MB.
     * It is recommended not to lower the value below 4, since that tends to hurt performance.
     * In addition, values above 8 tend to help very little on most data and can even hurt performance.
     *
     * The default value is automatically determined based on the size of the input data.
     */
    val mem: Int

}

data class Raw(
    override val level: Int = 6,
    override val mem: Int = 8,
    val dictionary: ByteArray? = null
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Raw

        if (level != other.level) return false
        if (mem != other.mem) return false
        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + mem
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        return result
    }
}

data class Gzip(
    override val level: Int = 6,
    override val mem: Int = 8,
    val filename: String? = null,
    val mtime: Instant? = null,
    val comment: String? = null,
    val extraFields: Map<String, ByteArray>? = null,
    val includeHeaderCrc: Boolean = false
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        require(mtime == null || mtime.epochSeconds in 0..MAX_GZIP_TIMESTAMP) {
            "mtime must fit the unsigned 32-bit GZIP timestamp field"
        }
        validateHeaderText(filename, "filename")
        validateHeaderText(comment, "comment")
        extraFields?.let { fields ->
            var totalSize = 0L
            for ((key, data) in fields) {
                val keyBytes = key.toIsoStringBytes()
                require(keyBytes.size == 2) {
                    "Extra field ID must be exactly 2 ISO-8859-1 bytes, got: '$key'"
                }
                require(keyBytes[1] != 0.toByte()) {
                    "Extra field ID second byte is reserved and cannot be zero"
                }
                require(data.size <= 65_535) { "Extra field data cannot exceed 65535 bytes" }
                totalSize += 4 + data.size
                require(totalSize <= 65_535) {
                    "Total extra fields size (XLEN) cannot exceed 65535 bytes, got: $totalSize"
                }
            }
        }
    }

    private fun validateHeaderText(value: String?, fieldName: String) {
        if (value == null) return
        require('\u0000' !in value) { "$fieldName cannot contain a NUL character" }
        val bytes = value.toIsoStringBytes()
        require(bytes.size <= 65_535) {
            "$fieldName cannot exceed 65535 bytes, but was ${bytes.size} bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Gzip

        if (level != other.level) return false
        if (mem != other.mem) return false
        if (filename != other.filename) return false
        if (mtime != other.mtime) return false
        if (comment != other.comment) return false
        if (extraFields != other.extraFields) return false
        if (includeHeaderCrc != other.includeHeaderCrc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + mem
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (mtime?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (extraFields?.hashCode() ?: 0)
        result = 31 * result + includeHeaderCrc.hashCode()
        return result
    }
}

data class Zlib(
    override val level: Int = 6,
    override val mem: Int = 8,
    val dictionary: ByteArray? = null
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Zlib

        if (level != other.level) return false
        if (mem != other.mem) return false
        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + mem
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        return result
    }
}
