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

/** Blocking and streaming DEFLATE, GZIP, and ZLIB operations. */
object KFlate {

    /** Compresses [data] with the selected format. */
    fun compress(data: ByteArray, options: CompressionOptions): ByteArray {
        return when (options) {
            is RawCompression -> compressRaw(data, options)
            is GzipCompression -> compressGzip(data, options)
            is ZlibCompression -> compressZlib(data, options)
        }
    }

    /**
     * Decompresses [data] with the selected format.
     *
     * @throws com.rafambn.kflate.error.FlateError if the input is invalid, truncated,
     * or exceeds the configured output limit.
     */
    fun decompress(data: ByteArray, options: DecompressionOptions): ByteArray {
        return when (options) {
            is RawDecompression -> decompressRaw(data, options)
            is GzipDecompression -> decompressGzip(data, options)
            is ZlibDecompression -> decompressZlib(data, options)
        }
    }

    /**
     * Reads [source], writes compressed bytes to [sink], and flushes the buffered sink.
     * The caller retains ownership of both resources.
     */
    fun compress(options: CompressionOptions, source: RawSource, sink: RawSink) {
        when (options) {
            is RawCompression -> compressStreamRaw(options, source, sink)
            is GzipCompression -> compressStreamGzip(options, source, sink)
            is ZlibCompression -> compressStreamZlib(options, source, sink)
        }
    }

    /**
     * Reads [source], writes decompressed bytes to [sink], and flushes the buffered sink.
     * The caller retains ownership of both resources. A failure can leave partial output in [sink].
     *
     * @throws com.rafambn.kflate.error.FlateError if the input is invalid, truncated,
     * or exceeds the configured output limit.
     */
    fun decompress(options: DecompressionOptions, source: RawSource, sink: RawSink) {
        when (options) {
            is RawDecompression -> decompressStreamRaw(options, source, sink)
            is GzipDecompression -> decompressStreamGzip(options, source, sink)
            is ZlibDecompression -> decompressStreamZlib(options, source, sink)
        }
    }
}
