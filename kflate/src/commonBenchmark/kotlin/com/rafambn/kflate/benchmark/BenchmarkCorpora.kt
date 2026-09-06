package com.rafambn.kflate.benchmark

object BenchmarkCorpora {
    const val SimpleText: String = "simpleText"
    const val Text: String = "text"
    const val Model3D: String = "model3D"
    const val RainierBmp: String = "Rainier.bmp"
    const val MalteseBmp: String = "Maltese.bmp"
    const val SunriseBmp: String = "Sunrise.bmp"
    const val CompressedMvtPbf: String = "compressed_MVT.pbf"

    val all: List<String> = listOf(
        SimpleText,
        Text,
        Model3D,
        RainierBmp,
        MalteseBmp,
        SunriseBmp,
        CompressedMvtPbf
    )
}
