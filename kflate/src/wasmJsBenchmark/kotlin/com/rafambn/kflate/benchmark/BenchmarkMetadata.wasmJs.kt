package com.rafambn.kflate.benchmark

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("node:fs")
external object NodeFs {
    fun existsSync(path: String): Boolean

    fun mkdirSync(path: String)

    fun appendFileSync(path: String, data: String)
}

actual fun appendBenchmarkMetadata(line: String) {
    if (!NodeFs.existsSync("performance")) {
        NodeFs.mkdirSync("performance")
    }
    NodeFs.appendFileSync(benchmarkMetadataPath(), line + "\n")
}

actual fun benchmarkMetadataPath(): String {
    return "performance/benchmark-metadata.jsonl"
}

actual fun benchmarkPlatformName(): String {
    return "Wasm/JS"
}
