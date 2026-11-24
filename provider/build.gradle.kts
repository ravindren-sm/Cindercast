// :provider - plain Kotlin/JVM library with no Android dependencies.
// Holds everything that talks about the provider: secrets, redaction, request
// budget, request builders and (later) protocol clients. The Fire TV app depends
// on this module in Phase 3, so the probe and the app share one implementation.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode: runs on the Mac's JDK and is accepted by Android's D8 for :app.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // HTTP client (Step 2 onward). Only HttpProbe uses it, always behind RequestBudget.
    implementation(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.okhttp.mockwebserver)   // local fake server; tests never touch the provider
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
