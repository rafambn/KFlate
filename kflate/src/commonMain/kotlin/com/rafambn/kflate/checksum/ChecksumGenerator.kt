package com.rafambn.kflate.checksum


internal interface ChecksumGenerator {
    fun update(data: ByteArray)
    fun update(data: ByteArray, offset: Int, length: Int)
    fun getChecksum(): Int
}