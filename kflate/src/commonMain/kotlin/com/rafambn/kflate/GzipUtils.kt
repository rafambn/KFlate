@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalTime::class)

package com.rafambn.kflate

import com.rafambn.kflate.checksum.CRC32_TABLE
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.options.GzipOptions
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal fun computeGzipHeaderCrc16(data: UByteArray, start: Int, end: Int): Int {
    // RFC 1952: CRC16 is the lower 16 bits of the CRC32 over the header bytes.
    var crc = -1
    for (i in start until end) {
        crc = CRC32_TABLE[(crc and 0xFF) xor data[i].toInt()] xor (crc ushr 8)
    }
    return crc.inv() and 0xFFFF
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
        val bytes = it.toIsoStringBytes()
        for (b in bytes) {
            output[headerOffset++] = b.toUByte()
        }
        output[headerOffset++] = 0u
    }

    // FCOMMENT: Write comment (null-terminated)
    options.comment?.let {
        val bytes = it.toIsoStringBytes()
        for (b in bytes) {
            output[headerOffset++] = b.toUByte()
        }
        output[headerOffset++] = 0u
    }

    // FHCRC: Compute and write CRC-16 of header bytes 0 to headerOffset
    if (options.includeHeaderCrc) {
        val crc16 = computeGzipHeaderCrc16(output, 0, headerOffset)
        output[headerOffset] = (crc16 and 0xFF).toUByte()
        output[headerOffset + 1] = (crc16 shr 8).toUByte()
        headerOffset += 2
    }

    val calculatedSize = getGzipHeaderSize(options)
    require(headerOffset <= calculatedSize) {
        "Header size mismatch: calculated=$calculatedSize, actual=$headerOffset"
    }
}

internal fun writeGzipStart(data: UByteArray): Int {
    if (data.size < 10) {
        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
    }
    if (data[0].toInt() != 31 || data[1].toInt() != 139 || data[2].toInt() != 8) {
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }
    val flags = data[3].toInt()
    if ((flags and 0xE0) != 0) { // Check reserved bits 5, 6, 7
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }

    var headerSize = 10
    // FEXTRA
    if ((flags and 4) != 0) {
        if (headerSize + 2 > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        val xlen = (data[headerSize].toInt() and 0xFF) or ((data[headerSize + 1].toInt() and 0xFF) shl 8)
        headerSize += 2
        if (headerSize + xlen > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        headerSize += xlen
    }

    // FNAME
    if ((flags and 8) != 0) {
        while (true) {
            if (headerSize >= data.size) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }
            if (data[headerSize++].toInt() == 0) {
                break
            }
        }
    }

    // FCOMMENT
    if ((flags and 16) != 0) {
        while (true) {
            if (headerSize >= data.size) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }
            if (data[headerSize++].toInt() == 0) {
                break
            }
        }
    }

    // FHCRC
    if ((flags and 2) != 0) {
        if (headerSize + 2 > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        val computedCrc = computeGzipHeaderCrc16(data, 0, headerSize)
        val storedCrc = (data[headerSize].toInt() and 0xFF) or ((data[headerSize + 1].toInt() and 0xFF) shl 8)
        if (computedCrc != storedCrc) {
            createFlateError(FlateErrorCode.INVALID_HEADER)
        }
        headerSize += 2
    }
    return headerSize
}

internal fun getGzipUncompressedSize(data: UByteArray): Long {
    val length = data.size
    return readFourBytes(data, length - 4)
}

internal fun getGzipHeaderSize(options: GzipOptions): Int {
    var size = 10

    options.extraFields?.let { fields ->
        size += 2
        for ((_, data) in fields) {
            size += 4 + data.size
        }
    }

    options.filename?.let {
        size += it.toIsoStringBytes().size + 1
    }

    options.comment?.let {
        size += it.toIsoStringBytes().size + 1
    }

    if (options.includeHeaderCrc) {
        size += 2
    }

    return size
}
