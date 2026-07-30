import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Único módulo que NO usa `testscanner.kmp.compose`: necesita frameworks de iOS, un ejecutable de
// Wasm y el target JVM nombrado "desktop" para el plugin de escritorio. Meter todo eso en un
// convention plugin que solo usaría este módulo sería una abstracción de un solo cliente.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    androidTarget()

    // Se llama "desktop" por convención de Compose Multiplatform: el plugin de escritorio busca
    // ese nombre. Las librerías declaran `jvm()` a secas y resuelven igual, porque el emparejado
    // es por atributos de la variante y no por el nombre del target.
    jvm("desktop")

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:domain"))
            implementation(project(":core:permissions"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:scanner"))

            // Único módulo que conoce a todos los motores: es el composition root.
            implementation(project(":engines:manual"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }

        androidMain.dependencies {
            // Los motores de Android se enlazan SOLO aquí: el binario de iOS, Desktop y Web no
            // debe cargar ML Kit ni Play Services (RNF-06).
            implementation(project(":engines:gms-code-scanner"))
            implementation(project(":engines:mlkit-camerax"))

            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }

        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.testscanner.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.testscanner.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TestScanner"
            packageVersion = "1.0.0"
        }
    }
}
