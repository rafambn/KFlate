package com.rafambn.kflate.benchmark

object BenchmarkCorpora {
    const val SimpleText: String = "simpleText"
    const val Text: String = "text"
    const val Model3D: String = "model3D"
    const val RainierBmp: String = "Rainier.bmp"
    const val MalteseBmp: String = "Maltese.bmp"
    const val SunriseBmp: String = "Sunrise.bmp"
    const val CompressedMvtPbf: String = "compressed_MVT.pbf"
}

object BenchmarkCorpus {
    fun load(name: String): ByteArray {
        return when (name) {
            BenchmarkCorpora.SimpleText -> textLike(100, simpleTextSeed)
            BenchmarkCorpora.Text -> textLike(1_200_000, textSeed)
            BenchmarkCorpora.Model3D -> model3DLike(2_400)
            BenchmarkCorpora.RainierBmp -> bmpLike(5_900_000, seed = 19)
            BenchmarkCorpora.MalteseBmp -> bmpLike(15_700_000, seed = 37)
            BenchmarkCorpora.SunriseBmp -> bmpLike(49_900_000, seed = 53)
            BenchmarkCorpora.CompressedMvtPbf -> compressedLike(142_800)
            else -> error("Unknown benchmark corpus '$name'")
        }
    }

    private fun textLike(size: Int, seed: String): ByteArray {
        val seedBytes = seed.encodeToByteArray()
        return ByteArray(size) { index ->
            seedBytes[index % seedBytes.size]
        }
    }

    private fun model3DLike(size: Int): ByteArray {
        val pattern = buildString {
            appendLine("# Generated OBJ-like benchmark fixture")
            repeat(32) { index ->
                append("v ")
                append(index)
                append(".0 ")
                append(index % 7)
                append(".5 ")
                append(index % 11)
                appendLine(".25")
            }
            repeat(24) { index ->
                append("f ")
                append(index + 1)
                append(' ')
                append(index + 2)
                append(' ')
                append(index + 3)
                appendLine()
            }
        }.encodeToByteArray()

        return ByteArray(size) { index ->
            pattern[index % pattern.size]
        }
    }

    private fun bmpLike(size: Int, seed: Int): ByteArray {
        require(size >= BMP_HEADER_SIZE) { "BMP-like fixture must be at least $BMP_HEADER_SIZE bytes" }

        val bytes = ByteArray(size)
        bytes[0] = 'B'.code.toByte()
        bytes[1] = 'M'.code.toByte()
        writeLittleEndianInt(bytes, 2, size)
        writeLittleEndianInt(bytes, 10, BMP_HEADER_SIZE)
        writeLittleEndianInt(bytes, 14, 40)
        writeLittleEndianInt(bytes, 18, 2048 + seed)
        writeLittleEndianInt(bytes, 22, size / 3 / (2048 + seed))
        bytes[26] = 1
        bytes[28] = 24

        for (index in BMP_HEADER_SIZE until size) {
            val pixelOffset = index - BMP_HEADER_SIZE
            val row = pixelOffset / 4096
            val column = pixelOffset % 4096
            bytes[index] = ((row * seed + column * 3 + index / 97) and 0xff).toByte()
        }

        return bytes
    }

    private fun compressedLike(size: Int): ByteArray {
        var state = 0x1234abcd
        return ByteArray(size) {
            state = state * 1_664_525 + 1_013_904_223
            (state ushr 24).toByte()
        }
    }

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    private const val BMP_HEADER_SIZE = 54

    private const val simpleTextSeed = "hello kflate benchmark "

    private const val textSeed = "Call me Ishmael. Some years ago, never mind how long precisely, " +
        "having little or no money in my purse, I thought I would sail about a little and see the watery part of the world. "
}
