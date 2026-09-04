package com.rafambn.kflate

import com.rafambn.kflate.compression.CompressionType
import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.compression.compressRaw
import com.rafambn.kflate.compression.compressGzip
import com.rafambn.kflate.compression.compressZlib
import com.rafambn.kflate.compression.compressStreamRaw
import com.rafambn.kflate.compression.compressStreamGzip
import com.rafambn.kflate.compression.compressStreamZlib
import com.rafambn.kflate.decompression.DecompressionType
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
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
            is CompressionRaw -> compressRaw(data, type)
            is CompressionGzip -> compressGzip(data, type)
            is CompressionZlib -> compressZlib(data, type)
        }
    }

    fun decompress(data: ByteArray, type: DecompressionType): ByteArray {
        return when (type) {
            is DecompressionRaw -> decompressRaw(data, type)
            is DecompressionGzip -> decompressGzip(data, type)
            is DecompressionZlib -> decompressZlib(data, type)
        }
    }

    fun compress(type: CompressionType, source: RawSource, sink: RawSink) {
        when (type) {
            is CompressionRaw -> compressStreamRaw(type, source, sink)
            is CompressionGzip -> compressStreamGzip(type, source, sink)
            is CompressionZlib -> compressStreamZlib(type, source, sink)
        }
    }

    fun decompress(type: DecompressionType, source: RawSource, sink: RawSink) {
        when (type) {
            is DecompressionRaw -> decompressStreamRaw(type, source, sink)
            is DecompressionGzip -> decompressStreamGzip(type, source, sink)
            is DecompressionZlib -> decompressStreamZlib(type, source, sink)
        }
    }
}
