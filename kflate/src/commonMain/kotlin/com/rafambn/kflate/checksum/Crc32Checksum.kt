
package com.rafambn.kflate.checksum


internal val CRC32_TABLE = IntArray(256).apply {
    for (i in 0 until 256) {
        var c = i
        repeat(8) {
            val mask = if ((c and 1) != 0) -306674912 else 0
            c = mask xor (c ushr 1)
        }
        this[i] = c
    }
}

internal class Crc32Checksum : ChecksumGenerator {
    private var crc = -1

    override fun update(data: ByteArray) {
        update(data, 0, data.size)
    }

    override fun update(data: ByteArray, offset: Int, length: Int) {
        var localCrc = crc
        var i = offset
        val end = offset + length
        
        while (i < end - 7) {
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+1].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+2].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+3].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+4].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+5].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+6].toInt() and 0xFF)] xor (localCrc ushr 8)
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i+7].toInt() and 0xFF)] xor (localCrc ushr 8)
            i += 8
        }
        
        while (i < end) {
            localCrc = CRC32_TABLE[(localCrc and 0xFF) xor (data[i].toInt() and 0xFF)] xor (localCrc ushr 8)
            i++
        }
        crc = localCrc
    }

    override fun getChecksum(): Int {
        return crc.inv()
    }
}

