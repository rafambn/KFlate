package com.rafambn.kflate.checksum


internal interface ChecksumGenerator {
    fun update(data: ByteArray)
    fun getChecksum(): Int
}
