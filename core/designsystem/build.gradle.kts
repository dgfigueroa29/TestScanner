plugins {
    id("testscanner.kmp.compose")
}

android {
    namespace = "com.testscanner.core.designsystem"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(compose.components.resources)
        }
    }
}
