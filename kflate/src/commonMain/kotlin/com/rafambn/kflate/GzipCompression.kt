package com.rafambn.kflate

import com.rafambn.kflate.util.toIsoStringBytes
import kotlin.time.Instant

/**
 * Options for RFC 1952 GZIP compression.
 *
 * @param level compression level from 0 to 9.
 * @param mem hash-table memory level from 0 to 12.
 * @param filename optional ISO-8859-1 filename without NUL characters.
 * @param mtime modification time; `null` writes the current time.
 * @param comment optional ISO-8859-1 comment without NUL characters.
 * @param extraFields optional two-byte ISO-8859-1 field IDs and data, copied on creation.
 * @param includeHeaderCrc whether to include the optional header CRC16.
 */
class GzipCompression(
    override val level: Int = 6,
    override val mem: Int = 8,
    val filename: String? = null,
    val mtime: Instant? = null,
    val comment: String? = null,
    extraFields: Map<String, ByteArray>? = null,
    val includeHeaderCrc: Boolean = false,
) : CompressionOptions {
    internal val extraFields = extraFields?.mapValues { (_, value) -> value.copyOf() }

    init {
        require(level in 0..9) { "level must be in range 0..9, but was $level" }
        require(mem in 0..12) { "mem must be in range 0..12, but was $mem" }
        require(mtime == null || mtime.epochSeconds in 0..MAX_GZIP_TIMESTAMP) {
            "mtime must fit the unsigned 32-bit GZIP timestamp field"
        }
        validateHeaderText(filename, "filename")
        validateHeaderText(comment, "comment")
        validateExtraFields(extraFields)
    }

    private fun validateHeaderText(value: String?, fieldName: String) {
        if (value == null) return
        require('\u0000' !in value) { "$fieldName cannot contain a NUL character" }
        val bytes = value.toIsoStringBytes()
        require(bytes.size <= MAX_GZIP_FIELD_SIZE) {
            "$fieldName cannot exceed $MAX_GZIP_FIELD_SIZE bytes, but was ${bytes.size} bytes"
        }
    }

    private fun validateExtraFields(fields: Map<String, ByteArray>?) {
        if (fields == null) return
        var totalSize = 0
        for ((key, data) in fields) {
            val keyBytes = key.toIsoStringBytes()
            require(keyBytes.size == 2) { "extra field ID must be exactly 2 ISO-8859-1 bytes, got '$key'" }
            require(data.size <= MAX_GZIP_FIELD_SIZE) {
                "extra field data cannot exceed $MAX_GZIP_FIELD_SIZE bytes"
            }
            totalSize += 4 + data.size
        }
        require(totalSize <= MAX_GZIP_FIELD_SIZE) {
            "total extra fields size cannot exceed $MAX_GZIP_FIELD_SIZE bytes, but was $totalSize"
        }
    }
}

private const val MAX_GZIP_FIELD_SIZE = 65_535
private const val MAX_GZIP_TIMESTAMP = 0xFFFF_FFFFL
