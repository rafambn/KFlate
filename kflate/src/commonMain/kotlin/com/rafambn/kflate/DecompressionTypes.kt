
package com.rafambn.kflate

/**
 * Base interface for decompression configuration options.
 */
sealed interface DecompressionType {
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

data class Raw(
    override val dictionary: ByteArray? = null
) : DecompressionType {
    init {
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Raw

        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        return dictionary?.contentHashCode() ?: 0
    }
}

data class Gzip(
    override val dictionary: ByteArray? = null
) : DecompressionType {
    init {
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Gzip

        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        return dictionary?.contentHashCode() ?: 0
    }
}

data class Zlib(
    override val dictionary: ByteArray? = null
) : DecompressionType {
    init {
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Zlib

        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        return dictionary?.contentHashCode() ?: 0
    }
}
