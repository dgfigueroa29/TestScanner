plugins {
    id("whyscan.kmp.library")
    // Para el historial persistente de Web: se guarda como JSON en el almacén de la plataforma.
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.whyscan.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            api(libs.multiplatform.settings)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
