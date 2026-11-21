// Settings for the Fire TV IPTV app (Phase 1: hello app).
// Plugin and library repositories are declared here; the project versions live in gradle/libs.versions.toml.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "firetv-iptv"
include(":app")
// Phase 2: provider protocol library (plain Kotlin, reused by :app in Phase 3)
// and the command-line probe that drives it on the Mac.
include(":provider")
include(":probe")
