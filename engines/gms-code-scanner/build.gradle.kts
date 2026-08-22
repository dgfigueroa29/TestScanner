// No usa `whyscan.kmp.library`: este motor solo existe en Android, así que declarar cuatro
// targets sería mentir sobre dónde funciona. El binario de iOS, Desktop y Web no debe enlazarlo
// (RNF-06), y el catálogo ya lo reporta como no soportado fuera de Android.
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
            implementation(libs.play.services.code.scanner)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:scanner-testing"))
        }
    }
}

android {
    namespace = "com.whyscan.engines.gms"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
