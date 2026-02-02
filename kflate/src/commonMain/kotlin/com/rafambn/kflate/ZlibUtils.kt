
package com.rafambn.kflate

import com.rafambn.kflate.checksum.Adler32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError

internal fun writeZlibHeader(output: ByteArray, options: ZLIB) {
    val level = options.level
    val compressionLevelFlag = when {
        level == 0 -> 0
        level < 6 -> 1
        level == 9 -> 3
        else -> 2
    }
    output[0] = 120.toByte()
    var headerByte1 = (compressionLevelFlag shl 6) or (if (options.dictionary != null) 32 else 0)
    // FCHECK: ensure (CMF * 256 + FLG) % 31 == 0. Use % 31 on result to get 0 instead of 31
    headerByte1 = headerByte1 or ((31 - ((output[0].toInt() and 0xFF shl 8) or headerByte1) % 31) % 31)
    output[1] = headerByte1.toByte()

    options.dictionary?.let {
        val checksum = Adler32Checksum()
        checksum.update(it)
        writeBytesBE(output, 2, checksum.getChecksum())
    }
}

internal fun writeZlibStart(data: ByteArray, hasDictionary: Boolean, dictionary: ByteArray? = null): Int {
    val cmf = data[0].toInt() and 0xFF
    val flg = data[1].toInt() and 0xFF
    if ((cmf and 15) != 8 || (cmf ushr 4) > 7 || ((cmf shl 8 or flg) % 31 != 0))
        createFlateError(FlateErrorCode.INVALID_HEADER)
    val needsDictionary = (flg and 32) != 0
    if (needsDictionary != hasDictionary)
        createFlateError(FlateErrorCode.INVALID_HEADER)

    val headerSize = (if (needsDictionary) 4 else 0) + 2

    // Validate DICTID if FDICT is set
    if (needsDictionary) {
        if (data.size < headerSize) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }

        if (dictionary == null) {
            createFlateError(FlateErrorCode.INVALID_HEADER)
        }

        val storedDictId = readFourBytesBE(data, 2)
        val computedDictId = Adler32Checksum().apply {
            update(dictionary)
        }.getChecksum()

        if (storedDictId != computedDictId) {
            createFlateError(FlateErrorCode.CHECKSUM_MISMATCH)
        }
    }

    return headerSize
}
