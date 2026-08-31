package com.rafambn.kflate.benchmark

import com.rafambn.kflate.KFlate
import com.rafambn.kflate.RawCompression
import com.rafambn.kflate.RawDecompression
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
            compress = { KFlate.compress(it, RawCompression()) },
            decompressionInput = { KFlate.compress(it, RawCompression()) },
            decompress = { KFlate.decompress(it, RawDecompression()) }
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return KFlate.compress(input, RawCompression())
    }

    @Benchmark
    open fun rawDeflateDecompression(): ByteArray {
        return KFlate.decompress(compressed, RawDecompression())
    }
}
