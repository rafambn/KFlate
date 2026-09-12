package com.rafambn.kflate.benchmark

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
open class KompressBenchmarks : RawBenchmarkState() {
    @Setup
    open fun setup() {
        setupRawBenchmark(
            compress = { Deflater.deflate(it, raw = true, level = level) },
            decompress = { Inflater.inflate(it, raw = true) },
        )
    }

    @Benchmark
    open fun rawDeflateCompression(): ByteArray {
        return Deflater.deflate(input, raw = true, level = level)
    }

    @Benchmark
    open fun rawDeflateDecompressionFromKompress(): ByteArray {
        return Inflater.inflate(kompressCompressed, raw = true)
    }
}
