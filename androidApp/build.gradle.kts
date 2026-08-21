plugins {
    id("testscanner.android.application")
}

android {
    namespace = "com.testscanner.android"

    defaultConfig {
        // El `applicationId` es la identidad de la app en Play **para siempre**: es la URL de la
        // ficha y la clave con la que el sistema reconoce una actualización. No se puede cambiar
        // después de la primera publicación, así que se ajusta ahora que todavía no hay ninguna.
        //
        // No tiene por qué coincidir con los paquetes de Kotlin, y aquí no coincide a propósito:
        // renombrar `com.testscanner.*` en doscientos archivos sería mucho movimiento para cambiar
        // algo que el usuario no ve. Lo que el usuario ve es esto.
        //
        // Antes de la primera subida hay que comprobar en Play Console que este id está libre.
        applicationId = "com.scanly.app"
        versionCode = 1
        versionName = "1.0.0"
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
