@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate.options

import com.rafambn.kflate.toIsoStringBytes

internal class GzipOptions(
    level: Int = 6,
    mem: Int? = null,
    dictionary: UByteArray? = null,
    val filename: String? = null,
    val mtime: Any? = null,
    val comment: String? = null,
    val extraFields: Map<String, UByteArray>? = null,
    val includeHeaderCrc: Boolean = false
) : DeflateOptions(level, mem, dictionary) {

    init {
        filename?.let {
            require(it.length <= 65535) { "Filename cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        comment?.let {
            require(it.length <= 65535) { "Comment cannot exceed 65535 bytes" }
            it.toIsoStringBytes()
        }
        extraFields?.let { fields ->
            for ((key, data) in fields) {
                require(key.length == 2) { "Extra field ID must be exactly 2 bytes, got: '$key'" }
                require(data.size <= 65535) { "Extra field data cannot exceed 65535 bytes" }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as GzipOptions

        if (filename != other.filename) return false
        if (mtime != other.mtime) return false
        if (comment != other.comment) return false
        if (extraFields != other.extraFields) return false
        if (includeHeaderCrc != other.includeHeaderCrc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (mtime?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (extraFields?.hashCode() ?: 0)
        result = 31 * result + includeHeaderCrc.hashCode()
        return result
    }
}
