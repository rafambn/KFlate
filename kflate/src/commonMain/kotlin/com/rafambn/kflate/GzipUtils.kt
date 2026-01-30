@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalTime::class)

package com.rafambn.kflate

import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.options.GzipOptions
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Pre-computed CRC-16 table (polynomial 0x1021, CCITT standard)
private fun buildCRC16Table(): IntArray {
    return IntArray(256).apply {
        for (i in 0 until 256) {
            var c = (i shl 8)
            repeat(8) {
                c = if ((c and 0x8000) != 0) {
                    ((c shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (c shl 1) and 0xFFFF
                }
            }
            this[i] = c
        }
    }
}

internal val CRC16_TABLE = buildCRC16Table()

internal fun computeGzipHeaderCrc16(data: UByteArray, start: Int, end: Int): Int {
    var crc = 0
    for (i in start until end) {
        crc = crc shr 8 xor CRC16_TABLE[(crc xor data[i].toInt()) and 0xFF]
    }
    return crc and 0xFFFF
}

internal fun buildExtraFields(extraFields: Map<String, UByteArray>): UByteArray {
    // Each subfield: SI1 (1 byte) + SI2 (1 byte) + LEN (2 bytes LE) + data
    val totalSize = extraFields.values.sumOf { it.size + 4 }
    val output = UByteArray(totalSize)
    var offset = 0

    for ((key, data) in extraFields) {
        require(key.length == 2) { "Extra field ID must be exactly 2 bytes, got: '$key'" }
        require(data.size <= 65535) { "Extra field data cannot exceed 65535 bytes" }

        output[offset] = key[0].code.toUByte()  // SI1
        output[offset + 1] = key[1].code.toUByte()  // SI2
        output[offset + 2] = (data.size and 0xFF).toUByte()  // LEN low byte
        output[offset + 3] = (data.size shr 8).toUByte()     // LEN high byte
        data.copyInto(output, offset + 4)
        offset += data.size + 4
    }

    return output
}

internal fun writeGzipHeader(output: UByteArray, options: GzipOptions) {
    output[0] = 31u
    output[1] = 139u
    output[2] = 8u

    // Calculate FLG byte
    var flg = 0u
    if (options.includeHeaderCrc) flg = flg or 2u
    if (options.extraFields != null) flg = flg or 4u
    if (options.filename != null) flg = flg or 8u
    if (options.comment != null) flg = flg or 16u
    output[3] = flg.toUByte()

    output[8] = when {
        options.level <= 1 -> 4u
        options.level >= 9 -> 2u
        else -> 0u
    }
    output[9] = 3u

    val mtime = options.mtime
    val timeInMillis = when (mtime) {
        is Number -> mtime.toLong()
        is String -> mtime.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
        else -> Clock.System.now().toEpochMilliseconds()
    }

    if (timeInMillis != 0L)
        writeBytes(output, 4, floor(timeInMillis / 1000.0).toLong())

    var headerOffset = 10

    // FEXTRA: Write extra subfields
    options.extraFields?.let {
        val extraData = buildExtraFields(it)
        val xlen = extraData.size
        output[headerOffset] = (xlen and 0xFF).toUByte()
        output[headerOffset + 1] = (xlen shr 8).toUByte()
        extraData.copyInto(output, headerOffset + 2)
        headerOffset += xlen + 2
    }

    // FNAME: Write filename (null-terminated)
    options.filename?.let {
        for (i in it.indices) {
            output[headerOffset++] = it[i].code.toUByte()
        }
        output[headerOffset++] = 0u
    }

    // FCOMMENT: Write comment (null-terminated)
    options.comment?.let {
        for (i in it.indices) {
            output[headerOffset++] = it[i].code.toUByte()
        }
        output[headerOffset++] = 0u
    }

    // FHCRC: Compute and write CRC-16 of header bytes 0 to headerOffset
    if (options.includeHeaderCrc) {
        val crc16 = computeGzipHeaderCrc16(output, 0, headerOffset)
        output[headerOffset] = (crc16 and 0xFF).toUByte()
        output[headerOffset + 1] = (crc16 shr 8).toUByte()
    }
}

internal fun writeGzipStart(data: UByteArray): Int {
    if (data[0].toInt() != 31 || data[1].toInt() != 139 || data[2].toInt() != 8) {
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }
    val flags = data[3].toInt()
    var headerSize = 10
    if ((flags and 4) != 0) {
        headerSize += (data[10].toInt() or (data[11].toInt() shl 8)) + 2
    }
    var remainingFlags = (flags ushr 3 and 1) + (flags ushr 4 and 1)
    while (remainingFlags > 0) {
        if (data[headerSize++].toInt() == 0) {
            remainingFlags--
        }
    }
    return headerSize + (flags and 2)
}

internal fun getGzipUncompressedSize(data: UByteArray): Long {
    val length = data.size
    return readFourBytes(data, length - 4)
}

internal fun getGzipHeaderSize(options: GzipOptions): Int {
    return 10 + if (options.filename != null) options.filename.length + 1 else 0
}
