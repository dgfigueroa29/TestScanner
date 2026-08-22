// Baseline de comparación (ADR-0008): Android e iOS, el mismo decodificador C++ en ambos.
//
// No hay `commonMain` con lógica compartida y no es un descuido: las dos publicaciones de zxing-cpp
// exponen APIs distintas —una anida `Format` en `BarcodeReader`, la otra lo saca a primer nivel—,
// así que cada target trae su propio adaptador. Lo que se comparte es el decodificador, no el
// binding, y es lo que hace justa la comparación.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    // Aporta su propia superficie de preview en las dos plataformas (ADR-0007).
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            api(project(":core:scanner-ui"))
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.zxingcpp.android)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
        }
        iosMain.dependencies {
            implementation(libs.zxingcpp.native)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:scanner-testing"))
        }
    }
}

android {
    namespace = "com.whyscan.engines.zxing"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
