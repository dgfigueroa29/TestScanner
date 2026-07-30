plugins {
    `kotlin-dsl`
}

kotlin {
    compilerOptions {
        // Los targets Wasm siguen tras un opt-in; se concede aquí una vez en lugar de repetir
        // `@OptIn(ExperimentalWasmDsl::class)` en cada convention plugin.
        optIn.add("org.jetbrains.kotlin.gradle.ExperimentalWasmDsl")
    }
}

dependencies {
    // `compileOnly`: los plugins se resuelven en el build principal, aquí solo se necesitan sus
    // APIs para compilar los scripts precompilados.
    compileOnly(libs.gradlePlugin.android)
    compileOnly(libs.gradlePlugin.kotlin)
    compileOnly(libs.gradlePlugin.compose)
    compileOnly(libs.gradlePlugin.composeCompiler)
}
