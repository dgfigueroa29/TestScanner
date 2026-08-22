plugins {
    id("whyscan.kmp.library")
}

android {
    namespace = "com.whyscan.core.permissions"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
