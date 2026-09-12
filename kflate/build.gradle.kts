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
        nodejs()
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
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    mingwX64()
    linuxX64 {
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
        commonMain.dependencies {
            implementation(libs.kotlinx.io)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

kover {
    reports {
        verify {
            rule {
                minBound(100, CoverageUnit.INSTRUCTION)
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
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
