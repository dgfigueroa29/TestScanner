import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // Módulo de fixtures: `kotlin("test")` va en commonMain a propósito, porque las clases
            // que expone son tests abstractos que otros módulos heredan desde su commonTest.
            api(kotlin("test"))
            api(project(":core:scanner-api"))
            api(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.testscanner.core.scanner.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
