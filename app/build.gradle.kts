import java.util.Properties

// App module. Package id is a new one, not StbEmu's, so both apps can coexist.
// Target device: Fire TV AFTMA08C15, Android 11 (API 30), 32-bit ABI only.

plugins {
    alias(libs.plugins.android.application)
    // AGP 8.x does not compile Kotlin by itself; without this plugin the
    // Kotlin sources are silently skipped and the APK has no MainActivity.
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Room's annotation processor.
    alias(libs.plugins.ksp)
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
        versionCode = 3
        versionName = "0.1.2"

        // The device has no 64-bit ABI, so ship the 32-bit ABI only.
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    // Release signing. The key and its passwords live outside the repository (and outside any synced folder); this file only says where.
    // Without that file the release build is produced unsigned, so a fresh clone still builds. Never commit the key. Losing it means
    // every installed copy has to be uninstalled before an update can be installed.
    val releaseProps = Properties().apply {
        val path = System.getenv("OPENSTREAM_RELEASE_PROPS") ?: "${System.getProperty("user.home")}/.config/firetv-iptv/release/release.properties"
        val f = file(path)
        if (f.isFile) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (releaseProps.containsKey("storeFile")) create("release") {
            storeFile = file(releaseProps.getProperty("storeFile"))
            storePassword = releaseProps.getProperty("storePassword")
            keyAlias = releaseProps.getProperty("keyAlias")
            keyPassword = releaseProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // Side-by-side test install with its own empty storage, so a fake-portal run never
            // touches the real app's saved settings:  ./gradlew -PcheckSuffix=true :app:assembleDebug
            if (providers.gradleProperty("checkSuffix").isPresent) applicationIdSuffix = ".check"
        }
        release {
            // Smaller and harder to read: code and resource shrinking on. The debug logging, health sampler and probe screens are debug-only.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // The same side-by-side switch as debug, to test a release build next to the real install.
            if (providers.gradleProperty("checkSuffix").isPresent) applicationIdSuffix = ".check"
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
    implementation(libs.kotlinx.serialization.json)   // reads the online tag service's answers
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    // Profiles, favorites and watch history. Logic stays in plain Kotlin; Room is only the storage layer.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.okhttp.mockwebserver)   // local fake portal; tests never touch the provider
}

// Room exports its database structure as JSON so schema changes are visible in review and migrations can be tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
