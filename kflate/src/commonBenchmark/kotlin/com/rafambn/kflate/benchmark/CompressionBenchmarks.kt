package com.rafambn.kflate.benchmark

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
open class CompressionBenchmarks : CorpusBenchmarkState() {
    @Setup
    open fun setup() {
        setupBenchmarkData(
            codec = KFlateBenchmarkCodec,
            formats = listOf(BenchmarkFormat.Raw),
            reportPrefix = "BENCHMARK_CORPUS"
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return KFlateBenchmarkCodec.compress(BenchmarkFormat.Raw, input)
    }

    @Benchmark
    open fun rawDeflateDecompression(): ByteArray {
        return KFlateBenchmarkCodec.decompress(BenchmarkFormat.Raw, compressed(BenchmarkFormat.Raw))
    }
}
