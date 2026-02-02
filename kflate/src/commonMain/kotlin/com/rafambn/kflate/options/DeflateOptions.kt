@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate.options

internal open class DeflateOptions(
    val level: Int = 6,
    val bufferSize: Int = 4096,
    val dictionary: UByteArray? = null
) {
    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(bufferSize >= 1024) { "bufferSize must be at least 1024, but was $bufferSize" }
        dictionary?.let {
            require(it.size <= 32768) { "dictionary must be 32kB or smaller, but was ${it.size} bytes" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DeflateOptions

        if (level != other.level) return false
        if (bufferSize != other.bufferSize) return false
        if (!dictionary.contentEquals(other.dictionary)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + bufferSize
        result = 31 * result + (dictionary?.contentHashCode() ?: 0)
        return result
    }
}

