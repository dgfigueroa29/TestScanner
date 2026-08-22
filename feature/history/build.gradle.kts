plugins {
    id("whyscan.kmp.compose")
}

android {
    namespace = "com.whyscan.feature.history"
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.whyscan.feature.history.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:platform"))
            api(project(":core:designsystem"))

            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
