plugins {
    id("whyscan.android.application")
}

android {
    namespace = "com.whyscan.android"

    defaultConfig {
        // El `applicationId` es la identidad de la app en Play **para siempre**: es la URL de la
        // ficha y la clave con la que el sistema reconoce una actualización. No se puede cambiar
        // después de la primera publicación, así que se ajusta ahora que todavía no hay ninguna.
        //
        // No tiene por qué coincidir con los paquetes de Kotlin, pero aquí coincide: el proyecto
        // usa `com.whyscan.*` en todas partes —paquetes, `namespace` de cada módulo, plugins de
        // convención y almacenes de datos—, así que no hay dos nombres que mantener sincronizados
        // ni ninguno que explicar.
        //
        // Antes de la primera subida hay que comprobar en Play Console que este id está libre.
        applicationId = "com.whyscan.app"
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
