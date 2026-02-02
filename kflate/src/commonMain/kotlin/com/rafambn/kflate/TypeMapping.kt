@file:OptIn(ExperimentalUnsignedTypes::class)

package com.rafambn.kflate

import com.rafambn.kflate.options.DeflateOptions
import com.rafambn.kflate.options.GzipOptions
import com.rafambn.kflate.options.InflateOptions

internal fun ByteArray?.toUByteArray(): UByteArray? =
    this?.let { UByteArray(it.size) { i -> it[i].toUByte() } }

internal fun UByteArray?.toByteArray(): ByteArray? =
    this?.let { ByteArray(it.size) { i -> it[i].toByte() } }

internal fun CompressionType.toDeflateOptions(): Pair<DeflateOptions, Boolean> {
    return when (this) {
        is RAW -> Pair(
            DeflateOptions(
                level = level,
                mem = mem,
                dictionary = dictionary.toUByteArray()
            ),
            false
        )
        is ZLIB -> Pair(
            DeflateOptions(
                level = level,
                mem = mem,
                dictionary = dictionary.toUByteArray()
            ),
            true
        )
        is GZIP -> Pair(
            GzipOptions(
                level = level,
                mem = mem,
                dictionary = dictionary.toUByteArray(),
                filename = filename,
                mtime = mtime,
                comment = comment,
                extraFields = extraFields?.mapValues { (_, v) ->
                    UByteArray(v.size) { i -> v[i].toUByte() }
                },
                includeHeaderCrc = includeHeaderCrc
            ),
            false
        )
    }
}

internal fun DecompressionType.toInflateOptions(): InflateOptions {
    return when (this) {
        is Raw -> InflateOptions(dictionary = dictionary.toUByteArray())
        is Gzip -> InflateOptions(dictionary = dictionary.toUByteArray())
        is Zlib -> InflateOptions(dictionary = dictionary.toUByteArray())
    }
}
