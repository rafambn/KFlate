package com.rafambn.kflate

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

class StreamingApiTest {

    private val testFiles = listOf(
        "model3D",
        "text",
        "Rainier.bmp",
        "Maltese.bmp",
        "Sunrise.bmp",
        "simpleText",
    )

    private fun readResourceFile(fileName: String): ByteArray {
        return javaClass.classLoader.getResourceAsStream(fileName)?.readBytes()
            ?: throw IllegalArgumentException("Resource file not found: $fileName")
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }

    private fun streamCompress(data: ByteArray, type: CompressionOptions): ByteArray {
        val input = Buffer()
        input.write(data)
        val output = Buffer()
        runSuspend { KFlate.compress(type, input, output) }
        return output.readByteArray()
    }

    private fun streamDecompress(data: ByteArray, type: DecompressionOptions): ByteArray {
        val input = Buffer()
        input.write(data)
        val output = Buffer()
        runSuspend { KFlate.decompress(type, input, output) }
        return output.readByteArray()
    }

    // 1. SELF-ROUNDTRIP TESTS

    @Test
    fun testRawSelfRoundtrip() {
        for (fileName in testFiles) {
            val original = readResourceFile(fileName)
            val compressed = streamCompress(original, RawCompression())
            val decompressed = streamDecompress(compressed, RawDecompression())
            assertContentEquals(original, decompressed, "RawCompression roundtrip failed on: $fileName")
        }
    }

    @Test
    fun testGzipSelfRoundtrip() {
        for (fileName in testFiles) {
            val original = readResourceFile(fileName)
            val compressed = streamCompress(original, GzipCompression())
            val decompressed = streamDecompress(compressed, GzipDecompression())
            assertContentEquals(original, decompressed, "GzipCompression roundtrip failed on: $fileName")
        }
    }

    @Test
    fun testZlibSelfRoundtrip() {
        for (fileName in testFiles) {
            val original = readResourceFile(fileName)
            val compressed = streamCompress(original, ZlibCompression())
            val decompressed = streamDecompress(compressed, ZlibDecompression())
            assertContentEquals(original, decompressed, "ZlibCompression roundtrip failed on: $fileName")
        }
    }

    // 2. COMPRESSION LEVELS

    @Test
    fun testCompressionLevels() {
        val original = readResourceFile("text")
        for (level in 0..9) {
            val compressed = streamCompress(original, ZlibCompression(level = level))
            val decompressed = streamDecompress(compressed, ZlibDecompression())
            assertContentEquals(original, decompressed, "Level $level roundtrip failed")
        }
    }

    // 3. MEMORY LEVELS

    @Test
    fun testMemoryLevels() {
        val original = readResourceFile("text")
        for (mem in listOf(1, 4, 8, 12)) {
            val compressed = streamCompress(original, ZlibCompression(mem = mem))
            val decompressed = streamDecompress(compressed, ZlibDecompression())
            assertContentEquals(original, decompressed, "Mem $mem roundtrip failed")
        }
    }

    // 4. DICTIONARY SUPPORT

    @Test
    fun testRawDictionaryRoundtrip() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, RawCompression(dictionary = dictionary))
        val decompressed = streamDecompress(compressed, RawDecompression(dictionary = dictionary))
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testZlibDictionaryRoundtrip() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, ZlibCompression(dictionary = dictionary))
        val decompressed = streamDecompress(compressed, ZlibDecompression(dictionary = dictionary))
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testZlibDictionaryMissingOnDecompress() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, ZlibCompression(dictionary = dictionary))
        assertFailsWith<Exception> {
            streamDecompress(compressed, ZlibDecompression())
        }
    }

    // 5. BLOCKING VS STREAMING EQUIVALENCE

    @Test
    fun testBlockingCompressStreamingDecompress() {
        val original = readResourceFile("text")

        val rawCompressed = KFlate.compress(original, RawCompression())
        assertContentEquals(original, streamDecompress(rawCompressed, RawDecompression()), "RawCompression blocking->streaming failed")

        val gzipCompressed = KFlate.compress(original, GzipCompression())
        assertContentEquals(original, streamDecompress(gzipCompressed, GzipDecompression()), "GzipCompression blocking->streaming failed")

        val zlibCompressed = KFlate.compress(original, ZlibCompression())
        assertContentEquals(original, streamDecompress(zlibCompressed, ZlibDecompression()), "ZlibCompression blocking->streaming failed")
    }

    @Test
    fun testStreamingCompressBlockingDecompress() {
        val original = readResourceFile("text")

        val rawCompressed = streamCompress(original, RawCompression())
        assertContentEquals(original, KFlate.decompress(rawCompressed, RawDecompression()), "RawCompression streaming->blocking failed")

        val gzipCompressed = streamCompress(original, GzipCompression())
        assertContentEquals(original, KFlate.decompress(gzipCompressed, GzipDecompression()), "GzipCompression streaming->blocking failed")

        val zlibCompressed = streamCompress(original, ZlibCompression())
        assertContentEquals(original, KFlate.decompress(zlibCompressed, ZlibDecompression()), "ZlibCompression streaming->blocking failed")
    }

    // 6. EDGE CASES

    @Test
    fun testEmptyInput() {
        for ((compType, decType) in listOf(RawCompression() to RawDecompression(), GzipCompression() to GzipDecompression(), ZlibCompression() to ZlibDecompression())) {
            val compressed = streamCompress(ByteArray(0), compType)
            val decompressed = streamDecompress(compressed, decType)
            assertContentEquals(ByteArray(0), decompressed, "Empty input failed for ${compType::class.simpleName}")
        }
    }

    @Test
    fun testSingleByte() {
        val original = byteArrayOf(42)
        for ((compType, decType) in listOf(RawCompression() to RawDecompression(), GzipCompression() to GzipDecompression(), ZlibCompression() to ZlibDecompression())) {
            val compressed = streamCompress(original, compType)
            val decompressed = streamDecompress(compressed, decType)
            assertContentEquals(original, decompressed, "Single byte failed for ${compType::class.simpleName}")
        }
    }

    @Test
    fun testHighlyCompressible() {
        val original = ByteArray(65536)
        val compressed = streamCompress(original, ZlibCompression())
        val decompressed = streamDecompress(compressed, ZlibDecompression())
        assertContentEquals(original, decompressed)
        assert(compressed.size < original.size / 10) { "All-zeros should compress significantly" }
    }

    @Test
    fun testIncompressible() {
        val random = java.util.Random(12345)
        val original = ByteArray(65536) { random.nextInt(256).toByte() }
        val compressed = streamCompress(original, ZlibCompression())
        val decompressed = streamDecompress(compressed, ZlibDecompression())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testRepeatedPattern() {
        val pattern = "ABCDEFGH".encodeToByteArray()
        val original = ByteArray(pattern.size * 8192)
        for (i in original.indices) {
            original[i] = pattern[i % pattern.size]
        }
        val compressed = streamCompress(original, ZlibCompression())
        val decompressed = streamDecompress(compressed, ZlibDecompression())
        assertContentEquals(original, decompressed)
        assert(compressed.size < original.size / 10) { "Repeated pattern should compress significantly" }
    }

    // 7. GzipCompression OPTIONAL FIELDS (STREAMING)

    @Test
    fun testGzipOptionalFieldsStreaming() {
        val original = "gzip optional fields test data".encodeToByteArray()
        val compressed = streamCompress(
            original,
            GzipCompression(
                filename = "test.txt",
                comment = "a comment",
                extraFields = mapOf("XX" to byteArrayOf(1, 2, 3)),
                includeHeaderCrc = true
            )
        )
        val decompressed = streamDecompress(compressed, GzipDecompression())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testGzipFilenameOnlyStreaming() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, GzipCompression(filename = "simpleText.txt"))
        val decompressed = streamDecompress(compressed, GzipDecompression())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testGzipHeaderCrcOnlyStreaming() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, GzipCompression(includeHeaderCrc = true))
        val decompressed = streamDecompress(compressed, GzipDecompression())
        assertContentEquals(original, decompressed)
    }

    // 8. ERROR HANDLING (STREAMING)

    @Test
    fun testTruncatedStream() {
        val original = readResourceFile("text")
        val compressed = streamCompress(original, ZlibCompression())
        val truncated = compressed.copyOf(compressed.size / 2)
        assertFailsWith<Exception> {
            streamDecompress(truncated, ZlibDecompression())
        }
    }

    @Test
    fun testCorruptedCompressedData() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, ZlibCompression()).clone()
        compressed[compressed.size / 2] = (compressed[compressed.size / 2].toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> {
            streamDecompress(compressed, ZlibDecompression())
        }
    }

    @Test
    fun testWrongFormat() {
        val original = readResourceFile("simpleText")
        val gzipCompressed = streamCompress(original, GzipCompression())
        assertFailsWith<Exception> {
            streamDecompress(gzipCompressed, ZlibDecompression())
        }
    }
}
