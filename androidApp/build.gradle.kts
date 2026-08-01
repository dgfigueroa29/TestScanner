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
    // La Activity presta sus `ActivityResultLauncher` al controlador de permisos, al selector de
    // imágenes y al guardado de archivos, así que necesita ver esos tres contratos. `:composeApp`
    // los declara como `implementation`, que no es transitivo, y por eso hay que nombrarlos aquí:
    // sin `:core:platform` la compilación fallaba con "Cannot access 'ImagePicker' which is a
    // supertype of 'AndroidImagePicker'".
    implementation(project(":core:permissions"))
    implementation(project(":core:platform"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(compose.runtime)
}
