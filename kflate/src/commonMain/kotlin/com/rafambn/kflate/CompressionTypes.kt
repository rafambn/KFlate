@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

sealed interface CompressionType

data class RAW(
    val level: Int = 6,
    val mem: Int? = null,
    val dictionary: ByteArray? = null
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        mem?.let { require(it in -1..12) { "mem must be -1 (default) or in range 0..12, but was $it" } }
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
        result = 31 * result + (mem ?: 0)
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        return result
    }
}

data class GZIP(
    val level: Int = 6,
    val mem: Int? = null,
    val dictionary: ByteArray? = null,
    val filename: String? = null,
    val mtime: Any? = null,
    val comment: String? = null,
    val extraFields: Map<String, ByteArray>? = null,
    val includeHeaderCrc: Boolean = false
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        mem?.let { require(it in -1..12) { "mem must be -1 (default) or in range 0..12, but was $it" } }
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
        result = 31 * result + (mem ?: 0)
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
    val level: Int = 6,
    val mem: Int? = null,
    val dictionary: ByteArray? = null
) : CompressionType {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        mem?.let { require(it in -1..12) { "mem must be -1 (default) or in range 0..12, but was $it" } }
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
        result = 31 * result + (mem ?: 0)
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        return result
    }
}
