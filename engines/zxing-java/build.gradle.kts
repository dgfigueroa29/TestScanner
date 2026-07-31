// Decodificador de escritorio (deuda D13). Un solo target: `com.google.zxing:core` es un jar de
// Java puro, así que declararlo en Android o iOS sería enlazar peso que esas plataformas ya cubren
// con motores mejores (RNF-06). Web queda fuera porque no hay JVM que lo ejecute.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(project(":core:scanner-api"))
            implementation(libs.zxing.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":core:scanner-testing"))
            implementation(libs.kotlinx.coroutines.test)
            // El propio ZXing genera los códigos con los que se le pone a prueba: así el test
            // decodifica una imagen real y no un doble que devuelve lo que ya se espera.
            implementation(libs.zxing.core)
        }
    }
}
