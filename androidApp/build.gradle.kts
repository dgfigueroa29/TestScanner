plugins {
    id("testscanner.android.application")
}

android {
    namespace = "com.testscanner.android"

    defaultConfig {
        applicationId = "com.testscanner"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Fase 2 (deuda D7): activar minify y afinar las reglas de R8.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":core:permissions"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(compose.runtime)
}
