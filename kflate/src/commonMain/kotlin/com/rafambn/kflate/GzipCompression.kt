package com.rafambn.kflate

import com.rafambn.kflate.util.toIsoStringBytes

/** Options for RFC 1952 GZIP compression. */
data class GzipCompression(
    override val level: Int = 6,
    override val mem: Int = 8,
    override val dictionary: ByteArray? = null,
    val filename: String? = null,
    val mtime: Any? = null,
    val comment: String? = null,
    val extraFields: Map<String, ByteArray>? = null,
    val includeHeaderCrc: Boolean = false,
) : CompressionOptions {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        require(dictionary == null || dictionary.size <= 32_768) {
            "dictionary must be 32kB or smaller, but was ${dictionary?.size} bytes"
        }
        filename?.let {
            require(it.length <= 65_535) { "Filename cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        comment?.let {
            require(it.length <= 65_535) { "Comment cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        extraFields?.let { fields ->
            var totalSize = 0
            for ((key, data) in fields) {
                require(key.length == 2) { "Extra field ID must be exactly 2 bytes, got: '$key'" }
                require(data.size <= 65_535) { "Extra field data cannot exceed 65535 bytes" }
                totalSize += 4 + data.size
            }
            require(totalSize <= 65_535) {
                "Total extra fields size (XLEN) cannot exceed 65535 bytes, got: $totalSize"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GzipCompression) return false
        return level == other.level &&
            mem == other.mem &&
            dictionary.contentEquals(other.dictionary) &&
            filename == other.filename &&
            mtime == other.mtime &&
            comment == other.comment &&
            extraFields == other.extraFields &&
            includeHeaderCrc == other.includeHeaderCrc
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + mem
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (mtime?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (extraFields?.hashCode() ?: 0)
        return 31 * result + includeHeaderCrc.hashCode()
    }
}
