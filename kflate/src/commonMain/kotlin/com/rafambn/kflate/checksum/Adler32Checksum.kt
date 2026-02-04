
package com.rafambn.kflate.checksum

internal class Adler32Checksum : ChecksumGenerator {
    private var a = 1
    private var b = 0

    override fun update(data: ByteArray) {
        update(data, 0, data.size)
    }

    override fun update(data: ByteArray, offset: Int, length: Int) {
        val finalEnd = offset + length
        var i = offset
        var localA = a
        var localB = b
        while (i < finalEnd) {
            val end = minOf(i + 3000, finalEnd)
            val limit = end - 7
            while (i < limit) {
                localA += data[i].toInt() and 0xFF; localB += localA
                localA += data[i+1].toInt() and 0xFF; localB += localA
                localA += data[i+2].toInt() and 0xFF; localB += localA
                localA += data[i+3].toInt() and 0xFF; localB += localA
                localA += data[i+4].toInt() and 0xFF; localB += localA
                localA += data[i+5].toInt() and 0xFF; localB += localA
                localA += data[i+6].toInt() and 0xFF; localB += localA
                localA += data[i+7].toInt() and 0xFF; localB += localA
                i += 8
            }
            while (i < end) {
                localA += data[i].toInt() and 0xFF
                localB += localA
                i++
            }
            localA = (localA and 0xFFFF) + 15 * (localA ushr 16)
            localB = (localB and 0xFFFF) + 15 * (localB ushr 16)
        }
        a = localA
        b = localB
    }

    override fun getChecksum(): Int {
        a %= 65521
        b %= 65521
        return (b shl 16) or a
    }
}
