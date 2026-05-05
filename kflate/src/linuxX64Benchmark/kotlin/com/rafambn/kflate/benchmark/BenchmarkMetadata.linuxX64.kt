package com.rafambn.kflate.benchmark

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdir

@OptIn(ExperimentalForeignApi::class)
actual fun appendBenchmarkMetadata(line: String) {
    mkdir("performance", 493u)
    val file = fopen(benchmarkMetadataPath(), "a")
        ?: error("Could not open benchmark metadata file '${benchmarkMetadataPath()}'")
    try {
        fputs(line, file)
        fputs("\n", file)
    } finally {
        fclose(file)
    }
}

actual fun benchmarkMetadataPath(): String {
    return "performance/benchmark-metadata.jsonl"
}

actual fun benchmarkPlatformName(): String {
    return "Linux x64 Native"
}
