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
open class KompressBaselineBenchmarks : CorpusBenchmarkState() {
    @Setup
    open fun setup() {
        setupBenchmarkData(
            codec = KompressBenchmarkCodec,
            formats = listOf(BenchmarkFormat.Raw),
            reportPrefix = "BENCHMARK_BASELINE_CORPUS"
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return KompressBenchmarkCodec.compress(BenchmarkFormat.Raw, input)
    }

    @Benchmark
    open fun rawDeflateDecompression(): ByteArray {
        return KompressBenchmarkCodec.decompress(BenchmarkFormat.Raw, compressed(BenchmarkFormat.Raw))
    }
}
