plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.core.scanner"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
