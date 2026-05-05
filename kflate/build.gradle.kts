@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.benchmark)
}

group = "com.rafambn"
version = "1.0.0"

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())

    androidLibrary {
        namespace = "com.rafambn"
        compileSdk = 36
        minSdk = 24
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
        nodejs {
            testTask {
                useKarma {
                    useChromiumHeadless()
                }
            }
        }
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
        nodejs {
            testTask {
                useKarma {
                    useChromiumHeadless()
                }
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    mingwX64()
    linuxX64{
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
            warmups = 5
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
    }
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Run all performance benchmarks (JVM + Native Release + WASM/JS)"
    dependsOn("jvmBenchmarkBenchmark", "linuxX64BenchmarkBenchmark", "wasmJsBenchmarkBenchmark")
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
}

mavenPublishing {
    coordinates(
        groupId = "com.rafambn",
        artifactId = "KFlate",
        version = "1.0.0"
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
