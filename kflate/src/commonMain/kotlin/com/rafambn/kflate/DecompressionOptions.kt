package com.rafambn.kflate

/** Common options for a DEFLATE-based decompression format. */
sealed interface DecompressionOptions {
    /** Maximum number of decompressed bytes, or `null` for no configured limit. */
    val maxOutputSize: Int?
}

internal fun validateMaxOutputSize(maxOutputSize: Int?) {
    require(maxOutputSize == null || maxOutputSize >= 0) {
        "maxOutputSize must be non-negative, but was $maxOutputSize"
    }
}
