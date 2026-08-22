plugins {
    id("whyscan.kmp.library")
}

android {
    namespace = "com.whyscan.core.scanner.testing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Módulo de fixtures: `kotlin("test")` va en commonMain a propósito, porque las clases
            // que expone son tests abstractos que otros módulos heredan desde su commonTest.
            api(kotlin("test"))
            api(project(":core:scanner-api"))
            api(libs.kotlinx.coroutines.test)
        }

        // En Kotlin/Native y en Wasm, `kotlin.test.Test` es una anotación de verdad. En la JVM no:
        // es un alias al `@Test` del framework que se esté usando, y esa variante solo se elige
        // sola en compilaciones **de test**. Como aquí las clases viven en `commonMain`, la
        // compilación principal de JVM y de Android se quedaba sin ella y fallaba con "Unresolved
        // reference 'Test'". Pedir la variante de JUnit explícitamente es lo que la resuelve.
        jvmMain.dependencies {
            api(kotlin("test-junit"))
        }
        androidMain.dependencies {
            api(kotlin("test-junit"))
        }
    }
}
