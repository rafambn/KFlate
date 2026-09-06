@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.benchmark)
    alias(libs.plugins.kover)
}

group = "com.rafambn"
version = "1.1.0"

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())

    android {
        namespace = "com.rafambn"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
    }
    jvm {
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
        }
    }
    js(IR) {
        useEsModules()
        browser {
            testTask {
                useKarma {
                    useChromiumHeadless()
                }
            }
        }
        nodejs()
    }
    wasmJs {
        useEsModules()
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
        }
        browser {
            testTask {
                useKarma {
                    useChromiumHeadless()
                }
            }
        }
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    mingwX64()
    linuxX64 {
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
        }
        binaries.test("release") {
            optimized = true
            debuggable = false
        }
    }
    linuxArm64()
    macosArm64()
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    sourceSets {
        val commonBenchmark by creating {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.kompress.core)
            }
        }
        val jvmBenchmark by getting {
            dependsOn(commonBenchmark)
        }
        val linuxX64Benchmark by getting {
            dependsOn(commonBenchmark)
        }
        val wasmJsBenchmark by getting {
            dependsOn(commonBenchmark)
            dependencies {
                implementation(npm("fflate", "0.8.2"))
            }
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.io)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

benchmark {
    targets {
        register("jvmBenchmark")
        register("linuxX64Benchmark")
        register("wasmJsBenchmark")
    }

    configurations {
        named("main") {
            warmups = 8
            iterations = 15
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
    }
}

kover {
    reports {
        filters {
            excludes {
                classes("com.rafambn.kflate.benchmark.*")
            }
        }

        verify {
            rule {
                minBound(100, CoverageUnit.INSTRUCTION)
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Run all performance benchmarks (JVM + Native Release + WASM/JS) and generate comparison tables"
    dependsOn("prepareBenchmarkAll", "jvmBenchmarkBenchmark", "linuxX64BenchmarkBenchmark", "wasmJsBenchmarkBenchmark")
    doFirst {
        println("\n" + "=".repeat(60))
        println("Running KFlate Performance Benchmarks (All Platforms)")
        println("=".repeat(60) + "\n")
    }
    doLast {
        println("\n" + "=".repeat(60))
        println("Benchmark Results")
        println("Check kflate/build/reports/benchmarks for detailed results")
        println("=".repeat(60) + "\n")
    }
    finalizedBy("benchmarkComparison")
}

val prepareBenchmarkAll by tasks.registering(Delete::class) {
    group = "benchmark"
    description = "Delete previous benchmark reports and metadata before benchmarkAll."
    delete(layout.buildDirectory.dir("reports/benchmarks/main"))
    delete(projectDir.resolve("performance/benchmark-metadata.jsonl"))
    delete(rootProject.projectDir.resolve("performance/benchmark-metadata.jsonl"))
    delete(
        rootProject.fileTree(rootProject.projectDir.resolve("build/wasm/packages")) {
            include("**/performance/benchmark-metadata.jsonl")
        }
    )
}

val benchmarkTaskNames = setOf("jvmBenchmarkBenchmark", "linuxX64BenchmarkBenchmark", "wasmJsBenchmarkBenchmark")

tasks.matching { it.name in benchmarkTaskNames }.configureEach {
        mustRunAfter(prepareBenchmarkAll)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

val collectBenchmarkMetadata by tasks.registering(Exec::class) {
    group = "benchmark"
    description = "Consolidate platform metadata into kflate/performance/benchmark-metadata.jsonl."
    mustRunAfter(benchmarkTaskNames)
    workingDir = rootProject.projectDir
    commandLine(
        "bash",
        "-lc",
        """
        set -euo pipefail
        dest="kflate/performance/benchmark-metadata.jsonl"
        tmp="${'$'}{dest}.tmp"
        mkdir -p "$(dirname "${'$'}dest")"
        : > "${'$'}tmp"
        if [ -f "${'$'}dest" ]; then cat "${'$'}dest" >> "${'$'}tmp"; fi
        if [ -f "performance/benchmark-metadata.jsonl" ]; then cat "performance/benchmark-metadata.jsonl" >> "${'$'}tmp"; fi
        for dir in build/wasm/packages kflate/build/wasm/packages; do
            if [ -d "${'$'}dir" ]; then
                find "${'$'}dir" -type f -name benchmark-metadata.jsonl -print \
                    | while IFS= read -r file; do cat "${'$'}file" >> "${'$'}tmp"; done
            fi
        done
        awk 'NF && !seen[${'$'}0]++' "${'$'}tmp" > "${'$'}dest"
        rm -f "${'$'}tmp"
        echo "Merged benchmark metadata rows: $(wc -l < "${'$'}dest") -> ${'$'}dest"
        """.trimIndent()
    )
}

tasks.register<Exec>("benchmarkComparison") {
    group = "benchmark"
    description = "Generate benchmark markdown/json comparison tables."
    mustRunAfter(benchmarkTaskNames)
    dependsOn(collectBenchmarkMetadata)
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/benchmark_comparison.py",
        "--metadata",
        "kflate/performance/benchmark-metadata.jsonl"
    )
}

mavenPublishing {
    coordinates(
        groupId = "com.rafambn",
        artifactId = "KFlate",
        version = project.version.toString(),
    )

// Configure POM metadata for the published artifact
    pom {
        name.set("KFlate")
        description.set("KFlate is a pure Kotlin implementation of DEFLATE, GZIP, and ZLIB compression algorithms. It provides multiplatform " +
                "compression/decompression with configurable compression levels and dictionary support, working seamlessly across all targets.")
        url.set("https://kflate.rafambn.com")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("rafambn")
                name.set("Rafael Mendonca")
                email.set("rafambn@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/rafambn/KFlate")
        }
    }

// Configure publishing to Maven Central
    publishToMavenCentral(automaticRelease = false)

// Enable GPG signing for all publications
    signAllPublications()

    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
            androidVariantsToPublish = listOf("release"),
        )
    )
}
