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

val benchmarkResults = rootProject.layout.projectDirectory.file("performance/latest.json")
val prepareBenchmarkResults by tasks.registering(Copy::class) {
    val snapshot = benchmarkResults.asFile
    inputs.file(benchmarkResults)
    from(benchmarkResults) { rename { "benchmark-results.json" } }
    into(layout.buildDirectory.dir("generated/benchmarkResources"))
    doFirst { require(snapshot.isFile) { "Missing performance/latest.json benchmark snapshot" } }
}

kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(prepareBenchmarkResults)
}

val optimizedOutputDir = layout.buildDirectory.dir(
    "compileSync/wasmJs/main/productionExecutable/optimized"
)

tasks.named<Copy>("wasmJsProcessResources") {
    dependsOn(":benchmark-plots:run")
    from(project(":benchmark-plots").layout.buildDirectory.dir("site")) { into("plots") }
}

tasks.register<Copy>("assembleWebDemo") {
    dependsOn("compileProductionExecutableKotlinWasmJsOptimize", ":benchmark-plots:run")
    from(prepareBenchmarkResults)
    from(optimizedOutputDir)
    from("src/wasmJsMain/resources")
    from(project(":benchmark-plots").layout.buildDirectory.dir("site")) { into("plots") }
    into(layout.buildDirectory.dir("webDemo"))
}
