plugins {
    id("whyscan.kmp.library")
}

android {
    namespace = "com.whyscan.core.platform"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Solo el modelo: el selector de imágenes devuelve un `ScanImage`. Nada de dominio.
            api(project(":core:model"))
        }
    }
}
