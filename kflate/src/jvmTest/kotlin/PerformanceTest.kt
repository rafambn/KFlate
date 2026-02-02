
package com.rafambn.kflate

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.*
import kotlin.system.measureTimeMillis

class PerformanceTest {

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


    // FLATE TESTS
    @Test
    fun testFlateCompressPerformance() {
        println("Flate Compress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val kflateTime = measureTimeMillis {
                KFlate.compress(originalData, RAW())
            }

            val jvmTime = measureTimeMillis {
                val deflater = Deflater(6, true)
                val outputStream = ByteArrayOutputStream()
                val deflaterStream = DeflaterOutputStream(outputStream, deflater)
                deflaterStream.write(originalData)
                deflaterStream.finish()
                deflaterStream.close()
                outputStream.toByteArray()
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }

    @Test
    fun testFlateDecompressPerformance() {
        println("\nFlate Decompress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)
            val deflater = Deflater(6, true)
            val outputStream = ByteArrayOutputStream()
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)
            deflaterStream.write(originalData)
            deflaterStream.finish()
            deflaterStream.close()
            val compressedData = outputStream.toByteArray()

            val kflateTime = measureTimeMillis {
                KFlate.decompress(compressedData, Raw())
            }

            val jvmTime = measureTimeMillis {
                val inflater = Inflater(true)
                val inputStream = ByteArrayInputStream(compressedData)
                val inflaterStream = InflaterInputStream(inputStream, inflater)
                val decompressedStream = ByteArrayOutputStream()
                inflaterStream.copyTo(decompressedStream)
                inflater.end()
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }

    // GZIP TESTS
    @Test
    fun testGzipCompressPerformance() {
        println("\nGzip Compress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val kflateTime = measureTimeMillis {
                KFlate.compress(originalData, GZIP())
            }

            val jvmTime = measureTimeMillis {
                val outputStream = ByteArrayOutputStream()
                val gzipOutputStream = GZIPOutputStream(outputStream)
                gzipOutputStream.write(originalData)
                gzipOutputStream.finish()
                gzipOutputStream.close()
                outputStream.toByteArray()
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }

    @Test
    fun testGzipDecompressPerformance() {
        println("\nGzip Decompress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)
            val outputStream = ByteArrayOutputStream()
            val gzipOutputStream = GZIPOutputStream(outputStream)
            gzipOutputStream.write(originalData)
            gzipOutputStream.finish()
            gzipOutputStream.close()
            val compressedData = outputStream.toByteArray()

            val kflateTime = measureTimeMillis {
                KFlate.decompress(compressedData, Gzip())
            }

            val jvmTime = measureTimeMillis {
                val inputStream = ByteArrayInputStream(compressedData)
                val gzipInputStream = GZIPInputStream(inputStream)
                val decompressedStream = ByteArrayOutputStream()
                gzipInputStream.copyTo(decompressedStream)
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }

    // ZLIBTESTS
    @Test
    fun testZlibCompressPerformance() {
        println("\nZlib Compress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)

            val kflateTime = measureTimeMillis {
                KFlate.compress(originalData, ZLIB())
            }

            val jvmTime = measureTimeMillis {
                val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
                val outputStream = ByteArrayOutputStream()
                val deflaterStream = DeflaterOutputStream(outputStream, deflater)
                deflaterStream.write(originalData)
                deflaterStream.finish()
                deflaterStream.close()
                outputStream.toByteArray()
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }

    @Test
    fun testZlibDecompressPerformance() {
        println("\nZlib Decompress Performance")
        println("File | KFlate Time | JVM Time")
        println("--- | --- | ---")
        for (fileName in testFiles) {
            val originalData = readResourceFile(fileName)
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
            val outputStream = ByteArrayOutputStream()
            val deflaterStream = DeflaterOutputStream(outputStream, deflater)
            deflaterStream.write(originalData)
            deflaterStream.finish()
            deflaterStream.close()
            val compressedData = outputStream.toByteArray()

            val kflateTime = measureTimeMillis {
                KFlate.decompress(compressedData, Zlib())
            }

            val jvmTime = measureTimeMillis {
                val inflater = Inflater()
                val inputStream = ByteArrayInputStream(compressedData)
                val inflaterStream = InflaterInputStream(inputStream, inflater)
                val decompressedStream = ByteArrayOutputStream()
                inflaterStream.copyTo(decompressedStream)
                inflater.end()
            }
            println("$fileName | ${kflateTime}ms | ${jvmTime}ms")
        }
    }
}
