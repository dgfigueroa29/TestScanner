plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.core.scanner.testing"
}

kotlin {
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
