package com.rafambn.kflate

/** Common options for a DEFLATE-based compression format. */
sealed interface CompressionOptions {
    /** Compression level from 0 (store) to 9 (maximum compression). */
    val level: Int

    /**
     * Hash-table memory level from 0 to 12.
     *
     * The hash table uses approximately 8 KiB at 0, 128 KiB at 4, 2 MiB at 8,
     * and 32 MiB at 12. Compression also uses a 64 KiB history table and working buffers.
     */
    val mem: Int
}
