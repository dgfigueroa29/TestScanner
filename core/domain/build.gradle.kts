plugins {
    id("testscanner.kmp.library")
    // Solo para la exportación del historial (RF-11): el formato del archivo es una decisión
    // explícita con sus propios DTO, no un reflejo del modelo interno.
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.testscanner.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
