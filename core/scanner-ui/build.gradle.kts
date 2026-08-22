plugins {
    id("whyscan.kmp.compose")
}

android {
    namespace = "com.whyscan.core.scanner.ui"
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
