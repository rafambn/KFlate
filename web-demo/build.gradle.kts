@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    wasmJs {
        outputModuleName = "kflate-demo"
        binaries.executable()
        browser()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":kflate"))
        }
    }
}

val optimizedOutputDir = layout.buildDirectory.dir(
    "compileSync/wasmJs/main/productionExecutable/optimized"
)

tasks.register<Copy>("assembleWebDemo") {
    dependsOn("compileProductionExecutableKotlinWasmJsOptimize")
    from(optimizedOutputDir)
    from("src/wasmJsMain/resources")
    into(layout.buildDirectory.dir("webDemo"))
}

