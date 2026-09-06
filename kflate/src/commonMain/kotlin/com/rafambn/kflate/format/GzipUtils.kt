@file:OptIn(ExperimentalTime::class)

package com.rafambn.kflate.format

import com.rafambn.kflate.compression.Gzip
import com.rafambn.kflate.algorithm.inflate
import com.rafambn.kflate.checksum.Crc32Checksum
import com.rafambn.kflate.error.FlateErrorCode
import com.rafambn.kflate.error.createFlateError
import com.rafambn.kflate.streaming.InflateState
import com.rafambn.kflate.util.readFourBytes
import com.rafambn.kflate.util.toIsoStringBytes
import com.rafambn.kflate.util.writeBytes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal fun computeGzipHeaderCrc16(data: ByteArray, start: Int, end: Int): Int {
    // RFC 1952: CRC16 is the lower 16 bits of the CRC32 over the header bytes.
    val crc = Crc32Checksum()
    crc.update(data, start, end - start)
    return crc.getChecksum() and 0xFFFF
}

internal fun buildExtraFields(extraFields: Map<String, ByteArray>): ByteArray {
    val totalSize = getGzipExtraFieldsSize(extraFields)
    val output = ByteArray(totalSize)
    var offset = 0

    for ((key, data) in extraFields) {
        val keyBytes = key.toIsoStringBytes()
        output[offset] = keyBytes[0]  // SI1
        output[offset + 1] = keyBytes[1]  // SI2
        output[offset + 2] = (data.size and 0xFF).toByte()  // LEN low byte
        output[offset + 3] = (data.size shr 8).toByte()     // LEN high byte
        data.copyInto(output, offset + 4)
        offset += data.size + 4
    }

    return output
}

private fun getGzipExtraFieldsSize(extraFields: Map<String, ByteArray>): Int {
    var totalSize = 0L
    for ((key, data) in extraFields) {
        val keyBytes = key.toIsoStringBytes()
        require(keyBytes.size == 2) { "Extra field ID must be exactly 2 bytes, got: '$key'" }
        require(keyBytes[1] != 0.toByte()) {
            "Extra field ID second byte is reserved and cannot be zero"
        }
        require(data.size <= 65_535) { "Extra field data cannot exceed 65535 bytes" }
        totalSize += 4 + data.size
        require(totalSize <= 65_535) {
            "Total extra field size ($totalSize bytes) exceeds maximum XLEN of 65535 bytes"
        }
    }
    return totalSize.toInt()
}

internal fun writeGzipHeader(output: ByteArray, options: Gzip) {
    output[0] = 31
    output[1] = -117 // 139 as signed byte
    output[2] = 8

    // Calculate FLG byte
    var flg = 0
    if (options.includeHeaderCrc) flg = flg or 2
    if (options.extraFields != null) flg = flg or 4
    if (options.filename != null) flg = flg or 8
    if (options.comment != null) flg = flg or 16
    output[3] = flg.toByte()

    output[8] = when {
        options.level <= 1 -> 4
        options.level >= 9 -> 2
        else -> 0
    }.toByte()
    output[9] = -1 // 255 as signed byte

    val timestamp = options.mtime?.epochSeconds ?: Clock.System.now().epochSeconds
    if (timestamp != 0L) {
        writeBytes(output, 4, timestamp)
    }

    var headerOffset = 10

    // FEXTRA: Write extra subfields
    options.extraFields?.let {
        val extraData = buildExtraFields(it)
        val xlen = extraData.size
        output[headerOffset] = (xlen and 0xFF).toByte()
        output[headerOffset + 1] = (xlen shr 8).toByte()
        extraData.copyInto(output, headerOffset + 2)
        headerOffset += xlen + 2
    }

    // FNAME: Write filename (null-terminated)
    options.filename?.let {
        val bytes = it.toIsoStringBytes()
        for (b in bytes) {
            output[headerOffset++] = b
        }
        output[headerOffset++] = 0
    }

    // FCOMMENT: Write comment (null-terminated)
    options.comment?.let {
        val bytes = it.toIsoStringBytes()
        for (b in bytes) {
            output[headerOffset++] = b
        }
        output[headerOffset++] = 0
    }

    // FHCRC: Compute and write CRC-16 of header bytes 0 to headerOffset
    if (options.includeHeaderCrc) {
        val crc16 = computeGzipHeaderCrc16(output, 0, headerOffset)
        output[headerOffset] = (crc16 and 0xFF).toByte()
        output[headerOffset + 1] = (crc16 shr 8).toByte()
        headerOffset += 2
    }

}

internal fun writeGzipStart(data: ByteArray, startOffset: Int = 0): Int {
    if (startOffset + 10 > data.size) {
        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
    }
    if ((data[startOffset].toInt() and 0xFF) != 31 || (data[startOffset + 1].toInt() and 0xFF) != 139 || (data[startOffset + 2].toInt() and 0xFF) != 8) {
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }
    val flags = data[startOffset + 3].toInt() and 0xFF
    if ((flags and 0xE0) != 0) { // Check reserved bits 5, 6, 7
        createFlateError(FlateErrorCode.INVALID_HEADER)
    }

    var headerSize = 10
    // FEXTRA
    if ((flags and 4) != 0) {
        if (startOffset + headerSize + 2 > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        val xlen = (data[startOffset + headerSize].toInt() and 0xFF) or ((data[startOffset + headerSize + 1].toInt() and 0xFF) shl 8)
        headerSize += 2
        if (startOffset + headerSize + xlen > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        headerSize += xlen
    }

    // FNAME
    if ((flags and 8) != 0) {
        while (true) {
            if (startOffset + headerSize >= data.size) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }
            if (data[startOffset + headerSize++].toInt() == 0) {
                break
            }
        }
    }

    // FCOMMENT
    if ((flags and 16) != 0) {
        while (true) {
            if (startOffset + headerSize >= data.size) {
                createFlateError(FlateErrorCode.UNEXPECTED_EOF)
            }
            if (data[startOffset + headerSize++].toInt() == 0) {
                break
            }
        }
    }

    // FHCRC
    if ((flags and 2) != 0) {
        if (startOffset + headerSize + 2 > data.size) {
            createFlateError(FlateErrorCode.UNEXPECTED_EOF)
        }
        val computedCrc = computeGzipHeaderCrc16(data, startOffset, startOffset + headerSize)
        val storedCrc = (data[startOffset + headerSize].toInt() and 0xFF) or ((data[startOffset + headerSize + 1].toInt() and 0xFF) shl 8)
        if (computedCrc != storedCrc) {
            createFlateError(FlateErrorCode.INVALID_HEADER)
        }
        headerSize += 2
    }
    return headerSize
}

internal fun getGzipUncompressedSize(data: ByteArray): Long {
    val length = data.size
    return readFourBytes(data, length - 4)
}

internal fun getGzipHeaderSize(options: Gzip): Int {
    var size = 10

    options.extraFields?.let { fields ->
        size += 2 + getGzipExtraFieldsSize(fields)
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

internal fun processSingleGzipMember(
    data: ByteArray,
    startOffset: Int,
    maxOutputSize: Int? = null,
): GzipMemberResult {
    // Validate minimum size: 10 bytes header + at least 2 bytes compressed data + 8 bytes trailer (CRC32 + ISIZE)
    if (startOffset + 20 > data.size) {
        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
    }

    // Parse header
    val headerEndPos = writeGzipStart(data, startOffset)
    val compressedDataStart = startOffset + headerEndPos

    // Inflate with state tracking
    val inflateState = InflateState(validationMode = 2)
    inflateState.inputBitPosition = compressedDataStart * 8
    val decompressed = inflate(data, inflateState, maxOutputSize = maxOutputSize)

    // Calculate bytes consumed by inflate
    val bitsConsumed = inflateState.inputBitPosition - (compressedDataStart * 8)
    val bytesConsumedByInflate = (bitsConsumed + 7) / 8

    // Validate trailer
    val trailerStart = compressedDataStart + bytesConsumedByInflate
    if (trailerStart + 8 > data.size) {
        createFlateError(FlateErrorCode.UNEXPECTED_EOF)
    }

    // Validate CRC32
    val storedCrc32 = readFourBytes(data, trailerStart).toInt()
    val crc = Crc32Checksum()
    crc.update(decompressed)
    if (crc.getChecksum() != storedCrc32) {
        createFlateError(FlateErrorCode.CRC_MISMATCH)
    }

    // Validate ISIZE
    val storedISize = readFourBytes(data, trailerStart + 4)
    if ((decompressed.size.toLong() and 0xFFFFFFFFL) != storedISize) {
        createFlateError(FlateErrorCode.ISIZE_MISMATCH)
    }

    val totalBytesConsumed = trailerStart + 8 - startOffset
    return GzipMemberResult(decompressed, totalBytesConsumed)
}
