package com.whyscan.di

import com.russhwolf.settings.Settings
import com.whyscan.core.domain.repository.AppPreferencesRepository
import com.whyscan.core.domain.repository.ScanPreferencesRepository
import com.whyscan.core.domain.repository.ScannerEngineRepository
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
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cierra la deuda **D18**: el grafo de Koin de **Android**, que es donde estaba el defecto original.
 *
 * ## Por qué hacía falta además de `KoinGraphTest`
 *
 * El de `desktopTest` cubre los cinco módulos comunes y el `platformModule` de escritorio, y eso ya
 * es mucho — pero **no cubre el módulo donde ocurrió el crash**. `platformModule()` es
 * `expect`/`actual`, y el de Android es con diferencia el más grande de los cuatro: cuatro motores
 * de cámara, un `Executor`, el controlador de permisos, tres servicios del sistema y las
 * preferencias sobre `SharedPreferences`. Que resuelva el de escritorio no dice nada del de Android.
 *
 * El defecto que costó el primer arranque vivía justo ahí: `Executors.newSingleThreadExecutor()`
 * registrado como `ExecutorService` mientras los tres motores de cámara lo piden como `Executor`.
 * Koin indexa por **igualdad exacta de tipo** y no recorre supertipos, así que la app moría al
 * componer la primera pantalla. Este test lo habría cazado.
 *
 * ## Por qué Robolectric, y por qué no contradice la decisión D6
 *
 * El grafo de Android necesita un `Context` de verdad: `SharedPreferencesSettings` llama a
 * `getSharedPreferences`, y eso no lo satisface un doble. Robolectric da ese `Context` **en la JVM**,
 * en el mismo job que el resto de los tests y sin emulador.
 *
 * D6 dice que no habrá tests instrumentados, y su motivo era concreto: sin emulador en CI, un test
 * que exija dispositivo es un test que nunca se ejecuta y que da una falsa sensación de red. Esto es
 * lo contrario — un test que sí corre en cada PR. Lo que se mantiene es lo que importaba de aquella
 * decisión: nada de esto necesita hardware.
 *
 * ## Qué no cubre, dicho para que no se confunda con una red completa
 *
 * **No toca el historial persistente, ni nada que dependa de él.** `sqlite-bundled` trae binarios
 * nativos compilados para las ABI de Android, y bajo Robolectric el proceso es una JVM de
 * escritorio: no los puede cargar. Eso deja fuera de este archivo `ScanHistoryRepository`,
 * `ScanHistory` y `ScanSessions` —que arrastra `SaveDetectionUseCase`—.
 *
 * No es un hueco de cableado y conviene ser preciso sobre por qué: esa misma cadena
 * (`DatabaseBuilderFactory` → `buildBundled` → `RoomScanHistoryRepository` → `ScanSessions`) **sí**
 * se resuelve de verdad en `KoinGraphTest`, con el `actual` de escritorio, y fue ahí donde se
 * destapó que el driver no se aplicaba. Lo único que no comprueba nadie es el `actual` de Android
 * de `DatabaseBuilderFactory`, que son cuatro líneas y sigue necesitando un dispositivo.
 *
 * Tampoco se construyen los ViewModels, por lo mismo que en su gemelo: instanciarlos arranca
 * corrutinas en `viewModelScope`. Lo que se comprueba es que **todo lo que piden por constructor**
 * resuelva, que es exactamente donde falló D18.
 */
@RunWith(RobolectricTestRunner::class)
// Fijado a conciencia y no heredado del `targetSdk`: Robolectric descarga un `android-all` por nivel
// de API, y subir el `targetSdk` no debería romper los tests el día que se suba, antes de que
// Robolectric publique soporte para ese nivel. Nada de lo que hay aquí depende del nivel: esto es
// cableado, no API de plataforma.
@Config(sdk = [ROBOLECTRIC_SDK])
class AndroidKoinGraphTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `el grafo de Android arranca con todos los modulos`() {
        // Arrancar ya detecta la clase de fallo más tonta y más cara: dos módulos declarando el
        // mismo tipo sin qualifier, que Koin rechaza al montar.
        val koin = start()

        assertEquals(ScannerPlatform.Android, koin.get<ScannerPlatform>())
    }

    /**
     * **El test que le faltaba a D18.**
     *
     * `Executor` y no `ExecutorService`: es el tipo con el que los tres motores de cámara lo piden y
     * por tanto el único con el que Koin lo va a encontrar. Si alguien vuelve a declarar la
     * definición por el tipo que devuelve la fábrica en lugar de por el que se consume, esto falla
     * aquí y no en el teléfono de alguien.
     */
    @Test
    fun `el executor de analisis se resuelve por el tipo que piden los motores`() {
        val koin = start()

        koin.get<Executor>()
    }

    @Test
    fun `los motores de Android se construyen todos`() {
        // Son los que solo existen en este binario (RNF-06). Si alguno pidiera por constructor algo
        // que este módulo no declara, no habría forma de verlo sin montar el grafo: los `get()` son
        // genéricos y se resuelven en ejecución.
        val koin = start()

        val engines = koin.get<List<BarcodeScannerEngine>>()

        assertEquals(
            EXPECTED_ANDROID_ENGINES,
            engines.size,
            "cambió la lista de motores de Android sin actualizar el test",
        )
        assertTrue(engines.distinctBy { it.id }.size == engines.size, "hay motores repetidos")
    }

    @Test
    fun `resuelve los servicios del sistema que aporta Android`() {
        val koin = start()

        koin.get<PermissionController>()
        koin.get<PlatformActions>()
        koin.get<ImagePicker>()
        koin.get<FileSaver>()
        koin.get<TimeProvider>()
    }

    /**
     * Las dependencias que **cruzan** de un módulo común a uno de plataforma, que es la forma exacta
     * del defecto D18 y la única que ningún compilador puede ver.
     *
     * `Settings` lo aporta Android sobre `SharedPreferences`; los dos repositorios de preferencias y
     * el catálogo de motores viven en el `dataModule` común y lo consumen desde allí.
     */
    @Test
    fun `resuelve lo comun que depende de lo que aporta Android`() {
        val koin = start()

        koin.get<Settings>()
        koin.get<ScanPreferencesRepository>()
        koin.get<AppPreferencesRepository>()
        koin.get<ScannerEngineRepository>()
    }

    @Test
    fun `resuelve lo que las pantallas piden y no viene del historial`() {
        val koin = start()

        koin.get<ScanSettings>()
        koin.get<ResultActionRunner>()
        koin.get<StartComparisonUseCase>()

        // La pantalla de escaneo lo pide aparte del ViewModel, con `koinInject`.
        koin.get<EnginePreviewResolver>()
    }

    private fun start(): Koin = startKoin {
        androidContext(RuntimeEnvironment.getApplication())
        modules(appModules())
    }.koin
}

/**
 * Nivel de API con el que Robolectric levanta el entorno: por encima de `minSdk` (24) y por debajo
 * del `compileSdk`, en un nivel para el que Robolectric lleva tiempo publicando su `android-all`.
 */
private const val ROBOLECTRIC_SDK = 34

/** Los cinco de `platformModule()`: GMS, ML Kit + CameraX, zxing-cpp, OCR y entrada manual. */
private const val EXPECTED_ANDROID_ENGINES = 5
