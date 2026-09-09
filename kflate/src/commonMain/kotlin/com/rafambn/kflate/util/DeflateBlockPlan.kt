package com.rafambn.kflate.util

import com.rafambn.kflate.huffman.HuffmanTreeResult

internal data class DeflateBlockPlan(
    val literalTree: HuffmanTreeResult,
    val distanceTree: HuffmanTreeResult,
    val codeLengthTree: HuffmanTreeResult,
    val codeLengthCodes: ShortArray,
    val numLiteralCodes: Int,
    val numDistanceCodes: Int,
    val fixedTypedLength: Int,
    val dynamicTypedLength: Int,
    val blockType: DeflateBlockType,
    val emittedTypedLength: Int,
)
