// :probe - command-line driver for the Phase 2 provider probe (runs on the Mac).
// Usage: ./gradlew :probe:run --args="<step> [--dry-run]"
// Step 0 build: dry runs only. There is no HTTP client in this module or in
// :provider yet, so it cannot contact the provider.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("io.github.ravindren_sm.openstream.probe.MainKt")
}

dependencies {
    implementation(project(":provider"))
}

// Run from the repo root so probe_output/ lands in the (gitignored) root folder,
// and pass through the secrets path variable if the user set one.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    System.getenv("FIRETV_IPTV_SECRETS")?.let { environment("FIRETV_IPTV_SECRETS", it) }
}
