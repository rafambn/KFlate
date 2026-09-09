package com.rafambn.kflate.benchmark

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

object BenchmarkCorpus {
    fun load(name: String): ByteArray {
        require(name in BenchmarkCorpora.all) { "Unknown benchmark corpus '$name'" }
        val path = Path(corpusDirectory, name)
        return SystemFileSystem.source(path).buffered().use { source ->
            source.readByteArray()
        }
    }

    private val corpusDirectory: Path by lazy {
        var directory: Path? = SystemFileSystem.resolve(Path("."))
        while (directory != null) {
            val repositoryPath = Path(directory, "kflate", "src", "jvmTest", "resources")
            if (containsCorpus(repositoryPath)) {
                return@lazy repositoryPath
            }

            val modulePath = Path(directory, "src", "jvmTest", "resources")
            if (containsCorpus(modulePath)) {
                return@lazy modulePath
            }
            directory = directory.parent
        }
        error("Could not find benchmark fixtures from the current working directory")
    }

    private fun containsCorpus(directory: Path): Boolean {
        return BenchmarkCorpora.all.all { name ->
            SystemFileSystem.exists(Path(directory, name))
        }
    }
}
