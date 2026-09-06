package com.rafambn.kflate.benchmark

import com.rafambn.kflate.KFlate
import com.rafambn.kflate.compression.Raw
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater
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
open class KompressBaselineBenchmarks : RawBenchmarkState() {
    @Setup
    open fun setup() {
        setupRawBenchmark(
            library = BenchmarkLibrary.Kompress,
            reportPrefix = "BENCHMARK_BASELINE_CORPUS",
            compressWithKFlate = { KFlate.compress(it, Raw(BENCHMARK_COMPRESSION_LEVEL, BENCHMARK_MEMORY_LEVEL)) },
            compressWithKompress = { Deflater.deflate(it, raw = true, level = BENCHMARK_COMPRESSION_LEVEL) },
            decompress = { Inflater.inflate(it, raw = true) }
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return Deflater.deflate(input, raw = true, level = BENCHMARK_COMPRESSION_LEVEL)
    }

    @Benchmark
    open fun rawDeflateDecompressionFromKFlate(): ByteArray {
        return Inflater.inflate(kflateCompressed, raw = true)
    }

    @Benchmark
    open fun rawDeflateDecompressionFromKompress(): ByteArray {
        return Inflater.inflate(kompressCompressed, raw = true)
    }
}
