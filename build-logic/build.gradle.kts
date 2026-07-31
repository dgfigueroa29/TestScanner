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
    // `implementation` y no `compileOnly`, aunque en muchos proyectos se vea lo contrario.
    //
    // La diferencia está en cómo se escriben los convention plugins. Los que son clases
    // `Plugin<Project>` aplican los suyos con `pluginManager.apply("id")`, que se resuelve en
    // tiempo de ejecución contra el classpath del build principal: ahí `compileOnly` basta. Estos
    // son **scripts precompilados** (`.gradle.kts`) con un bloque `plugins { id(...) }`, y ese
    // bloque lo resuelve Gradle al generar los accesores de `build-logic`, antes de que exista el
    // build principal. Con `compileOnly` el plugin no está en ese classpath y la build muere en
    // `generatePrecompiledScriptPluginAccessors` con "plugin was not found in any of the following
    // sources", que es exactamente lo que hizo en el primer CI del proyecto.
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.compose)
    implementation(libs.gradlePlugin.composeCompiler)
}
