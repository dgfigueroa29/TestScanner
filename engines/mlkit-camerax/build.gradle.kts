// Motor exclusivo de Android: declarar los cuatro targets sería mentir sobre dónde funciona, y
// haría que iOS, Desktop y Web enlazaran un SDK que no pueden usar (RNF-06).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    // Este motor aporta su propia superficie de preview (CameraPreviewEngine), así que necesita
    // Compose. Es un módulo de plataforma, no de dominio: la dependencia es asumible.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            api(project(":core:scanner-ui"))
        }
        androidMain.dependencies {
            implementation(compose.ui)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:scanner-testing"))
        }
    }
}

android {
    namespace = "com.whyscan.engines.mlkit"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
