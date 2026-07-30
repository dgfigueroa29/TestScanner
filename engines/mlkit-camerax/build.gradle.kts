// Motor exclusivo de Android: declarar los cuatro targets sería mentir sobre dónde funciona, y
// haría que iOS, Desktop y Web enlazaran un SDK que no pueden usar (RNF-06).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
        }
        androidMain.dependencies {
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
    namespace = "com.testscanner.engines.mlkit"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
