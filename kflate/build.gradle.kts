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
            include(".*CompressionBenchmarks.*")
            warmups = 8
            iterations = 15
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
            advanced("jvmForks", 3)
        }
        register("smoke") {
            include(".*CompressionBenchmarks.*")
            warmups = 1
            iterations = 1
            iterationTime = 1
            iterationTimeUnit = "ms"
            param("corpus", "simpleText")
            param("level", "6")
            reportFormat = "json"
            advanced("jvmForks", 1)
        }
        register("baseline") {
            include(".*KompressBaselineBenchmarks.*")
            warmups = 8
            iterations = 15
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
            advanced("jvmForks", 3)
        }
        register("baselineSmoke") {
            include(".*KompressBaselineBenchmarks.*")
            warmups = 1
            iterations = 1
            iterationTime = 1
            iterationTimeUnit = "ms"
            param("corpus", "simpleText")
            param("level", "6")
            reportFormat = "json"
            advanced("jvmForks", 1)
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

tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

// The script invokes one platform task at a time and archives it before the next starts.
tasks.register<Exec>("benchmarkAll") {
    group = "benchmark"
    description = "Measure KFlate against the saved Kompress baseline on all platforms"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/benchmark_suite.py", "run")
    notCompatibleWithConfigurationCache("Invokes the platform benchmark builds sequentially")
}

tasks.register<Exec>("benchmarkBaseline") {
    group = "benchmark"
    description = "Capture missing Kompress baselines and fixtures on all platforms"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/benchmark_suite.py", "baseline")
    notCompatibleWithConfigurationCache("Invokes the platform benchmark builds sequentially")
}

tasks.register<Exec>("benchmarkComparison") {
    group = "benchmark"
    description = "Rebuild the retained benchmark history without running measurements"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/benchmark_suite.py", "report")
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
