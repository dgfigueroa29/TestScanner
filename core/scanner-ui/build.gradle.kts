plugins {
    id("testscanner.kmp.compose")
}

android {
    namespace = "com.testscanner.core.scanner.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            api(compose.runtime)
            api(compose.ui)
        }
    }
}
