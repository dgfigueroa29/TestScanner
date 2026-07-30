plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.core.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
