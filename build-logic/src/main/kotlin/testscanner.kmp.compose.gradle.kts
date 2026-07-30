/**
 * Librería KMP que además usa Compose Multiplatform.
 *
 * Se apoya en [testscanner.kmp.library] en lugar de repetir su configuración: los módulos de UI
 * son librerías KMP normales que encima traen Compose.
 */
plugins {
    id("testscanner.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
