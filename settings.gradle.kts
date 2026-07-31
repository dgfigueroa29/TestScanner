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
// Fase 3: include(":engines:zxing-cpp")
// Fase 4: include(":engines:browser-detector")
// Fase 4: include(":engines:mlkit-ocr")

// Features
include(":feature:scanner")
include(":feature:history")

// Aplicaciones
include(":composeApp")
include(":androidApp")
