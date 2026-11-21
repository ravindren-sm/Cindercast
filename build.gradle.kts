// Root build script. Plugins are declared here with "apply false" so that
// the app module can apply them with the versions from the version catalog.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
