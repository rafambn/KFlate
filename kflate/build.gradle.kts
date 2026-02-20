@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
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
    jvm()
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
        binaries.test("release") {
            optimized = true
            debuggable = false
        }
    }
    linuxArm64()
    macosX64()
    macosArm64()
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kompress.core)
            implementation(libs.file)
            implementation(libs.kotlinx.datetime)
        }
    }
}

// Performance benchmark tasks
tasks.register<Exec>("benchmarkNativeRelease") {
    group = "benchmark"
    description = "Run native release performance benchmark"
    dependsOn("linkReleaseReleaseTestLinuxX64")
    workingDir = project.rootDir
    val binaryPath = project.layout.buildDirectory.file("bin/linuxX64/releaseReleaseTest/release.kexe").get().asFile.absolutePath
    commandLine = listOf(binaryPath)
    doFirst {
        println("\n=== Running KFlate Native Release Benchmark ===\n")
    }
}

tasks.register("benchmarkJvmRelease") {
    group = "benchmark"
    description = "Run JVM release performance benchmark"
    dependsOn("jvmTest")
    doFirst {
        println("\n=== Running KFlate JVM Benchmark ===\n")
    }
}

tasks.register("benchmarkWasmJs") {
    group = "benchmark"
    description = "Run WASM/JS (Node.js) performance benchmark"
    dependsOn("cleanWasmJsNodeTest", "wasmJsNodeTest")
    doFirst {
        println("\n=== Running KFlate WASM/JS (Node.js) Benchmark ===\n")
    }
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Run all performance benchmarks (JVM + Native Release + WASM/JS)"
    dependsOn("benchmarkJvmRelease", "benchmarkNativeRelease", "benchmarkWasmJs")
    doFirst {
        println("\n" + "=".repeat(60))
        println("Running KFlate Performance Benchmarks (All Platforms)")
        println("=".repeat(60) + "\n")
    }
    doLast {
        println("\n" + "=".repeat(60))
        println("Benchmark Results")
        println("Check performance/ directory for detailed results")
        println("=".repeat(60) + "\n")
    }
}

mavenPublishing {
    coordinates(
        groupId = "com.rafambn",
        artifactId = "KFlate",
        version = "0.1.0"
    )

// Configure POM metadata for the published artifact
    pom {
        name.set("KFlate")
        description.set("KFlate is a pure Kotlin implementation of DEFLATE, GZIP, and ZLIB compression algorithms. It provides multiplatform compression/decompression with configurable compression levels and dictionary support, working seamlessly across JVM, JavaScript, WebAssembly, iOS, and other Kotlin Multiplatform targets.")
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