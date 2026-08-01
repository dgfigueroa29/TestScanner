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
        // Sin esto, detekt no analizaba **ni un solo archivo**. Su fuente por defecto es
        // `src/main/kotlin` y `src/test/kotlin`, que son los directorios de un proyecto JVM
        // clásico; en un proyecto KMP el código vive en `src/commonMain/kotlin`,
        // `src/androidMain/kotlin` y demás. El resultado era un análisis estático que pasaba
        // siempre en verde porque no miraba nada, que es peor que no tenerlo: daba por revisado
        // lo que nadie había revisado.
        setSource(files("src"))
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/resources/**")

        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
