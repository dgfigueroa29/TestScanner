import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    // Aporta `clean` en la raíz. Antes se registraba a mano, y eso rompía la build: el target
    // wasmJs de `:composeApp` aplica sus plugins de Node y Yarn **al proyecto raíz**, que a su vez
    // aplican `LifecycleBasePlugin`, que registra su propio `clean`. Dos tareas con el mismo nombre
    // y la configuración se caía entera con "Cannot add task 'clean'". Aplicar `base` es lo mismo
    // que hacía la tarea a mano, pero de forma idempotente: quien llegue después no colisiona.
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.detekt)
}

// Se captura fuera de `allprojects` porque el accessor `libs` del version catalog
// solo está disponible en el ámbito de este script.
val detektFormatting = libs.detekt.formatting
val detektConfigFile = files("$rootDir/config/detekt/detekt.yml")

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(detektConfigFile)
        basePath = rootDir.absolutePath
    }

    dependencies {
        add("detektPlugins", detektFormatting)
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
