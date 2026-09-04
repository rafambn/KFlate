@file:OptIn(ExperimentalJsExport::class)

package com.rafambn.kflate.demo

import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.KFlate
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import kotlin.js.ExperimentalJsExport
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
    inputData = ByteArray(len) { i -> jsGet(arr, i).toByte() }
}

@JsExport
fun runCompress(format: String, level: Int): Int {
    lastError = ""
    return try {
        outputData = KFlate.compress(
            inputData,
            when (format) {
                "raw"  -> CompressionRaw(level = level)
                "gzip" -> CompressionGzip(level = level)
                else   -> CompressionZlib(level = level)
            }
        )
        outputData.size
    } catch (e: Exception) {
        lastError = e.message ?: "Unknown error"
        -1
    }
}

@JsExport
fun runDecompress(format: String): Int {
    lastError = ""
    return try {
        outputData = KFlate.decompress(
            inputData,
            when (format) {
                "raw"  -> DecompressionRaw()
                "gzip" -> DecompressionGzip()
                else   -> DecompressionZlib()
            }
        )
        outputData.size
    } catch (e: Exception) {
        lastError = e.message ?: "Unknown error"
        -1
    }
}

@JsExport
fun getOutput(): JsAny {
    val arr = newUint8Array(outputData.size)
    for (i in outputData.indices) {
        jsSet(arr, i, outputData[i].toInt() and 0xFF)
    }
    return arr
}

@JsExport
fun getLastError(): String = lastError
