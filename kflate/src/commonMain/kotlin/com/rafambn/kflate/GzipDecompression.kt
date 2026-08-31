package com.rafambn.kflate

/** Options for RFC 1952 GZIP decompression. */
data class GzipDecompression(
    override val dictionary: ByteArray? = null,
) : DecompressionOptions {
    init {
        require(dictionary == null || dictionary.size <= 32_768) {
            "dictionary must be 32kB or smaller, but was ${dictionary?.size} bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is GzipDecompression && dictionary.contentEquals(other.dictionary)
    }

    override fun hashCode() = dictionary?.contentHashCode() ?: 0
}
