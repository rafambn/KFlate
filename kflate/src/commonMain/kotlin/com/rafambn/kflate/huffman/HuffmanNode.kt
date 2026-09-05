package com.rafambn.kflate.huffman

internal data class HuffmanNode(
    val symbol: Int,
    val frequency: Int,
    var leftChild: HuffmanNode? = null,
    var rightChild: HuffmanNode? = null,
)
