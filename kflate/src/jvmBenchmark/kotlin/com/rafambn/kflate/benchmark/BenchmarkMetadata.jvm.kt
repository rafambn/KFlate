package com.rafambn.kflate.benchmark

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

actual fun appendBenchmarkMetadata(line: String) {
    val path = Path.of(benchmarkMetadataPath())
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        line + "\n",
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
    )
}

actual fun benchmarkMetadataPath(): String {
    return "performance/benchmark-metadata.jsonl"
}

actual fun benchmarkPlatformName(): String {
    return "JVM"
}
