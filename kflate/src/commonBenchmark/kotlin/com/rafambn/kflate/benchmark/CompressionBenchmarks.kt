package com.rafambn.kflate.benchmark

import com.rafambn.kflate.KFlate
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.SECONDS)
open class CompressionBenchmarks : RawBenchmarkState() {
    @Setup
    open fun setup() {
        setupRawBenchmark(
            libraryName = "KFlate",
            reportPrefix = "BENCHMARK_CORPUS",
            compress = { KFlate.compress(it, CompressionRaw()) },
            decompressionInput = { KFlate.compress(it, CompressionRaw()) },
            decompress = { KFlate.decompress(it, DecompressionRaw()) }
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return KFlate.compress(input, CompressionRaw())
    }

    @Benchmark
    open fun rawDeflateDecompression(): ByteArray {
        return KFlate.decompress(compressed, DecompressionRaw())
    }
}
