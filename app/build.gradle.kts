// App module. Package id is a new one, not StbEmu's, so both apps can coexist.
// Target device: Fire TV AFTMA08C15, Android 11 (API 30), 32-bit ABI only.

plugins {
    alias(libs.plugins.android.application)
    // AGP 8.x does not compile Kotlin by itself; without this plugin the
    // Kotlin sources are silently skipped and the APK has no MainActivity.
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Emit Java 17 bytecode to match compileOptions below. A toolchain is not used,
// so the build runs on Android Studio's bundled JDK without downloading another.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "io.github.ravindren_sm.openstream"
    // The installed platform is android-37.0 (minor 0). Ask AGP for that exact release.
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "io.github.ravindren_sm.openstream"
        // minSdk 25 keeps room for older Fire TV models; the test device is API 30.
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-phase1"

        // The device has no 64-bit ABI, so ship the 32-bit ABI only.
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    // Provider protocol (shared with the Mac probe) and the player.
    implementation(project(":provider"))
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
}
