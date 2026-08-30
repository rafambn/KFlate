package com.rafambn.kflate

/**
 * Options for raw DEFLATE compression.
 *
 * @param level compression level from 0 to 9.
 * @param mem hash-table memory level from 0 to 12.
 * @param dictionary optional preset dictionary, copied when this instance is created.
 */
class RawCompression(
    override val level: Int = 6,
    override val mem: Int = 8,
    dictionary: ByteArray? = null,
) : CompressionOptions {
    internal val dictionary = dictionary?.copyOf()

    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        require(dictionary == null || dictionary.size <= MAX_DICTIONARY_SIZE) {
            "dictionary must be 32 KiB or smaller, but was ${dictionary?.size} bytes"
        }
    }
}
