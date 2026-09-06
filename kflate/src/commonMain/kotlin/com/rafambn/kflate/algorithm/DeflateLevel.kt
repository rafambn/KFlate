package com.rafambn.kflate.algorithm

internal class DeflateLevel(
    // Stop walking the hash chain once a match reaches this length.
    val niceLength: Int,
    // Maximum number of prior positions considered for a match.
    val chainLength: Int,
    // Look ahead one byte when the current match is shorter than this.
    val maxLazyLength: Int,
    // Cap the hash table so low levels retain their memory and cache advantage.
    val maxHashBits: Int,
    // Use bounded dynamic programming instead of greedy or one-byte lazy parsing.
    val usesCostAwareParsing: Boolean = false,
)

internal val DEFLATE_LEVELS = arrayOf(
    DeflateLevel(niceLength = 0, chainLength = 0, maxLazyLength = 0, maxHashBits = 12),
    DeflateLevel(niceLength = 8, chainLength = 4, maxLazyLength = 0, maxHashBits = 12),
    DeflateLevel(niceLength = 16, chainLength = 8, maxLazyLength = 0, maxHashBits = 13),
    DeflateLevel(niceLength = 16, chainLength = 16, maxLazyLength = 0, maxHashBits = 13),
    DeflateLevel(niceLength = 16, chainLength = 32, maxLazyLength = 4, maxHashBits = 14),
    DeflateLevel(niceLength = 32, chainLength = 32, maxLazyLength = 16, maxHashBits = 14),
    DeflateLevel(niceLength = 128, chainLength = 128, maxLazyLength = 16, maxHashBits = 15),
    DeflateLevel(niceLength = 128, chainLength = 256, maxLazyLength = 32, maxHashBits = 15),
    DeflateLevel(niceLength = 258, chainLength = 1_024, maxLazyLength = 128, maxHashBits = 16),
    DeflateLevel(
        niceLength = 258,
        chainLength = 4_096,
        maxLazyLength = 0,
        maxHashBits = 20,
        usesCostAwareParsing = true,
    ),
)
