package com.rafambn.kflate

/**
 * Options for raw DEFLATE decompression.
 *
 * @param dictionary optional preset dictionary, copied when this instance is created.
 * @param maxOutputSize maximum decompressed bytes, or `null` for no configured limit.
 */
class RawDecompression(
    dictionary: ByteArray? = null,
    override val maxOutputSize: Int? = null,
) : DecompressionOptions {
    internal val dictionary = dictionary?.copyOf()

    init {
        require(dictionary == null || dictionary.size <= MAX_DICTIONARY_SIZE) {
            "dictionary must be 32 KiB or smaller, but was ${dictionary?.size} bytes"
        }
        validateMaxOutputSize(maxOutputSize)
    }
}
