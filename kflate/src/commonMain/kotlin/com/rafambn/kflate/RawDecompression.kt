package com.rafambn.kflate

/** Options for raw DEFLATE decompression. */
class RawDecompression(
    dictionary: ByteArray? = null,
) : DecompressionOptions {
    private val storedDictionary = dictionary?.copyOf()
    override val dictionary get() = storedDictionary?.copyOf()

    init {
        require(dictionary == null || dictionary.size <= 32_768) {
            "dictionary must be 32kB or smaller, but was ${dictionary?.size} bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is RawDecompression && dictionary.contentEquals(other.dictionary)
    }

    override fun hashCode() = dictionary?.contentHashCode() ?: 0
}
