package com.testscanner.feature.scanner

import com.testscanner.core.domain.scan.OpenKind
import com.testscanner.core.domain.scan.ResultAction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cuándo se avisa al usuario tras una acción sobre un resultado (RF-13).
 *
 * Esta decisión estaba dentro del ViewModel y solo se podía probar levantándolo entero; ahora es
 * una clase sin estado (deuda D16) y se comprueba de un vistazo.
 */
class ResultActionRunnerTest {

    private fun runnerOf(succeeds: Boolean) =
        ResultActionRunner(FakePlatformActions(canShare = true, succeeds = succeeds))

    @Test
    fun `copiar confirma cuando sale bien, porque no se ve nada`() = runTest {
        assertEquals(ScannerMessage.Copied, runnerOf(succeeds = true).run(ResultAction.Copy, "algo"))
    }

    @Test
    fun `compartir no avisa cuando sale bien, porque se ve la hoja`() = runTest {
        assertNull(runnerOf(succeeds = true).run(ResultAction.Share, "algo"))
    }

    @Test
    fun `abrir no avisa cuando sale bien, porque cambia de app`() = runTest {
        val open = ResultAction.Open("https://ejemplo.com", OpenKind.Link)

        assertNull(runnerOf(succeeds = true).run(open, "algo"))
    }

    @Test
    fun `cada accion tiene su propio aviso de fallo`() = runTest {
        val runner = runnerOf(succeeds = false)
        val open = ResultAction.Open("algo://raro", OpenKind.Link)

        assertEquals(ScannerMessage.CopyFailed, runner.run(ResultAction.Copy, "algo"))
        assertEquals(ScannerMessage.ShareFailed, runner.run(ResultAction.Share, "algo"))
        assertEquals(ScannerMessage.OpenFailed, runner.run(open, "algo"))
    }

    @Test
    fun `manda a la plataforma exactamente el texto que recibe`() = runTest {
        // Redactarlo es cosa de la UI (D15): aquí no se reinterpreta ni se recorta.
        val platform = FakePlatformActions(canShare = true, succeeds = true)

        ResultActionRunner(platform).run(ResultAction.Copy, "Red: MiRed · Clave: clave")

        assertEquals(listOf("Red: MiRed · Clave: clave"), platform.copied)
    }

    @Test
    fun `expone si la plataforma sabe compartir`() {
        val sinCompartir = ResultActionRunner(FakePlatformActions(canShare = false, succeeds = true))

        assertEquals(true, runnerOf(succeeds = true).canShare)
        assertEquals(false, sinCompartir.canShare)
    }
}
