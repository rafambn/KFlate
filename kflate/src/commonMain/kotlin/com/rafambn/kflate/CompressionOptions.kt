package com.rafambn.kflate

/** Common options for a DEFLATE-based compression format. */
sealed interface CompressionOptions {
    /** Compression level from 0 to 9. */
    val level: Int

    /** Hash-table memory level from 0 to 12. */
    val mem: Int

    /** Optional preset dictionary of at most 32 KiB. */
    val dictionary: ByteArray?
}
