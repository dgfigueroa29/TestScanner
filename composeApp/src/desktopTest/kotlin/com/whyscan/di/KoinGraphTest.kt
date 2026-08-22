package com.whyscan.di

import com.whyscan.core.domain.repository.AppPreferencesRepository
import com.whyscan.core.domain.repository.ScanHistoryRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.domain.repository.ScannerEngineRepository
import com.whyscan.core.domain.usecase.ScanHistory
import com.whyscan.core.domain.usecase.ScanSessions
import com.whyscan.core.domain.usecase.ScanSettings
import com.whyscan.core.domain.usecase.StartComparisonUseCase
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.permissions.PermissionController
import com.whyscan.core.platform.FileSaver
import com.whyscan.core.platform.ImagePicker
import com.whyscan.core.platform.PlatformActions
import com.whyscan.core.scanner.BarcodeScannerEngine
import com.whyscan.core.scanner.TimeProvider
import com.whyscan.feature.scanner.EnginePreviewResolver
import com.whyscan.feature.scanner.ResultActionRunner
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Salda la deuda **D18**: hasta ahora nada comprobaba que el grafo de Koin resolviera.
 *
 * ## Por qué existe este archivo
 *
 * El primer arranque en un dispositivo real mató la app. `platformModule` registraba el executor de
 * análisis como `ExecutorService` mientras los tres motores de cámara lo piden como `Executor`, y
 * Koin resuelve por **igualdad exacta de tipo**: no recorre supertipos. La app moría al componer la
 * primera pantalla con `NoDefinitionFoundException`.
 *
 * El compilador no puede ver ese fallo —los `get()` son genéricos que se resuelven en ejecución— y
 * el CI tampoco podía: compilaba, pasaba lint, pasaba R8 y publicaba un APK que reventaba al
 * abrirse. Este test es lo que faltaba, y sin necesitar emulador.
 *
 * ## Qué comprueba y qué no
 *
 * Resuelve **de verdad** cada tipo que la raíz de la app pide, en lugar de inspeccionar
 * definiciones: si algo está declarado con el tipo equivocado, `get()` lanza aquí igual que lanzaba
 * en el teléfono.
 *
 * Lo que **no** cubre, dicho para que no se confunda con una red completa:
 *
 *  - Es el `platformModule` de **escritorio**, que es el que este test puede enlazar. El de Android
 *    —justo donde estaba el defecto D18— necesita un `androidUnitTest` en `:composeApp`, y sigue
 *    pendiente. Lo que sí queda cubierto para las cuatro plataformas son los módulos comunes:
 *    `dataModule`, `domainModule` y los tres de features.
 *  - No construye los ViewModels. Instanciarlos arranca corrutinas en `viewModelScope`, que exige un
 *    `Dispatchers.Main` real; lo que se comprueba es que **todo lo que piden por constructor**
 *    resuelve, que es exactamente donde falló D18. Añadir un parámetro nuevo a un ViewModel obliga a
 *    añadirlo también a la lista de abajo — y esa fricción es deliberada.
 *
 * Nota sobre efectos: resolver el historial crea el directorio `~/.whyscan`, que es el mismo que
 * usa la app en escritorio. No abre la base de datos: Room construye el archivo en la primera
 * consulta y aquí no se hace ninguna.
 */
class KoinGraphTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `el grafo arranca con todos los modulos de la app`() {
        // Arrancar ya detecta la clase de fallo más tonta y más cara: dos módulos declarando el
        // mismo tipo sin qualifier, que Koin rechaza al montar.
        val koin = start()

        assertTrue(koin.get<List<BarcodeScannerEngine>>().isNotEmpty(), "ningún motor enlazado")
    }

    @Test
    fun `resuelve todo lo que ScannerViewModel pide por constructor`() {
        val koin = start()

        koin.get<ScanSettings>()
        koin.get<ScanSessions>()
        koin.get<ScannerEngineRepository>()
        koin.get<PermissionController>()
        koin.get<ImagePicker>()
        koin.get<ResultActionRunner>()

        // La pantalla los pide aparte del ViewModel, con `koinInject`.
        koin.get<EnginePreviewResolver>()
    }

    @Test
    fun `resuelve todo lo que HistoryViewModel pide por constructor`() {
        val koin = start()

        koin.get<ScanHistory>()
        koin.get<PlatformActions>()
        koin.get<FileSaver>()
    }

    @Test
    fun `resuelve todo lo que ComparisonViewModel pide por constructor`() {
        val koin = start()

        koin.get<StartComparisonUseCase>()
        koin.get<ScanPreferencesRepository>()
    }

    @Test
    fun `resuelve todo lo que SettingsViewModel pide por constructor`() {
        // `AppPreferencesRepository` se declara en el `dataModule` común y depende de un `Settings`
        // que aporta cada plataforma. Es cableado nuevo, y es exactamente la forma del defecto D18:
        // una dependencia entre un módulo común y uno de plataforma que solo falla en ejecución.
        val koin = start()

        koin.get<AppPreferencesRepository>()
    }

    @Test
    fun `resuelve lo que la app necesita para arrancar`() {
        val koin = start()

        koin.get<ScannerPlatform>()
        koin.get<TimeProvider>()
    }

    /**
     * Aparte de los demás a propósito: es el único que construye algo pesado —Room— y si algún día
     * se rompe por el entorno en vez de por el cableado, conviene que no arrastre consigo la
     * comprobación del resto del grafo.
     */
    @Test
    fun `resuelve el historial persistente`() {
        val koin = start()

        koin.get<ScanHistoryRepository>()
    }

    private fun start(): Koin = startKoin { modules(appModules()) }.koin
}
