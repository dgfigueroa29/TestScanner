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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        // El target wasmJs necesita descargar Node y Yarn, y el plugin de Kotlin los declara como
        // repositorios **del proyecto**. Con `FAIL_ON_PROJECT_REPOS` eso rompe la build entera:
        //
        //     Could not determine the dependencies of task ':kotlinWasmNodeJsSetup'.
        //     > repository 'Distributions at https://nodejs.org/dist' was added by unknown code
        //
        // Se declaran aquí en lugar de relajar el modo. Mantener `FAIL_ON_PROJECT_REPOS` vale la
        // pena: es lo que impide que un módulo se traiga dependencias de un repositorio que nadie
        // más ve. El `content` acota cada uno a su único artefacto, así que no compiten con Maven
        // Central para nada más.
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
