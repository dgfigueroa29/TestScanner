plugins {
    id("whyscan.kmp.library")
}

android {
    namespace = "com.whyscan.core.scanner"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
