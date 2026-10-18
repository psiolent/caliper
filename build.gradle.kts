buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Pin the Kotlin Gradle plugin so the built-in Kotlin compiler, the
        // Compose/serialization plugins, and KSP all agree on one version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
