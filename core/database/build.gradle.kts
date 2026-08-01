// No usa `testscanner.kmp.library`: Room KMP **no soporta wasmJs**, así que este módulo declara
// tres targets en lugar de cuatro. Es una limitación real de la librería, no una decisión de
// diseño, y está registrada en el SDD §11: en Web el historial sigue siendo en memoria.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            // `api` y no `implementation`: los tipos públicos de este módulo **son** tipos de Room.
            // `ScanDatabase` hereda de `RoomDatabase` y `DatabaseBuilderFactory.create()` devuelve
            // un `RoomDatabase.Builder`, así que quien los use necesita ver esas clases. Con
            // `implementation`, `:composeApp` fallaba al montar el grafo con "Cannot access class
            // 'androidx.room.RoomDatabase.Builder'".
            api(libs.room.runtime)
            // El driver sí se queda como detalle interno: solo se usa dentro de `build()`.
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// KSP se declara por target: el procesador de Room genera una implementación distinta para cada
// plataforma, y omitir uno deja ese target sin `ScanDatabase` generada.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "com.testscanner.core.database"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
