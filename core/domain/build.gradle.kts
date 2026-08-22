plugins {
    id("whyscan.kmp.library")
    // Solo para la exportación del historial (RF-11): el formato del archivo es una decisión
    // explícita con sus propios DTO, no un reflejo del modelo interno.
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.whyscan.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            // La suite de contrato se aplica también a los decoradores del dominio (SDD §13.2), así
            // que `DecoratorContractTest` hereda de ella y necesita el módulo de fixtures.
            implementation(project(":core:scanner-testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
