// El OCR es Android-only por ahora: en iOS, ML Kit se distribuye por CocoaPods y este proyecto no
// usa CocoaPods (el motor de Vision se apoya solo en frameworks del sistema). El catálogo sigue
// declarando iOS porque el motor llegará allí; mientras tanto responde NotImplemented, que es
// exactamente para lo que existe ese estado.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    // Aporta su propia superficie de preview, así que necesita Compose. Ver ADR-0007.
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
            implementation(libs.mlkit.text.recognition)
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
    namespace = "com.whyscan.engines.ocr"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
