package com.whyscan.core.domain.scan

import com.whyscan.core.model.ScannerEngineId
import com.whyscan.core.model.ScannerPlatform
import com.whyscan.core.scanner.catalog.ScannerEngineCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnginePriorityPolicyTest {

    @Test
    fun `la cadena solo lista motores que existen en esa plataforma`() {
        // Es el invariante que se rompió al descubrir que zxing-cpp no publica artefacto JVM ni
        // wasmJs: la tabla seguía priorizándolo en Desktop y Web, donde nunca podrá estar. Una
        // entrada muerta en la cadena no falla — simplemente nunca se elige — y por eso hace falta
        // un test que la vea.
        ScannerPlatform.entries.forEach { platform ->
            EnginePriorityPolicy.order(platform).forEach { id ->
                val descriptor = ScannerEngineCatalog.byId(id)
                assertTrue(
                    platform in descriptor.platforms,
                    "${id.id} está priorizado en $platform pero el catálogo no lo soporta ahí",
                )
            }
        }
    }

    @Test
    fun `la entrada manual cierra la cadena en las cuatro plataformas`() {
        // Es lo que garantiza que nunca exista el estado "no se puede escanear nada".
        ScannerPlatform.entries.forEach { platform ->
            assertEquals(
                ScannerEngineId.ManualInput,
                EnginePriorityPolicy.order(platform).lastOrNull(),
                "en $platform la cadena no termina en entrada manual",
            )
        }
    }

    @Test
    fun `ninguna cadena repite un motor`() {
        ScannerPlatform.entries.forEach { platform ->
            val order = EnginePriorityPolicy.order(platform)
            assertEquals(order.distinct(), order, "$platform tiene motores repetidos")
        }
    }

    @Test
    fun `un motor fuera de la tabla nunca gana a uno priorizado`() {
        val ranked = EnginePriorityPolicy.rank(ScannerPlatform.Android, ScannerEngineId.ManualInput)
        val unranked = EnginePriorityPolicy.rank(ScannerPlatform.Desktop, ScannerEngineId.VisionIos)

        assertTrue(unranked > ranked)
    }
}
