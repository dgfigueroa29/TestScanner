import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Motor exclusivo de Web: el decodificador lo pone el navegador, así que no hay nada que enlazar en
// las otras plataformas. Declarar los cuatro targets sería mentir sobre dónde funciona (RNF-06).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Aporta su propia superficie de preview (ADR-0007), aunque en Web sea un elemento del DOM.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            api(project(":core:scanner-ui"))
            implementation(compose.ui)
            implementation(compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
