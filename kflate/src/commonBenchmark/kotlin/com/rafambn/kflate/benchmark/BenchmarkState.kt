package com.rafambn.kflate.benchmark

import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
abstract class RawBenchmarkState {
    @Param(
        BenchmarkCorpora.SimpleText,
        BenchmarkCorpora.Text,
        BenchmarkCorpora.Model3D,
        BenchmarkCorpora.RainierBmp,
        BenchmarkCorpora.MalteseBmp,
        BenchmarkCorpora.SunriseBmp,
        BenchmarkCorpora.CompressedMvtPbf
    )
    var corpus: String = BenchmarkCorpora.SimpleText

    @Param("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    var level: Int = 6

    protected lateinit var input: ByteArray
    protected lateinit var kompressCompressed: ByteArray

    protected fun setupRawBenchmark(
        compress: (ByteArray) -> ByteArray,
        decompress: (ByteArray) -> ByteArray,
    ) {
        input = BenchmarkCorpus.load(corpus)
        kompressCompressed = compress(input)

        check(decompress(kompressCompressed).contentEquals(input)) {
            "Kompress output failed to round-trip for corpus '$corpus' at level $level"
        }
    }
}
