pluginManagement {
    // Build incluido con los convention plugins (ver build-logic/).
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // `PREFER_SETTINGS` y no `FAIL_ON_PROJECT_REPOS`, que es lo que había.
    //
    // El plugin de Kotlin/Wasm declara los repositorios de Node y Yarn **a nivel de proyecto**, por
    // diseño y sin forma de desactivarlo. Con `FAIL_ON_PROJECT_REPOS` eso tumba la build entera
    // ("repository 'Distributions at https://nodejs.org/dist' was added by unknown code"), y
    // declararlos también aquí no ayuda: el modo no comprueba si el repositorio ya existe, prohíbe
    // que un proyecto declare ninguno.
    //
    // `PREFER_SETTINGS` conserva lo que importaba —los repositorios de proyecto se ignoran, así que
    // ningún módulo puede traerse dependencias de un sitio que nadie más ve— y se queda en un aviso
    // en lugar de un error. Los de Node y Yarn se declaran abajo para que sí haya de dónde bajarlos.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        // El target wasmJs descarga tres herramientas —Node, Yarn y Binaryen— y el plugin de Kotlin
        // declara sus repositorios a nivel de proyecto, que es lo que `PREFER_SETTINGS` ignora. Hay
        // que replicarlos aquí o la build no encuentra de dónde bajarlas.
        //
        // Son tres hoy. Una actualización de Kotlin podría añadir una cuarta, y entonces el job de
        // Web volverá a fallar con un "Could not find ..." sobre un artefacto con pinta rara. Es el
        // precio de no dejar que cualquier módulo declare repositorios por su cuenta, y el fallo al
        // menos es ruidoso e inmediato. El `content` acota cada uno a su único artefacto, así que
        // ninguno compite con Maven Central para nada más.
        ivy("https://nodejs.org/dist/") {
            name = "Node Distributions at https://nodejs.org/dist"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions at https://github.com/yarnpkg/yarn/releases/download"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions at https://github.com/WebAssembly/binaryen/releases/download"
            patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "TestScanner"

// Núcleo compartido
include(":core:model")
include(":core:scanner-api")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:permissions")
include(":core:platform")
include(":core:scanner-testing")
include(":core:scanner-ui")
include(":core:database")

// Motores de escaneo — un módulo por alternativa (ver docs/ENGINES.md)
include(":engines:manual")
include(":engines:gms-code-scanner")
include(":engines:mlkit-camerax")
include(":engines:vision-ios")
include(":engines:zxing-cpp")
include(":engines:zxing-java")
include(":engines:browser-detector")
include(":engines:mlkit-ocr")

// Features
include(":feature:scanner")
include(":feature:history")

// Aplicaciones
include(":composeApp")
include(":androidApp")
