plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.engines.manual"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Un motor solo conoce el SPI y el modelo. Nunca el dominio, los datos ni la UI.
            api(project(":core:scanner-api"))
        }
        commonTest.dependencies {
            implementation(project(":core:scanner-testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
