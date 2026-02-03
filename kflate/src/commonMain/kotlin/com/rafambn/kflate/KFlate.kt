package com.rafambn.kflate

import com.rafambn.kflate.compression.compressRaw
import com.rafambn.kflate.compression.compressGzip
import com.rafambn.kflate.compression.compressZlib
import com.rafambn.kflate.compression.compressStreamRaw
import com.rafambn.kflate.compression.compressStreamGzip
import com.rafambn.kflate.compression.compressStreamZlib
import com.rafambn.kflate.decompression.decompressRaw
import com.rafambn.kflate.decompression.decompressGzip
import com.rafambn.kflate.decompression.decompressZlib
import com.rafambn.kflate.decompression.decompressStreamRaw
import com.rafambn.kflate.decompression.decompressStreamGzip
import com.rafambn.kflate.decompression.decompressStreamZlib
import kotlinx.io.RawSink
import kotlinx.io.RawSource

object KFlate {

    fun compress(data: ByteArray, type: CompressionType): ByteArray {
        return when (type) {
            is RAW -> compressRaw(data, type)
            is GZIP -> compressGzip(data, type)
            is ZLIB -> compressZlib(data, type)
        }
    }

    fun decompress(data: ByteArray, type: DecompressionType): ByteArray {
        return when (type) {
            is Raw -> decompressRaw(data, type)
            is Gzip -> decompressGzip(data, type)
            is Zlib -> decompressZlib(data, type)
        }
    }

    fun compress(type: CompressionType, source: RawSource, sink: RawSink) {
        when (type) {
            is RAW -> compressStreamRaw(type, source, sink)
            is GZIP -> compressStreamGzip(type, source, sink)
            is ZLIB -> compressStreamZlib(type, source, sink)
        }
    }

    fun decompress(type: DecompressionType, source: RawSource, sink: RawSink) {
        when (type) {
            is Raw -> decompressStreamRaw(type, source, sink)
            is Gzip -> decompressStreamGzip(type, source, sink)
            is Zlib -> decompressStreamZlib(type, source, sink)
        }
    }
}
