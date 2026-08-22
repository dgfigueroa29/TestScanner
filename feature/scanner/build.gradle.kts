plugins {
    id("whyscan.kmp.compose")
}

android {
    namespace = "com.whyscan.feature.scanner"
}

// El paquete se fija a mano en lugar de dejar que se derive: así el import de `Res` es estable y
// no depende de cómo el plugin componga el nombre a partir del grupo y el módulo.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.whyscan.feature.scanner.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:platform"))
            implementation(project(":core:permissions"))
            api(project(":core:scanner-ui"))
            api(project(":core:designsystem"))

            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
