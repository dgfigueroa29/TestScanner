plugins {
    id("testscanner.kmp.library")
}

android {
    namespace = "com.testscanner.core.permissions"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
