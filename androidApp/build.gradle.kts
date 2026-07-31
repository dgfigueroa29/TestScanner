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
            isMinifyEnabled = true

            // Encoger recursos exige encoger código. No toca `assets/`, que es donde Compose
            // Multiplatform empaqueta los `composeResources`: los textos siguen ahí.
            isShrinkResources = true

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
