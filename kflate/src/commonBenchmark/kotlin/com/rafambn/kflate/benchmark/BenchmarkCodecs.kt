package com.rafambn.kflate.benchmark

import com.rafambn.kflate.KFlate
import com.rafambn.kflate.RAW
import com.rafambn.kflate.Raw
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater

interface BenchmarkCodec {
    val libraryName: String
    val formats: List<BenchmarkFormat>

    fun compress(format: BenchmarkFormat, input: ByteArray): ByteArray

    fun decompress(format: BenchmarkFormat, input: ByteArray): ByteArray
}

object KFlateBenchmarkCodec : BenchmarkCodec {
    override val libraryName: String = "KFlate"
    override val formats: List<BenchmarkFormat> = listOf(BenchmarkFormat.Raw)

    override fun compress(format: BenchmarkFormat, input: ByteArray): ByteArray {
        return when (format) {
            BenchmarkFormat.Raw -> KFlate.compress(input, RAW())
        }
    }

    override fun decompress(format: BenchmarkFormat, input: ByteArray): ByteArray {
        return when (format) {
            BenchmarkFormat.Raw -> KFlate.decompress(input, Raw())
        }
    }
}

object KompressBenchmarkCodec : BenchmarkCodec {
    override val libraryName: String = "Kompress"
    override val formats: List<BenchmarkFormat> = listOf(BenchmarkFormat.Raw)

    override fun compress(format: BenchmarkFormat, input: ByteArray): ByteArray {
        return when (format) {
            BenchmarkFormat.Raw -> Deflater.deflate(input, raw = true)
        }
    }

    override fun decompress(format: BenchmarkFormat, input: ByteArray): ByteArray {
        return when (format) {
            BenchmarkFormat.Raw -> Inflater.inflate(input, raw = true)
        }
    }
}
