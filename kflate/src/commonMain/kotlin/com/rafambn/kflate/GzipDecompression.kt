package com.rafambn.kflate

/**
 * Options for RFC 1952 GZIP decompression.
 *
 * @param maxOutputSize maximum decompressed bytes across all members,
 * or `null` for no configured limit.
 */
class GzipDecompression(
    override val maxOutputSize: Int? = null,
) : DecompressionOptions {
    init {
        validateMaxOutputSize(maxOutputSize)
    }
}
