// Motor exclusivo de iOS: declara solo los targets de Apple. No usa `testscanner.kmp.library`
// porque ese convention plugin da los cuatro, y aquí tres de ellos serían mentira.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:scanner-api"))
            // Aporta su propia superficie de preview (ADR-0007), así que necesita Compose.
            api(project(":core:scanner-ui"))
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:scanner-testing"))
        }
    }
}
