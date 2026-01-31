package com.rafambn.kflate.error

enum class FlateErrorCode(val code: Int, val message: String) {
    UNEXPECTED_EOF(0, "unexpected EOF"),
    INVALID_BLOCK_TYPE(1, "invalid block type"),
    INVALID_LENGTH_LITERAL(2, "invalid length/literal"),
    INVALID_DISTANCE(3, "invalid distance"),
    INVALID_HEADER(4, "invalid header data"),
    CHECKSUM_MISMATCH(5, "ADLER32 checksum mismatch - data corruption detected"),
    ISIZE_MISMATCH(6, "ISIZE mismatch"),
    CRC_MISMATCH(7, "CRC32 checksum mismatch - data corruption detected"),
    TRAILING_GARBAGE(8, "trailing garbage after gzip member"),
    INVALID_HUFFMAN_TREE(9, "invalid Huffman tree - oversubscribed or incomplete"),
}

