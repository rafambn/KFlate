package com.rafambn.kflate

/** Common options for a DEFLATE-based decompression format. */
sealed interface DecompressionOptions {
    /** Optional preset dictionary of at most 32 KiB. */
    val dictionary: ByteArray?
}
