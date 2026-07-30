pluginManagement {
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

// Motores de escaneo — un módulo por alternativa (ver docs/ENGINES.md)
include(":engines:manual")
// Fase 2: include(":engines:gms-code-scanner")
// Fase 2: include(":engines:mlkit-camerax")
// Fase 3: include(":engines:vision-ios")
// Fase 3: include(":engines:zxing-cpp")
// Fase 4: include(":engines:browser-detector")
// Fase 4: include(":engines:mlkit-ocr")

// Features
include(":feature:scanner")

// Aplicaciones
include(":composeApp")
include(":androidApp")
