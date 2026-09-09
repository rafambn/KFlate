plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.kandy)
    implementation("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:4.11.2")
    implementation("org.jetbrains.lets-plot:lets-plot-common:4.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}

application { mainClass.set("com.rafambn.kflate.plots.MainKt") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
