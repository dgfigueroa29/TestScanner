plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
