package com.rafambn.kflate.decompression

/**
 * Base interface for decompression configuration options.
 */
sealed interface DecompressionType {
    val maxOutputSize: Int?
}

data class Raw(
    val dictionary: ByteArray? = null,
    override val maxOutputSize: Int? = null
) : DecompressionType {
    init {
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
        require(maxOutputSize == null || maxOutputSize >= 0) {
            "maxOutputSize must be non-negative, but was $maxOutputSize"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Raw

        if (!dictionary.contentEquals(other.dictionary)) return false
        if (maxOutputSize != other.maxOutputSize) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * (dictionary?.contentHashCode() ?: 0) + (maxOutputSize ?: 0)
    }
}

data class Gzip(
    override val maxOutputSize: Int? = null
) : DecompressionType {
    init {
        require(maxOutputSize == null || maxOutputSize >= 0) {
            "maxOutputSize must be non-negative, but was $maxOutputSize"
        }
    }
}

data class Zlib(
    val dictionary: ByteArray? = null,
    override val maxOutputSize: Int? = null
) : DecompressionType {
    init {
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
        require(maxOutputSize == null || maxOutputSize >= 0) {
            "maxOutputSize must be non-negative, but was $maxOutputSize"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Zlib

        if (!dictionary.contentEquals(other.dictionary)) return false
        if (maxOutputSize != other.maxOutputSize) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * (dictionary?.contentHashCode() ?: 0) + (maxOutputSize ?: 0)
    }
}
