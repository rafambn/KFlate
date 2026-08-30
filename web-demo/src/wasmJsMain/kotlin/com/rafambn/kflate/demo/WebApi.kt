@file:OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)

package com.rafambn.kflate.demo

import com.rafambn.kflate.GzipCompression
import com.rafambn.kflate.GzipDecompression
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.RawCompression
import com.rafambn.kflate.RawDecompression
import com.rafambn.kflate.ZlibCompression
import com.rafambn.kflate.ZlibDecompression
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsExport

// Kotlin/WASM JS interop helpers — these run as inline JS, called from WASM
@JsFun("(len) => new Uint8Array(len)")
private external fun newUint8Array(len: Int): JsAny

@JsFun("(arr) => arr.length")
private external fun jsLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i]")
private external fun jsGet(arr: JsAny, i: Int): Int

@JsFun("(arr, i, v) => { arr[i] = v; }")
private external fun jsSet(arr: JsAny, i: Int, v: Int)

private var inputData = ByteArray(0)
private var outputData = ByteArray(0)
private var lastError = ""

@JsExport
fun loadInput(arr: JsAny) {
    val len = jsLength(arr)
    require(len <= MAX_INPUT_SIZE) { "Input exceeds the 64 MiB demo limit" }
    inputData = ByteArray(len) { i -> jsGet(arr, i).toByte() }
    outputData = ByteArray(0)
}

@JsExport
fun runCompress(format: String, level: Int): Int {
    lastError = ""
    return try {
        outputData = KFlate.compress(
            inputData,
            when (format) {
                "raw" -> RawCompression(level = level)
                "gzip" -> GzipCompression(level = level)
                "zlib" -> ZlibCompression(level = level)
                else -> error("Unsupported format: $format")
            }
        )
        outputData.size
    } catch (e: Exception) {
        lastError = e.message ?: "Unknown error"
        -1
    } finally {
        inputData = ByteArray(0)
    }
}

@JsExport
fun runDecompress(format: String): Int {
    lastError = ""
    return try {
        outputData = KFlate.decompress(
            inputData,
            when (format) {
                "raw" -> RawDecompression(maxOutputSize = MAX_OUTPUT_SIZE)
                "gzip" -> GzipDecompression(maxOutputSize = MAX_OUTPUT_SIZE)
                "zlib" -> ZlibDecompression(maxOutputSize = MAX_OUTPUT_SIZE)
                else -> error("Unsupported format: $format")
            }
        )
        outputData.size
    } catch (e: Exception) {
        lastError = e.message ?: "Unknown error"
        -1
    } finally {
        inputData = ByteArray(0)
    }
}

@JsExport
fun getOutput(): JsAny {
    val arr = newUint8Array(outputData.size)
    for (i in outputData.indices) {
        jsSet(arr, i, outputData[i].toInt() and 0xFF)
    }
    outputData = ByteArray(0)
    return arr
}

@JsExport
fun getLastError(): String = lastError

private const val MAX_INPUT_SIZE = 64 * 1_024 * 1_024
private const val MAX_OUTPUT_SIZE = 128 * 1_024 * 1_024
