
package com.rafambn.kflate

import com.rafambn.kflate.util.toIsoStringBytes

/**
 * Base interface for compression configuration options.
 */
sealed interface CompressionType {
    /**
     * The level of compression to use, ranging from 0-9.
     *
     * 0 will store the data without compression.
     * 1 is fastest but compresses the worst, 9 is slowest but compresses the best.
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

    /**
     * A buffer containing common byte sequences in the input data that can be used to significantly improve compression ratios.
     *
     * Dictionaries should be 32kB or smaller and include strings or byte sequences likely to appear in the input.
     * The decompressor must supply the same dictionary as the compressor to extract the original data.
     *
     * Dictionaries only improve aggregate compression ratio when reused across multiple small inputs. They should typically not be used otherwise.
     *
     * Avoid using dictionaries with GZIP and ZIP to maximize software compatibility.
     */
    val dictionary: ByteArray?
}

data class RAW(
    override val level: Int = 6,
    override val mem: Int = 8,
    override val dictionary: ByteArray? = null
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

        other as RAW

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

data class GZIP(
    override val level: Int = 6,
    override val mem: Int = 8,
    override val dictionary: ByteArray? = null,
    val filename: String? = null,
    val mtime: Any? = null,
    val comment: String? = null,
    val extraFields: Map<String, ByteArray>? = null,
    val includeHeaderCrc: Boolean = false
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
        filename?.let {
            require(it.length <= 65535) { "Filename cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        comment?.let {
            require(it.length <= 65535) { "Comment cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        extraFields?.let { fields ->
            var totalXlen = 0
            for ((key, data) in fields) {
                require(key.length == 2) { "Extra field ID must be exactly 2 bytes, got: '$key'" }
                require(data.size <= 65535) { "Extra field data cannot exceed 65535 bytes" }
                totalXlen += 4 + data.size  // 4 bytes header (ID + length) + data
            }
            require(totalXlen <= 65535) { "Total extra fields size (XLEN) cannot exceed 65535 bytes, got: $totalXlen" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GZIP

        if (level != other.level) return false
        if (mem != other.mem) return false
        if (!dictionary.contentEquals(other.dictionary)) return false
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
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (mtime?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (extraFields?.hashCode() ?: 0)
        result = 31 * result + includeHeaderCrc.hashCode()
        return result
    }
}

data class ZLIB(
    override val level: Int = 6,
    override val mem: Int = 8,
    override val dictionary: ByteArray? = null
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

        other as ZLIB

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
