package com.rafambn.kflate

import com.rafambn.kflate.compression.CompressionType
import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.DecompressionType
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
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

    private fun streamCompress(data: ByteArray, type: CompressionType): ByteArray {
        val input = Buffer()
        input.write(data)
        val output = Buffer()
        runSuspend { KFlate.compress(type, input, output) }
        return output.readByteArray()
    }

    private fun streamDecompress(data: ByteArray, type: DecompressionType): ByteArray {
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
            val compressed = streamCompress(original, CompressionRaw())
            val decompressed = streamDecompress(compressed, DecompressionRaw())
            assertContentEquals(original, decompressed, "RAW roundtrip failed on: $fileName")
        }
    }

    @Test
    fun testGzipSelfRoundtrip() {
        for (fileName in testFiles) {
            val original = readResourceFile(fileName)
            val compressed = streamCompress(original, CompressionGzip())
            val decompressed = streamDecompress(compressed, DecompressionGzip())
            assertContentEquals(original, decompressed, "GZIP roundtrip failed on: $fileName")
        }
    }

    @Test
    fun testZlibSelfRoundtrip() {
        for (fileName in testFiles) {
            val original = readResourceFile(fileName)
            val compressed = streamCompress(original, CompressionZlib())
            val decompressed = streamDecompress(compressed, DecompressionZlib())
            assertContentEquals(original, decompressed, "ZLIB roundtrip failed on: $fileName")
        }
    }

    // 2. COMPRESSION LEVELS

    @Test
    fun testCompressionLevels() {
        val original = readResourceFile("text")
        for (level in 0..9) {
            val compressed = streamCompress(original, CompressionZlib(level = level))
            val decompressed = streamDecompress(compressed, DecompressionZlib())
            assertContentEquals(original, decompressed, "Level $level roundtrip failed")
        }
    }

    // 3. MEMORY LEVELS

    @Test
    fun testMemoryLevels() {
        val original = readResourceFile("text")
        for (mem in listOf(1, 4, 8, 12)) {
            val compressed = streamCompress(original, CompressionZlib(mem = mem))
            val decompressed = streamDecompress(compressed, DecompressionZlib())
            assertContentEquals(original, decompressed, "Mem $mem roundtrip failed")
        }
    }

    // 4. DICTIONARY SUPPORT

    @Test
    fun testRawDictionaryRoundtrip() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, CompressionRaw(dictionary = dictionary))
        val decompressed = streamDecompress(compressed, DecompressionRaw(dictionary = dictionary))
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testZlibDictionaryRoundtrip() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, CompressionZlib(dictionary = dictionary))
        val decompressed = streamDecompress(compressed, DecompressionZlib(dictionary = dictionary))
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testZlibDictionaryMissingOnDecompress() {
        val dictionary = "the quick brown fox jumps over the lazy dog".encodeToByteArray()
        val original = readResourceFile("simpleText")

        val compressed = streamCompress(original, CompressionZlib(dictionary = dictionary))
        assertFailsWith<Exception> {
            streamDecompress(compressed, DecompressionZlib())
        }
    }

    // 5. BLOCKING VS STREAMING EQUIVALENCE

    @Test
    fun testBlockingCompressStreamingDecompress() {
        val original = readResourceFile("text")

        val rawCompressed = KFlate.compress(original, CompressionRaw())
        assertContentEquals(original, streamDecompress(rawCompressed, DecompressionRaw()), "RAW blocking->streaming failed")

        val gzipCompressed = KFlate.compress(original, CompressionGzip())
        assertContentEquals(original, streamDecompress(gzipCompressed, DecompressionGzip()), "GZIP blocking->streaming failed")

        val zlibCompressed = KFlate.compress(original, CompressionZlib())
        assertContentEquals(original, streamDecompress(zlibCompressed, DecompressionZlib()), "ZLIB blocking->streaming failed")
    }

    @Test
    fun testStreamingCompressBlockingDecompress() {
        val original = readResourceFile("text")

        val rawCompressed = streamCompress(original, CompressionRaw())
        assertContentEquals(original, KFlate.decompress(rawCompressed, DecompressionRaw()), "RAW streaming->blocking failed")

        val gzipCompressed = streamCompress(original, CompressionGzip())
        assertContentEquals(original, KFlate.decompress(gzipCompressed, DecompressionGzip()), "GZIP streaming->blocking failed")

        val zlibCompressed = streamCompress(original, CompressionZlib())
        assertContentEquals(original, KFlate.decompress(zlibCompressed, DecompressionZlib()), "ZLIB streaming->blocking failed")
    }

    // 6. EDGE CASES

    @Test
    fun testEmptyInput() {
        for ((compType, decType) in listOf(CompressionRaw() to DecompressionRaw(), CompressionGzip() to DecompressionGzip(), CompressionZlib() to DecompressionZlib())) {
            val compressed = streamCompress(ByteArray(0), compType)
            val decompressed = streamDecompress(compressed, decType)
            assertContentEquals(ByteArray(0), decompressed, "Empty input failed for ${compType::class.simpleName}")
        }
    }

    @Test
    fun testSingleByte() {
        val original = byteArrayOf(42)
        for ((compType, decType) in listOf(CompressionRaw() to DecompressionRaw(), CompressionGzip() to DecompressionGzip(), CompressionZlib() to DecompressionZlib())) {
            val compressed = streamCompress(original, compType)
            val decompressed = streamDecompress(compressed, decType)
            assertContentEquals(original, decompressed, "Single byte failed for ${compType::class.simpleName}")
        }
    }

    @Test
    fun testHighlyCompressible() {
        val original = ByteArray(65536)
        val compressed = streamCompress(original, CompressionZlib())
        val decompressed = streamDecompress(compressed, DecompressionZlib())
        assertContentEquals(original, decompressed)
        assert(compressed.size < original.size / 10) { "All-zeros should compress significantly" }
    }

    @Test
    fun testIncompressible() {
        val random = java.util.Random(12345)
        val original = ByteArray(65536) { random.nextInt(256).toByte() }
        val compressed = streamCompress(original, CompressionZlib())
        val decompressed = streamDecompress(compressed, DecompressionZlib())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testRepeatedPattern() {
        val pattern = "ABCDEFGH".encodeToByteArray()
        val original = ByteArray(pattern.size * 8192)
        for (i in original.indices) {
            original[i] = pattern[i % pattern.size]
        }
        val compressed = streamCompress(original, CompressionZlib())
        val decompressed = streamDecompress(compressed, DecompressionZlib())
        assertContentEquals(original, decompressed)
        assert(compressed.size < original.size / 10) { "Repeated pattern should compress significantly" }
    }

    // 7. GZIP OPTIONAL FIELDS (STREAMING)

    @Test
    fun testGzipOptionalFieldsStreaming() {
        val original = "gzip optional fields test data".encodeToByteArray()
        val compressed = streamCompress(
            original,
            CompressionGzip(
                filename = "test.txt",
                comment = "a comment",
                extraFields = mapOf("XX" to byteArrayOf(1, 2, 3)),
                includeHeaderCrc = true
            )
        )
        val decompressed = streamDecompress(compressed, DecompressionGzip())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testGzipFilenameOnlyStreaming() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, CompressionGzip(filename = "simpleText.txt"))
        val decompressed = streamDecompress(compressed, DecompressionGzip())
        assertContentEquals(original, decompressed)
    }

    @Test
    fun testGzipHeaderCrcOnlyStreaming() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, CompressionGzip(includeHeaderCrc = true))
        val decompressed = streamDecompress(compressed, DecompressionGzip())
        assertContentEquals(original, decompressed)
    }

    // 8. ERROR HANDLING (STREAMING)

    @Test
    fun testTruncatedStream() {
        val original = readResourceFile("text")
        val compressed = streamCompress(original, CompressionZlib())
        val truncated = compressed.copyOf(compressed.size / 2)
        assertFailsWith<Exception> {
            streamDecompress(truncated, DecompressionZlib())
        }
    }

    @Test
    fun testCorruptedCompressedData() {
        val original = readResourceFile("simpleText")
        val compressed = streamCompress(original, CompressionZlib()).clone()
        compressed[compressed.size / 2] = (compressed[compressed.size / 2].toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> {
            streamDecompress(compressed, DecompressionZlib())
        }
    }

    @Test
    fun testWrongFormat() {
        val original = readResourceFile("simpleText")
        val gzipCompressed = streamCompress(original, CompressionGzip())
        assertFailsWith<Exception> {
            streamDecompress(gzipCompressed, DecompressionZlib())
        }
    }
}
