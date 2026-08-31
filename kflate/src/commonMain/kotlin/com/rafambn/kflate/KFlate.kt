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

    fun compress(data: ByteArray, type: CompressionOptions): ByteArray {
        return when (type) {
            is RawCompression -> compressRaw(data, type)
            is GzipCompression -> compressGzip(data, type)
            is ZlibCompression -> compressZlib(data, type)
        }
    }

    fun decompress(data: ByteArray, type: DecompressionOptions): ByteArray {
        return when (type) {
            is RawDecompression -> decompressRaw(data, type)
            is GzipDecompression -> decompressGzip(data, type)
            is ZlibDecompression -> decompressZlib(data, type)
        }
    }

    fun compress(type: CompressionOptions, source: RawSource, sink: RawSink) {
        when (type) {
            is RawCompression -> compressStreamRaw(type, source, sink)
            is GzipCompression -> compressStreamGzip(type, source, sink)
            is ZlibCompression -> compressStreamZlib(type, source, sink)
        }
    }

    fun decompress(type: DecompressionOptions, source: RawSource, sink: RawSink) {
        when (type) {
            is RawDecompression -> decompressStreamRaw(type, source, sink)
            is GzipDecompression -> decompressStreamGzip(type, source, sink)
            is ZlibDecompression -> decompressStreamZlib(type, source, sink)
        }
    }
}
