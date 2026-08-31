package com.rafambn.kflate

/** Options for RFC 1950 ZLIB compression. */
class ZlibCompression(
    override val level: Int = 6,
    override val mem: Int = 8,
    dictionary: ByteArray? = null,
) : CompressionOptions {
    private val storedDictionary = dictionary?.copyOf()
    override val dictionary get() = storedDictionary?.copyOf()

    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        require(dictionary == null || dictionary.size <= 32_768) {
            "dictionary must be 32kB or smaller, but was ${dictionary?.size} bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZlibCompression) return false
        return level == other.level && mem == other.mem && dictionary.contentEquals(other.dictionary)
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + mem
        return 31 * result + (dictionary?.contentHashCode() ?: 0)
    }
}
