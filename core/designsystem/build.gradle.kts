plugins {
    id("whyscan.kmp.compose")
}

android {
    namespace = "com.whyscan.core.designsystem"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            // Explícita y no heredada de `foundation`: las pantallas usan
            // `AnimatedVisibility` y `animateContentSize`, y depender de que otro artefacto
            // las arrastre es depender de un detalle de empaquetado ajeno.
            api(compose.animation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(compose.components.resources)
        }
    }
}
