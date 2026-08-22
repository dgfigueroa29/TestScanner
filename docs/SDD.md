# Software Design Document — WhyScan Multiplatform

| Campo | Valor |
|---|---|
| Proyecto | WhyScan |
| Documento | Software Design Document (SDD) |
| Versión | 1.10 |
| Estado | Vigente — **el proyecto compila y pasa CI** en Android (con R8), Escritorio y Web, y el framework de iOS **enlaza entero** desde el workflow manual `ios.yml`. Fases 1, 2, 4 y 5 cerradas salvo lo listado como pendiente; la 3 (iOS) escrita y despriorizada por falta de dispositivo, no de compilación. **La app arrancó por primera vez en un dispositivo real** en la versión anterior, y ese arranque encontró un defecto que ninguna comprobación automática podía ver (§10); esta versión convierte esa comprobación en un test y, con él, destapa un segundo defecto de meses en la persistencia (§11) |
| Fecha | 2026-08-22 |
| Autor | Equipo WhyScan |
| Alcance de esta versión | Cierre de D18 y D22 —el grafo de Android y la migración pasan a tener test (§10, §11, §13.1)—, deshacer un borrado, búsqueda sin acentos y exportación a texto plano (§9.7). Antes, en la misma fase: las notas del historial (ADR-0012), marca, tema, idiomas y el rediseño del escáner (ADR-0010, ADR-0011), y el renombrado a **WhyScan** (§1.1) |

---

## 1. Introducción

### 1.1 Propósito

Este documento define la arquitectura objetivo de **WhyScan** tras su migración desde una
aplicación Android de módulo único hacia una aplicación **Compose Multiplatform (CMP)**.

> **Un solo nombre, escrito siempre igual.** **WhyScan** nombra el producto, el proyecto Gradle,
> los paquetes de Kotlin (`com.whyscan.*`), el `namespace` de cada módulo, los plugins de convención
> (`whyscan.kmp.library`, `whyscan.kmp.compose`, `whyscan.android.application`), el `applicationId`
> de Play (`com.whyscan.app`) y los almacenes de datos de cada plataforma. **Es una sola palabra**:
> `WhyScan` en prosa y en tipos, `whyScan` en identificadores lowerCamelCase, `whyscan` en paquetes,
> ids de plugin y nombres de recurso. Nunca "Why Scan" ni "Why-Scan".
>
> Esto no fue así desde el principio, y la decisión de unificarlo es deliberada: convivían un nombre
> de producto y un nombre interno distintos, con una nota en cada documento explicando por qué. Un
> nombre que hay que explicar en tres sitios cuesta más que el renombrado que evita.

El objetivo funcional principal del producto es:

> Ofrecer **múltiples alternativas de escaneo intercambiables** para **todo tipo de códigos de
> barras y QR**, permitiendo comparar motores, degradar con elegancia cuando uno no está
> disponible, y funcionar sobre Android, iOS, Desktop y Web.

WhyScan no es solo un lector de códigos: es un **banco de pruebas de motores de escaneo**. Esa
naturaleza es lo que dicta la decisión arquitectónica central del documento — el **Scanner Engine
SPI** (§7).

**Y desde que hay intención de publicar, es las dos cosas a la vez.** El banco de pruebas dejó de ser
la portada: la app arranca como lector —cámara a pantalla completa, resultado a mano, sin nombrar un
solo motor— y el catálogo, el comparador y las métricas vuelven con un interruptor en Ajustes. No son
la misma pantalla con cosas ocultas sino dos disposiciones distintas, por los motivos de
[ADR-0010](adr/ADR-0010-dos-disposiciones-de-la-pantalla-de-escaneo.md).

### 1.2 Alcance

| Dentro de alcance | Fuera de alcance (por ahora) |
|---|---|
| Arquitectura multiplataforma y estructura de módulos Gradle | Backend propio / sincronización en la nube |
| SPI de motores de escaneo, registro, selección y fallback | Generación de códigos (encoding) |
| Modelo de dominio de códigos y resultados | Cuentas de usuario / autenticación |
| Capa de presentación MVI en Compose Multiplatform | Analítica de producto y A/B testing |
| Gestión de permisos de cámara multiplataforma | Backend, sincronización o cualquier uso de red |
| Estrategia de testing, calidad y CI | Facturación / monetización |
| Marca, tema claro/oscuro e idiomas inglés y español (§9.9) | Más idiomas que esos dos |
| Preparación para publicar en Play (icono, `applicationId`, `localeConfig`) | El trámite de publicación en sí: ficha, capturas y firma |

### 1.3 Glosario

| Término | Definición |
|---|---|
| **Motor / Engine** | Implementación concreta capaz de detectar códigos (ej. ML Kit, ZXing, Vision) |
| **SPI** | *Service Provider Interface*: contrato que implementan los motores y consume el dominio |
| **Símbolo / Formato** | Simbología del código: QR, EAN-13, Code 128, PDF417, DataMatrix, Aztec… |
| **Sesión de escaneo** | Ciclo de vida desde que se arranca un motor hasta que se detiene |
| **Detección** | Un código concreto reconocido dentro de una sesión |
| **Capabilities** | Descripción declarativa de lo que un motor sabe y no sabe hacer |
| **KMP** | Kotlin Multiplatform |
| **CMP** | Compose Multiplatform |
| **expect/actual** | Mecanismo de KMP para declarar una API común con implementación por plataforma |

### 1.4 Documentos relacionados

- `docs/adr/` — Architecture Decision Records (decisiones puntuales con su contexto y consecuencias)
- `docs/ROADMAP.md` — plan de fases y criterios de salida de cada una
- `docs/ENGINES.md` — matriz operativa de motores (fuente de verdad para el catálogo en código)

---

## 2. Contexto y motivación

### 2.1 Estado inicial (antes de la migración)

```
WhyScan/
└── app/                          # módulo Android único
    ├── build.gradle              # Groovy DSL, AGP 8.0.2, Kotlin 1.8.10
    └── src/main/java/…/
        ├── MainActivity.kt       # scaffolding de plantilla: Greeting("Android")
        └── ui/theme/             # tema Material 3 por defecto (Purple/Pink)
```

Diagnóstico:

- **No hay lógica de escaneo implementada.** La dependencia `play-services-code-scanner:16.0.0`
  y el `<meta-data barcode_ui>` del manifiesto están declarados, pero ningún código los usa.
- **No hay arquitectura.** No existen capas, ni DI, ni modelo de dominio, ni tests reales.
- **Toolchain vencido.** Groovy DSL, Kotlin 1.8.10, AGP 8.0.2, `compileSdk 34`, Java 8, sin
  version catalog. Nada de esto es compatible con Compose Multiplatform moderno.
- **Acoplamiento total a Android.** Incluso el poco código presente (`Activity`, `res/`,
  `dynamicColorScheme`) es intransportable.

**Conclusión:** no hay deuda funcional que preservar. La migración es efectivamente una
**reconstrucción sobre fundaciones nuevas**, lo que elimina el riesgo típico de una migración
incremental y justifica reestructurar el build de una sola vez.

### 2.2 Objetivos

| ID | Objetivo | Métrica de éxito |
|---|---|---|
| G1 | Un único código base para Android, iOS, Desktop y Web | ≥ 85 % del código en `commonMain` |
| G2 | Motores de escaneo intercambiables en caliente | Cambiar de motor sin reiniciar la app |
| G3 | Cobertura de simbologías | 1D lineales + 2D matriciales + postales (§6.2) |
| G4 | Degradación elegante | Si el motor preferido no está disponible, se usa el siguiente sin error visible |
| G5 | Comparabilidad | El usuario puede ver qué motor detectó qué, con latencia y confianza |
| G6 | Base testeable | Lógica de selección/fallback cubierta por tests en `commonTest` |

### 2.3 No-objetivos

- **No** se busca la máxima performance de un motor concreto, sino la **capacidad de comparar**.
- **No** se busca paridad visual pixel-perfect entre plataformas; se busca coherencia de diseño.
- **No** se implementarán todos los motores en la Fase 1 (ver §14).

---

## 3. Requisitos

### 3.1 Requisitos funcionales

| ID | Requisito | Prioridad |
|---|---|---|
| RF-01 | El usuario puede escanear un código con la cámara en vivo | Must |
| RF-02 | El usuario puede elegir explícitamente el motor de escaneo entre los disponibles | Must |
| RF-03 | La app muestra el catálogo de motores con sus capacidades y su estado de disponibilidad | Must |
| RF-04 | La app selecciona automáticamente el mejor motor disponible si el usuario no elige | Must |
| RF-05 | Si el motor elegido falla o no está disponible, la app cae al siguiente de la cadena | Must |
| RF-06 | El usuario puede filtrar qué formatos quiere detectar | Should |
| RF-07 | El usuario puede escanear desde una imagen de la galería o un archivo | Should |
| RF-08 | El resultado muestra: contenido, formato, motor usado, latencia y timestamp | Must |
| RF-09 | El resultado se interpreta semánticamente (URL, WiFi, vCard, EAN de producto, …) | Should |
| RF-10 | Modo continuo: detección múltiple sin cerrar la cámara | Should |
| RF-11 | Historial local de escaneos, consultable y borrable | Should |
| RF-12 | Reconocimiento de texto (OCR) como motor alternativo para códigos ilegibles | Could |
| RF-13 | Acciones sobre el resultado: copiar, compartir, abrir enlace | Should |
| RF-14 | Control de linterna y zoom cuando el motor lo soporte | Could |
| RF-15 | La sesión puede tener un plazo máximo tras el cual se cierra sola | Could |

Los límites del `ScanRequest` — formatos, cuántos códigos por frame, si la sesión sigue tras la
primera lectura y el plazo máximo — los hacen cumplir **decoradores del dominio**, no cada motor.
Los motores son desiguales en esto: el de entrada manual respeta el modo continuo porque lo
implementa a mano, mientras que ML Kit y Vision dejan la cámara corriendo hasta que el consumidor
cancele. Centralizarlo es lo que garantiza el mismo comportamiento observable en los ocho.

### 3.2 Requisitos no funcionales

| ID | Requisito | Criterio |
|---|---|---|
| RNF-01 | Latencia de detección | < 500 ms desde que el código es visible, en gama media |
| RNF-02 | Arranque de cámara | < 1 s desde que se abre la pantalla de escaneo |
| RNF-03 | Privacidad | Ningún frame de cámara sale del dispositivo. Sin analítica de imagen remota |
| RNF-04 | Offline | Todos los motores excepto los que declaren `requiresNetwork` funcionan sin red |
| RNF-05 | Accesibilidad | Contraste AA, targets ≥ 48 dp, lectores de pantalla en resultados |
| RNF-06 | Tamaño | El APK/IPA no debe crecer por motores que el usuario no usa (ver §7.6) |
| RNF-07 | Mantenibilidad | Añadir un motor nuevo = 1 módulo + 1 registro, sin tocar UI ni dominio |
| RNF-08 | Testabilidad | El dominio no depende de ninguna API de plataforma |

---

## 4. Principios de diseño

1. **El dominio no sabe qué es una cámara.** El dominio conoce `BarcodeScannerEngine`, un
   contrato abstracto. Toda API de plataforma vive detrás de ese contrato.
2. **Las capacidades son datos, no `if`s.** Un motor declara qué sabe hacer
   (`ScannerCapabilities`); la lógica de selección razona sobre esos datos. Añadir un motor no
   añade ramas condicionales en ningún lado.
3. **La indisponibilidad es un estado de primera clase, no una excepción.** Un motor puede estar
   `Available`, `RequiresPermission`, `RequiresDownload`, `Unsupported` o `NotImplemented`. La UI
   muestra la razón; el selector la usa para decidir.
4. **`commonMain` primero.** Se baja a `expect/actual` solo cuando la plataforma es
   inevitablemente distinta; nunca por conveniencia.
5. **Unidireccionalidad estricta.** Estado hacia abajo, acciones hacia arriba (MVI, §9).
6. **Composición sobre herencia.** El fallback, la telemetría y el filtrado de formatos son
   *decoradores* de `BarcodeScannerEngine`, no subclases.

---

## 5. Arquitectura

### 5.1 Vista de capas

```
┌───────────────────────────────────────────────────────────────────────┐
│  PRESENTACIÓN            Compose Multiplatform (commonMain)           │
│  ScannerScreen · EngineCatalogScreen · ResultScreen · HistoryScreen   │
│  ScannerViewModel (MVI: State + Action + Effect)                      │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ solo UseCases
┌───────────────────────────────▼───────────────────────────────────────┐
│  DOMINIO                 Kotlin puro, sin dependencias de plataforma   │
│  UseCases · Repository interfaces · Modelo · Políticas de selección    │
└───────────────┬───────────────────────────────┬───────────────────────┘
                │ implementa                    │ consume
┌───────────────▼───────────────┐   ┌───────────▼───────────────────────┐
│  DATOS                        │   │  SCANNER SPI  (contrato)          │
│  Registry · Preferencias      │   │  BarcodeScannerEngine             │
│  Historial · Mappers          │   │  ScannerCapabilities              │
└───────────────────────────────┘   │  EngineAvailability · Catálogo    │
                                    └───────────┬───────────────────────┘
                                                │ implementan
        ┌───────────────┬───────────────┬───────┴───────┬───────────────┐
        ▼               ▼               ▼               ▼               ▼
  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐
  │ GMS Code  │  │  ML Kit   │  │  Vision   │  │ ZXing-cpp │  │  Browser  │
  │ Scanner   │  │ + CameraX │  │AVFoundat. │  │   (KMP)   │  │ Detector  │
  │ (Android) │  │ (Android) │  │   (iOS)   │  │ (todas)   │  │  (Wasm)   │
  └───────────┘  └───────────┘  └───────────┘  └───────────┘  └───────────┘
```

La regla de dependencias es estricta y verificable: **las flechas apuntan siempre hacia el
dominio**. Un módulo de motor depende de `:core:scanner-api` y de su SDK nativo; **nunca** de
`:feature:*`, de `:core:data` ni de otro motor.

### 5.2 Estructura de módulos Gradle

```
WhyScan/
├── gradle/libs.versions.toml          # version catalog — única fuente de versiones
├── build-logic/                       # convention plugins: kmp.library, kmp.compose, android.application
│
├── core/
│   ├── model/                         # KMP puro: Barcode, BarcodeFormat, ScanResult
│   ├── scanner-api/                   # SPI: BarcodeScannerEngine, Capabilities, Availability
│   │                                  #      + catálogo declarativo de los 8 motores
│   ├── scanner-ui/                    # capacidad de UI del motor: CameraPreviewEngine (ADR-0007)
│   ├── scanner-testing/               # suite de contrato que todo motor hereda (§13.2)
│   ├── database/                      # Room KMP: historial persistente (sin target wasmJs)
│   ├── domain/                        # UseCases, interfaces de Repository y decoradores del SPI
│   ├── data/                          # Registry, preferencias e historial
│   ├── designsystem/                  # CMP: paleta, tipografía, formas, marca y cambio de idioma
│   ├── permissions/                   # expect/actual: permiso de cámara
│   └── platform/                      # servicios del sistema: portapapeles, compartir, abrir
│                                      #   enlace, selector de imágenes y guardado de archivos
│
├── engines/
│   ├── manual/                        # entrada manual — motor de referencia, 100 % common
│   ├── gms-code-scanner/              # Android — UI propia, sin permisos
│   ├── mlkit-camerax/                 # Android — CameraX + preview + linterna/zoom
│   ├── vision-ios/                    # iOS — AVFoundation
│   ├── zxing-cpp/                     # Android + iOS — baseline de comparación (ADR-0008)
│   ├── zxing-java/                    # Desktop — com.google.zxing:core, solo imagen (D13)
│   ├── browser-detector/              # Wasm/JS — BarcodeDetector del navegador
│   └── mlkit-ocr/                     # Android — lee el número impreso bajo el código
│
├── feature/
│   ├── scanner/                       # pantalla de escaneo, selector de motor y comparador
│   ├── history/                       # historial, filtrable por motor
│   └── settings/                      # tema, idioma y modo avanzado
│
├── composeApp/                        # raíz CMP: App(), navegación, wiring de DI
│                                      # targets: android, iosArm64/SimulatorArm64, jvm, wasmJs
├── androidApp/                        # shell Android: Application + MainActivity
│                                      #   + icono adaptativo, temas de arranque y localeConfig
├── iosApp/                            # shell iOS: proyecto Xcode + SwiftUI host
├── playstore/                         # material de la ficha de Play (icono 512×512)
└── docs/
```

**Por qué esta granularidad**

- `:core:model` y `:core:scanner-api` separados: un módulo de motor solo necesita el SPI y el
  modelo. No arrastra el dominio ni los casos de uso, lo que impide dependencias cíclicas por
  descuido y mantiene los motores livianos y sustituibles.
- `engines/*` como módulos independientes: cumple RNF-06 y RNF-07. Un motor se añade o se
  elimina cambiando **una línea** en `settings.gradle.kts` y **una línea** en el registro.
- `:composeApp` es el único módulo que conoce a todos los motores. Es el *composition root*.

### 5.3 Matriz módulo × target

| Módulo | android | ios | jvm | wasmJs |
|---|:---:|:---:|:---:|:---:|
| `:core:model` | ✅ | ✅ | ✅ | ✅ |
| `:core:scanner-api` | ✅ | ✅ | ✅ | ✅ |
| `:core:scanner-ui` | ✅ | ✅ | ✅ | ✅ |
| `:core:scanner-testing` | ✅ | ✅ | ✅ | ✅ |
| `:core:database` | ✅ | ✅ | ✅ | ❌ |
| `:core:domain` | ✅ | ✅ | ✅ | ✅ |
| `:core:data` | ✅ | ✅ | ✅ | ✅ |
| `:core:designsystem` | ✅ | ✅ | ✅ | ✅ |
| `:core:permissions` | ✅ | ✅ | ✅ | ✅ |
| `:core:platform` | ✅ | ✅ | ✅ | ✅ |
| `:engines:manual` | ✅ | ✅ | ✅ | ✅ |
| `:engines:gms-code-scanner` | ✅ | — | — | — |
| `:engines:mlkit-camerax` | ✅ | — | — | — |
| `:engines:vision-ios` | — | ✅ | — | — |
| `:engines:zxing-cpp` | ✅ | ✅ | — | — |
| `:engines:zxing-java` | — | — | ✅ | — |
| `:engines:browser-detector` | — | — | — | ✅ |
| `:engines:mlkit-ocr` | ✅ | — | — | — |
| `:feature:scanner` | ✅ | ✅ | ✅ | ✅ |
| `:feature:history` | ✅ | ✅ | ✅ | ✅ |
| `:feature:settings` | ✅ | ✅ | ✅ | ✅ |
| `:composeApp` | ✅ | ✅ | ✅ | ✅ |

Los módulos de motor específicos de plataforma se agregan a `:composeApp` mediante dependencias
condicionadas por *source set* (`androidMain.dependencies { … }`), de modo que el binario de cada
plataforma solo enlaza lo que puede usar.

---

## 6. Modelo de dominio

### 6.1 Entidades principales

```kotlin
data class Barcode(
    val rawValue: String,
    val rawBytes: ByteArray?,
    val format: BarcodeFormat,
    val valueType: BarcodeValueType,   // interpretación semántica (RF-09)
    val cornerPoints: List<Point>?,    // normalizados a [0,1], para el overlay
    val confidence: Float?,            // 0..1, null si el motor no lo reporta
)

data class Detection(
    val barcode: Barcode,
    val engineId: ScannerEngineId,     // qué motor lo detectó (G5)
    val detectedAtMillis: Long,
    val latencyMillis: Long?,          // desde inicio de sesión hasta detección
)

data class HistoryEntry(
    val detection: Detection,
    val note: String?,                 // lo que el usuario escribió, después y a mano
)
```

**Tres niveles, y cada envoltura responde a una pregunta distinta:**

| | Qué es | Quién lo produce | Cuándo |
|---|---|---|---|
| `Barcode` | Qué dice el código | El mundo | Existe antes que la app |
| `Detection` | Quién lo leyó y cuándo | Un motor | En el instante de leerlo |
| `HistoryEntry` | Qué significa para el usuario | Una persona | Más tarde, y se reescribe |

`Detection` envuelve a `Barcode` en lugar de añadirle campos: el `Barcode` es lo que existe en el
mundo; la `Detection` es el evento de haberlo visto con un motor concreto en un instante. Esa
separación es la que habilita G5 (comparabilidad entre motores).

`HistoryEntry` envuelve a `Detection` por el mismo criterio llevado un paso más: una nota es un dato
**mutable, humano y posterior**, y meterla dentro de `Detection` obligaría a los ocho motores, a los
seis decoradores, al comparador y al marcador a acarrear un campo que en todo ese recorrido vale
siempre `null` — además de hacer que "estas dos lecturas son la misma" dependiera de si alguien
escribió algo. El razonamiento completo, y los tres defectos de persistencia que destapó, están en
[ADR-0012](adr/ADR-0012-la-nota-es-del-historial-no-de-la-deteccion.md).

### 6.2 Formatos soportados (G3)

| Familia | Formatos |
|---|---|
| 1D producto | EAN-8, EAN-13, UPC-A, UPC-E |
| 1D industrial | Code 39, Code 93, Code 128, Codabar, ITF |
| 2D matricial | QR Code, Data Matrix, Aztec, PDF417 |
| Postal / especial | MaxiCode, RSS/DataBar (según motor) |
| Extendido | Micro QR, rMQR (solo motores que lo declaren) |

`BarcodeFormat` es una jerarquía sellada con un caso `Unknown(rawName)` para formatos que un motor
reporte y el dominio aún no modele — un `enum` obligaría a descartar ese nombre original. Cada motor expone un mapper `PlatformFormat ↔ BarcodeFormat`
en su propio módulo; el dominio nunca ve constantes de SDK.

### 6.3 Interpretación semántica (RF-09)

`BarcodeValueType` es una `sealed interface` — `Url`, `Wifi`, `ContactInfo`, `Email`, `Phone`,
`Sms`, `GeoPoint`, `CalendarEvent`, `Product`, `Text`. El parseo vive en `:core:domain`
(`BarcodeValueParser`), **no** se delega al SDK: si dependiéramos del parser de ML Kit, un
resultado de ZXing tendría menos información que uno de ML Kit para el mismo código, y la
comparación entre motores dejaría de ser justa.

---

## 7. Scanner Engine SPI — el núcleo del diseño

Es el punto donde se juega el objetivo principal del producto (G2). Todo lo demás se subordina a
que este contrato sea correcto.

### 7.1 Contrato

```kotlin
interface BarcodeScannerEngine {
    val id: ScannerEngineId
    val descriptor: ScannerEngineDescriptor        // nombre, capacidades, plataformas

    suspend fun availability(): EngineAvailability

    /** Sesión de escaneo en vivo. Cancelar el Flow detiene la cámara. */
    fun scan(request: ScanRequest): Flow<ScanEvent>
}

/** Capacidad opcional: decodificar una imagen ya capturada (RF-07). */
interface ImageDecodingEngine {
    suspend fun decode(image: ScanImage, request: ScanRequest): Result<List<Barcode>>
}

/** Capacidad opcional: controles de cámara (RF-14). */
interface CameraControlEngine {
    suspend fun setTorch(enabled: Boolean)
    suspend fun setZoomRatio(ratio: Float)
}
```

**Decisiones justificadas**

- **`Flow<ScanEvent>` y no `suspend fun scan(): Barcode`.** El modo continuo (RF-10), el overlay
  en vivo y la detección múltiple necesitan un stream. Un `suspend` de un solo resultado obligaría
  a inventar un segundo camino para el modo continuo, y a duplicar la gestión del ciclo de vida.
  Con `Flow`, cancelar la corrutina apaga la cámara: el ciclo de vida es estructural, no manual.
- **Capacidades opcionales como interfaces separadas.** El GMS Code Scanner abre su propia UI y
  **no** permite controlar la linterna ni decodificar imágenes. Si `setTorch` estuviera en el
  contrato base, ese motor tendría que lanzar `UnsupportedOperationException` — un contrato que
  miente. Con interfaces segregadas, la UI hace `engine as? CameraControlEngine` y muestra el
  control solo si existe.
- **`availability()` es `suspend`.** Determinar disponibilidad puede requerir I/O: consultar si
  los módulos de ML Kit ya se descargaron, si Google Play Services está actualizado, o si el
  navegador expone `BarcodeDetector`.

### 7.2 Capacidades declarativas

```kotlin
data class ScannerCapabilities(
    val supportedFormats: Set<BarcodeFormat>,
    val sources: Set<ScanSource>,          // LiveCamera, StaticImage, ManualInput
    val supportsMultipleCodes: Boolean,
    val supportsContinuousScan: Boolean,
    val providesOwnUi: Boolean,            // el motor pinta su propia pantalla (GMS)
    val supportsTorch: Boolean,
    val supportsZoom: Boolean,
    val reportsCornerPoints: Boolean,
    val reportsConfidence: Boolean,
    val requiresCameraPermission: Boolean,
    val requiresNetwork: Boolean,
    val requiresRuntimeDownload: Boolean,  // ML Kit descarga modelos bajo demanda
)
```

Esto es lo que permite que la UI y el selector sean genéricos: la pantalla de catálogo (RF-03) se
renderiza desde estos datos, y el selector automático (RF-04) puntúa motores comparando
capacidades contra el `ScanRequest`. Añadir un motor nuevo no modifica ninguna de las dos.

### 7.3 Disponibilidad

```kotlin
sealed interface EngineAvailability {
    data object Available : EngineAvailability
    data class RequiresPermission(val permission: Permission) : EngineAvailability
    data class RequiresDownload(val sizeBytes: Long?) : EngineAvailability
    data class Unsupported(val reason: String) : EngineAvailability   // plataforma/hardware
    data class NotImplemented(val plannedPhase: Int) : EngineAvailability
    data class Failed(val error: ScanError) : EngineAvailability
}
```

`NotImplemented` es deliberado: permite que el **catálogo completo de motores exista desde la
Fase 1**, con la UI mostrando qué está listo y qué está planificado. El registro no cambia de
forma cuando un motor se implementa — solo cambia su respuesta de `availability()`.

### 7.4 Eventos de sesión

```kotlin
sealed interface ScanEvent {
    /** Todo evento sabe de qué motor viene; `null` solo si no lo produjo ninguno en concreto. */
    val engineId: ScannerEngineId?

    data object SessionStarted : ScanEvent
    data class Detected(val detections: List<Detection>) : ScanEvent
    data class FrameAnalyzed(val analyzedAtMillis: Long) : ScanEvent  // telemetría/FPS
    data class Failed(val error: ScanError) : ScanEvent
    data object SessionEnded : ScanEvent
}
```

Todos los eventos llevan el motor que los produjo. En una sesión normal es obvio — hay uno solo —
pero el comparador (§9.4) fusiona los streams de varios motores, y ahí un evento sin autor es un
dato perdido: las métricas de frames y de fallos por motor no se podían calcular.

`Failed` es un evento, no una excepción lanzada: un fallo transitorio (un frame corrupto) no debe
matar la sesión, y un fallo fatal se sigue de `SessionEnded`. Los errores del dominio se modelan
con `ScanError` (sealed), nunca con excepciones crudas.

### 7.5 Registro, selección y fallback

```
ScannerEngineRegistry            ← conoce todos los motores enlazados en el binario
   │
   ├─ expect fun platformEngines(): List<BarcodeScannerEngine>
   │     androidMain → [GmsCodeScanner, MlKitCameraX, ZXingCpp, MlKitOcr, Manual]
   │     iosMain     → [VisionScanner, ZXingCpp, MlKitOcr, Manual]
   │     jvmMain     → [ZXingCpp, Manual]
   │     wasmJsMain  → [BrowserDetector, Manual]
   │
   ▼
SelectScannerEngineUseCase       ← política de selección
   │   1. ¿El usuario fijó un motor?  → usarlo si availability == Available
   │   2. Filtrar por capacidades requeridas por el ScanRequest
   │   3. Ordenar por EnginePriority (por plataforma) y por cobertura de formatos
   │   4. Devolver cadena ordenada: [preferido, fallback1, fallback2, …]
   ▼
FallbackScannerEngine            ← decorador que recorre la cadena
       si el motor N emite Failed(fatal) o no está Available → intenta N+1
       emite EngineSwitched para que la UI lo comunique (G4)
```

El fallback es un **decorador** (`BarcodeScannerEngine` que envuelve una lista de motores) y no
lógica dentro del ViewModel. Consecuencia práctica: es testeable en `commonTest` con motores
falsos, sin cámara, sin dispositivo y sin Compose.

#### La cadena completa, y por qué el orden importa

`StartScanSessionUseCase` monta esto, y cada nivel está donde está por un motivo:

```
FallbackScannerEngine(
    motores.map { it.filteringFormats().enforcingRequestLimits().interpretingValues() }
).withDeadline().suppressingRepeats(time)
```

- **Por motor**: primero se filtra por formato lo que reporta, después se aplican los límites del
  request —cuántos códigos y si la sesión sigue— y solo lo que sobrevive se interpreta
  semánticamente. Envolver la cadena entera dejaría el fallback fuera del filtrado.
- **Sobre la cadena**: el plazo, porque si fuera por motor una cadena de tres tardaría el triple de
  lo que el usuario pidió; y la supresión de repeticiones, porque un código que un motor lee y otro
  vuelve a leer tras un fallback es **una** lectura repetida y no dos.

#### `DistinctDetectionsScannerEngine`: un defecto de datos, no de presentación

Ningún motor evita reportar el mismo código dos veces, y no es un descuido suyo: para ML Kit o Vision
un código que sigue delante de la lente es un código que sigue ahí. Una cámara analiza unos treinta
frames por segundo, así que con el escaneo continuo encendido **apuntar a un QR durante tres segundos
emitía ese código noventa veces**. Cada repetición se apilaba en la lista de resultados y —esto es lo
grave— **se escribía en el historial persistente**: exportar a CSV daba un archivo lleno de filas que
no correspondían a nada que hubiera ocurrido. No era ruido visual, era corrupción de los datos del
usuario.

La regla es una **ventana de tiempo** (dos segundos) y no "una sola vez por sesión", porque leer dos
veces el mismo código **es** un caso de uso —contar unidades iguales en un inventario, comprobar que
una etiqueta se lee bien—. La ventana distingue las dos situaciones sin preguntar: el código que no
se ha movido de delante de la cámara se ignora; el que se aparta y se vuelve a presentar se lee de
nuevo. Solo la lectura que pasa el filtro renueva la marca de tiempo — si la renovaran también las
suprimidas, un código sostenido ante la lente no volvería a leerse jamás.

Está **solo en la sesión en vivo**: `DecodeImageUseCase` monta su propia cadena sin él, porque en una
foto los códigos aparecen una vez; y el comparador tampoco lo lleva, donde es esencial, porque su
razón de ser es que **todos** los motores reporten el mismo código.

### 7.6 Coste de binario (RNF-06)

Cada motor es un módulo Gradle propio y se agrega desde el *source set* correspondiente de
`:composeApp`. Un target no enlaza SDKs que no puede usar: el binario de Desktop no contiene ML
Kit, el de iOS no contiene Google Play Services.

**Dentro de Android, en cambio, no se cumple**: el APK enlaza los cuatro motores de la plataforma
aunque el usuario use uno solo, y R8 no puede quitarlos porque están registrados explícitamente en
el grafo de Koin. Play Feature Delivery era la salida prevista y **se aplaza a conciencia**
([ADR-0009](adr/ADR-0009-play-feature-delivery-aplazado.md)): un módulo de característica dinámica
no puede ser un módulo KMP, el mecanismo solo se ejecuta distribuyendo por Play, y no hay ninguna
medición del APK con la que decidir qué conviene partir. Se retoma cuando haya distribución y
medición; hasta entonces el incumplimiento queda escrito y acotado en lugar de darse por resuelto.

---

## 8. Catálogo de motores

Detalle operativo completo en `docs/ENGINES.md`. Resumen:

| Motor | Plataforma | Fuente | Fortaleza | Limitación clave |
|---|---|---|---|---|
| **GMS Code Scanner** | Android | Cámara (UI propia) | Cero permisos, cero UI que mantener, modelo descargado por Play Services | UI no personalizable, sin linterna, sin modo continuo, requiere Play Services |
| **ML Kit + CameraX** | Android | Cámara (UI propia de la app) | Control total del preview, overlay, linterna, zoom, modo continuo | Añade peso; modelo *unbundled* requiere descarga |
| **Vision / AVFoundation** | iOS | Cámara | Nativo del sistema, sin dependencias externas, muy rápido | Solo iOS; el set de simbologías varía por versión de iOS |
| **ZXing-cpp** | Android, iOS, Desktop | Cámara e imagen | 100 % offline, mismo decodificador en todas las plataformas → **baseline de comparación justa** | Menos tolerante a códigos dañados o mal iluminados que ML Kit |
| **BarcodeDetector API** | Web (Wasm/JS) | Cámara e imagen | Cero peso, provisto por el navegador | Soporte desigual entre navegadores; requiere fallback a ZXing-cpp/Wasm |
| **ML Kit Text Recognition (OCR)** | Android, iOS | Cámara e imagen | Recupera códigos ilegibles leyendo el número impreso debajo | No es un decodificador: requiere validar checksum del formato inferido |
| **Entrada manual** | Todas | Teclado | Siempre disponible; red de seguridad final del fallback | Requiere intervención del usuario |

La presencia de **ZXing-cpp en las cuatro plataformas** no es redundante: es el control
experimental. Al ser el mismo decodificador en todas partes, cualquier diferencia de resultado
entre plataformas se atribuye a la captura de cámara, no al algoritmo — que es exactamente la
medición que hace útil a WhyScan.

---

## 9. Capa de presentación

### 9.1 Patrón MVI

```kotlin
data class ScannerState(
    val isLoading: Boolean = true,
    val availableEngines: List<EngineUIModel> = emptyList(),
    val selectedEngineId: ScannerEngineId? = null,
    val activeEngineId: ScannerEngineId? = null,   // puede diferir por fallback
    val formatFilter: Set<BarcodeFormat> = BarcodeFormat.all,
    val detections: List<DetectionUIModel> = emptyList(),
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val torchEnabled: Boolean = false,
    val error: ScanErrorUIModel? = null,
)

sealed interface ScannerAction {
    data object StartSession : ScannerAction
    data object StopSession : ScannerAction
    data class SelectEngine(val id: ScannerEngineId) : ScannerAction
    data class ToggleFormat(val format: BarcodeFormat) : ScannerAction
    data object ToggleTorch : ScannerAction
    data class DecodeImage(val image: ScanImage) : ScannerAction
    data object RequestPermission : ScannerAction
}

sealed interface ScannerEffect {          // one-shot, no forma parte del estado
    data class ShowMessage(val text: StringResource) : ScannerEffect
    data class NavigateToResult(val detectionId: String) : ScannerEffect
    data class OpenUrl(val url: String) : ScannerEffect
}
```

Convención obligatoria (heredada de los estándares del equipo):

- `XScreen` es *stateful* — recibe el ViewModel.
- `XContent` es *stateless* — recibe `state` y `onAction`, es 100 % previsualizable y testeable.
- Estado colectado con `collectAsStateWithLifecycle()` en Android; el `commonMain` usa el
  `ViewModel` multiplataforma de `androidx.lifecycle`.
- Ninguna lógica de negocio dentro de un `@Composable`.

### 9.2 Preview de cámara multiplataforma

El preview es el único punto donde la UI toca la plataforma. **No** es un `expect @Composable` en la
feature: es una **capacidad opcional del motor**, en la línea de `CameraControlEngine`. Ver
[ADR-0007](adr/ADR-0007-preview-como-capacidad-del-motor.md).

```kotlin
// :core:scanner-ui
interface CameraPreviewEngine {
    @Composable fun CameraPreview(modifier: Modifier)
}
```

La pantalla hace `(engine as? CameraPreviewEngine)?.CameraPreview(modifier)` y no nombra ningún
motor, de modo que añadir el de iOS o el del navegador no toca `:feature:scanner` (RNF-07).

| Target | Implementación |
|---|---|
| Android | `AndroidView` con `PreviewView` de CameraX |
| iOS | `UIKitView` con `AVCaptureVideoPreviewLayer` |
| Desktop | `Canvas` alimentado por frames de webcam |
| Web | `HtmlView` con `<video srcObject=getUserMedia()>` |

El overlay (marco de encuadre y esquinas detectadas) se dibuja **encima, en Compose común** sobre el
preview nativo — `ScanOverlay` en `:feature:scanner`. Así el 100 % del diseño visual es compartido y
solo la superficie de vídeo es nativa.

Para que ese overlay funcione igual en las cuatro plataformas, los `cornerPoints` viajan
**normalizados a `[0, 1]`** sobre el frame analizado: normaliza el motor, que es quien conoce el
tamaño real del frame, y mapea la UI, que es quien sabe cómo se está escalando el preview.

### 9.3 Navegación

Navegador propio mínimo (`sealed interface Destination` + backstack en un `StateFlow`), sin
dependencia externa. El grafo tiene **cuatro** destinos — escanear, comparar, historial y ajustes;
Android le cede el botón atrás del sistema y todas las plataformas usan la barra inferior. Razón: la
navegación multiplataforma de Jetpack está aún en versiones alpha/beta y no queremos que su ciclo de
releases bloquee el nuestro en la fase de fundaciones.

La revisión prevista se hizo y **la decisión se mantiene**: cuatro destinos y ningún deep link no
alcanzan el umbral de migración, que sigue siendo seis destinos o el primer deep link. Ver
`docs/adr/ADR-0005`.

**La lista de destinos cambia en caliente**, y eso trajo un caso que un navegador estático no tiene:
apagar el modo avanzado (ADR-0010) retira el comparador. Si el usuario lo tenía en pantalla se
quedaba en un destino que ya no sale en ninguna barra —sin ítem activo y sin forma de volver salvo el
botón atrás—, y apilar encima el escáner no servía: el atrás lo devolvía justo a donde no debía
estar. Lo resuelve `pruneTo`, que **poda el backstack entero** y no solo la cima, porque un destino
retirado enterrado más abajo tiene el mismo problema aplazado:

```kotlin
fun pruneTo(available: Collection<Destination>) {
    _backstack.update { stack -> stack.filter { it in available }.ifEmpty { listOf(initial) } }
}
```

El `ifEmpty` no es defensivo por costumbre: un backstack vacío es lo único que este tipo no puede
representar, porque `current` es `last()`.

La barra superior **no se dibuja en el escáner**. Una barra que dice "Escanear" encima de un visor de
cámara no añade información que el visor no esté dando ya, y se come la altura de lo único que
importa en esa pantalla; el ítem activo de la barra inferior dice dónde está el usuario. El
`Scaffold` sigue aportando los insets de la barra de estado en su `padding`, así que quitarla no mete
la cámara debajo del reloj.

El backstack sí se guarda y se restaura, que era la mitad de la deuda que sí resultó ser un defecto:

```kotlin
fun saveState(): List<String> = backstack.value.map { it.id }
fun restoreState(ids: List<String>)   // ignora lo que no reconoce
```

`Destination.id` está **escrito a mano**, no derivado del nombre de la clase. La razón es la misma
que en `BarcodeValueType.id`: R8 ofusca `::class.simpleName`, así que restaurar funcionaría en debug
y fallaría en release — el peor sitio donde descubrirlo. `MainActivity` lo guarda en
`onSaveInstanceState`; viaja como ids y no como objetos, con lo que `Destination` no necesita ser
`Parcelable` y el estado guardado no queda atado a su representación interna.

El caso que cubre no es la rotación: la Activity declara `configChanges` para orientación y tamaño
—a propósito, para no reiniciar la cámara al girar— así que rotar nunca la recreó. Cubre la muerte
del proceso en segundo plano y los cambios de configuración que la Activity no declara, como el
tamaño de letra o el idioma del sistema.

**El botón atrás se conecta con `BackHandler`, y el `enabled` es la pieza que importa:**

```kotlin
val backstack by navigator.backstack.collectAsState()
BackHandler(enabled = backstack.size > 1) { navigator.goBack() }
```

Antes era un `onBackPressedDispatcher.addCallback` siempre habilitado que, cuando no había nada que
desapilar, se autodesactivaba y volvía a lanzar `onBackPressed()`. Ese patrón deja de funcionar con
el *predictive back*, que viene activado por defecto desde `targetSdk` 36: el sistema decide qué
animación pintar **al empezar el gesto**, preguntando si hay algún callback habilitado. Con uno
siempre habilitado la respuesta era siempre "la vuelta es dentro de la app", y al soltar el dedo se
encontraba con que la Activity se cerraba. Además `isEnabled` no se restauraba nunca, así que el
callback quedaba muerto si la Activity sobrevivía.

Atar `enabled` al tamaño del backstack le dice al sistema de antemano cuál de las dos vueltas toca.
`Navigator` no se enteró del cambio: sigue siendo Kotlin puro y testeable sin Compose (ADR-0005), y
lo que se movió es solo el punto de conexión, que ahora vive junto a la UI en lugar de en `onCreate`.

---

## 10. Inyección de dependencias

**Koin 4.x.** Hilt no es multiplataforma (depende de procesamiento de anotaciones sobre el modelo
de componentes de Android), por lo que no es una opción aquí. Ver `docs/adr/ADR-0003`.

```
appModule              (composeApp)   → wiring raíz, arranca Koin
├── platformModule     (expect/actual) → motores de la plataforma, permisos, dispatchers
├── dataModule         (core:data)    → Registry, repositorios
├── domainModule       (core:domain)  → UseCases y sus agrupadores (ScanSettings, ScanSessions)
└── scannerModule      (feature:scanner) → ViewModels
```

Convenciones: constructor injection siempre; ningún `Context` en ViewModels. Los tests de ViewModel
sustituyen el dispatcher principal con `Dispatchers.setMain` de `kotlinx-coroutines-test`, no con un
`DispatcherProvider` inyectado: no hay ninguno en el proyecto y no ha hecho falta, porque los
ViewModels usan `viewModelScope` y el resto del dominio es `suspend` sin dispatcher propio.

### Koin resuelve por **igualdad exacta de tipo**, y eso muerde

Es la lección más cara de esta versión y merece estar aquí y no solo en el registro de deudas.
`platformModule` de Android declaraba el executor de análisis de frames así:

```kotlin
single<ExecutorService> { Executors.newSingleThreadExecutor() }   // ← declarado
```

mientras los tres motores de cámara lo piden así:

```kotlin
class MlKitCameraXEngine(context: Context, analysisExecutor: Executor, ...)   // ← consumido
```

`ExecutorService` **es** un `Executor`, pero Koin indexa cada definición por el tipo con el que se
declara y no recorre supertipos al resolver. El `get()` no encontraba nada, el primer motor de la
lista no se podía construir, y la cascada se llevó por delante la `List<BarcodeScannerEngine>`, el
`ScannerEngineRepository`, los casos de uso y el `ScannerViewModel`: la app moría al componer la
primera pantalla con `NoDefinitionFoundException`.

Lo que hace este fallo digno de documentar no es el error en sí, que es de una línea, sino **quién
podía haberlo detectado y no pudo**:

- El **compilador** no: los `get()` son genéricos resueltos en ejecución, así que declarar de más o
  de menos es indistinguible en tiempo de compilación.
- El **CI** tampoco: compiló, pasó lint, pasó R8 y publicó un APK que reventaba al abrirse.
- Los **tests** tampoco: los de dominio inyectan sus dobles a mano y nunca tocan el grafo real.

La regla que queda: **declarar el tipo que se consume, no el que devuelve la fábrica.**

### La comprobación que faltaba ya existe: `KoinGraphTest`

D18 pasó de deuda a test. `composeApp/src/desktopTest` arranca el grafo real —`platformModule()` de
escritorio más los cinco módulos comunes— y **resuelve** cada tipo que la raíz de la app pide,
agrupado por el ViewModel que lo consume. No inspecciona definiciones por reflexión: instancia, que
es estrictamente más fuerte. Si algo está declarado con el tipo equivocado, `get()` lanza en CI igual
que lanzaba en el teléfono.

Lo que **no** cubre, dicho para que no se confunda con una red completa:

- Es el `platformModule` de **escritorio**, que es el que un test JVM puede enlazar sin más. El de
  Android tiene ahora el suyo —ver abajo—. Lo que cubren los dos a la vez, para las cuatro
  plataformas, son `dataModule`, `domainModule` y los tres módulos de feature.
- No construye los ViewModels: instanciarlos arranca corrutinas en `viewModelScope`, que exige un
  `Dispatchers.Main` real. Comprueba que **todo lo que piden por constructor** resuelva, que es
  exactamente donde falló D18. Añadir un parámetro a un ViewModel obliga a añadirlo también a la
  lista del test, y esa fricción es deliberada.

**Encontró un defecto en su primera ejecución**, y no en el cableado de Koin sino en la persistencia
(§11): la base de datos nunca recibía su driver. Es la mejor defensa posible de por qué este test
tenía que existir — no comprobaba una hipótesis, destapó algo que llevaba meses ahí.

### El grafo de Android, con Robolectric

`AndroidKoinGraphTest` cierra la otra mitad, que es **la mitad donde ocurrió el crash**. El
`platformModule` de Android es con diferencia el mayor de los cuatro —cuatro motores de cámara, el
`Executor`, el controlador de permisos, tres servicios del sistema y las preferencias sobre
`SharedPreferences`— y que resuelva el de escritorio no dice absolutamente nada de él.

Necesita un `Context` de verdad: `SharedPreferencesSettings` llama a `getSharedPreferences` y eso no
lo satisface un doble. **Robolectric da ese `Context` en la JVM**, así que el test corre en el mismo
job que todos los demás, con `:composeApp:testDebugUnitTest`.

Conviene decir por qué esto no contradice la decisión de no tener tests instrumentados (§13.1). Aquel
argumento era concreto: sin emulador en CI, un test que exija dispositivo nunca se ejecuta y da una
falsa sensación de red. Esto es lo contrario — un test que sí se ejecuta en cada PR. Lo que se
mantiene es lo que importaba: nada de esto necesita hardware.

Queda un hueco y está acotado: **no toca el historial persistente**. `sqlite-bundled` trae binarios
nativos de las ABI de Android y bajo Robolectric el proceso es una JVM de escritorio, así que no los
puede cargar. No es un hueco de cableado —esa misma cadena se resuelve de verdad en el test de
escritorio, y fue ahí donde se destapó lo del driver—; lo único sin cubrir es el `actual` de Android
de `DatabaseBuilderFactory`, que son cuatro líneas y sigue necesitando un dispositivo.

**Un caso de uso por operación no es una regla**, y esta versión es la primera en aplicarlo *antes*
de repetir el error en vez de después. Añadir la nota y el borrado por fila pedía dos clases nuevas de
una línea al lado de `ObserveScanHistoryUseCase` y `ClearScanHistoryUseCase`, que ya delegaban sin
añadir nada: cuatro nombres, cuatro registros en Koin y cuatro parámetros en el constructor del
ViewModel para una sola idea. Los dos que había se borraron y su trabajo vive en `ScanHistory`, junto
con la única regla que hay dentro —normalizar la nota en un sitio para que las tres plataformas
guarden lo mismo—. `SaveDetectionUseCase` se queda fuera y no es una inconsistencia: lo llama el
escáner al leer un código, que es otro camino y no quiere arrastrar el borrado ni las notas.

La historia original, que es de donde salió el criterio: `ScannerViewModel` llegó a tener doce
colaboradores por seguirla al pie de la letra, y cuatro de ellos eran la misma idea: tres casos de
uso de una línea sobre `ScanPreferencesRepository` más el propio repositorio, inyectado aparte
porque dos operaciones no tenían caso de uso. La corrección (deuda D16) fue en dos direcciones:

- **Borrar** los que solo delegaban —los tres de preferencias y `ObserveEngineCatalogUseCase`—.
  Un caso de uso que no añade una regla añade un nombre, y el nombre ya lo daba el repositorio. La
  única regla que había, que un conjunto de formatos vacío significa *todos*, se conservó en
  `ScanSettings`.
- **Agrupar** los que sí tienen lógica y se usan siempre juntos: `ScanSessions` reúne arrancar,
  decodificar y guardar, y se lleva consigo la traducción de preferencias a `ScanRequest`.

El criterio que queda para el futuro: un caso de uso existe si guarda una regla, no si existe una
operación. Y agrupar colaboradores es un cambio de dominio, no de UI — por eso ambos viven en
`:core:domain` y no en la feature.

---

## 11. Persistencia

| Dato | Almacén | Estado |
|---|---|---|
| Motor preferido, filtros de formato, ajustes | **`multiplatform-settings`** en las cuatro plataformas | ✅ implementado |
| Historial de escaneos (RF-11) | **Room KMP** en Android, iOS y Desktop; JSON en `localStorage` en Web | ✅ implementado |

El historial se definió en la Fase 1 como **interfaz de repositorio** (`ScanHistoryRepository`) con
una implementación en memoria detrás. Sustituirla por Room en la Fase 2 no tocó ni el dominio ni la
UI: solo cambió el binding de Koin. Era exactamente la apuesta que justificaba definir el contrato
antes que el almacén.

Las preferencias sí cubren las cuatro plataformas — `multiplatform-settings` mapea a
SharedPreferences, NSUserDefaults, `java.util.prefs` y `localStorage`. Su almacén es síncrono, así
que la parte observable es un `StateFlow` hidratado al construir y escrito en cada cambio: no se usa
la API de flujos de la librería, que sigue siendo experimental y no aporta nada mientras nadie más
escriba en esas claves.

**Room KMP no tiene target wasmJs.** Es una limitación real de la librería, no una decisión de
diseño, y tiene dos consecuencias que conviene tener presentes:

- `:core:database` declara tres targets en lugar de cuatro.
- `ScanHistoryRepository` **no** se declara en `dataModule`: lo aporta cada `platformModule`. La
  diferencia queda visible en el wiring en lugar de escondida tras un `expect/actual` que fingiera
  que todas las plataformas hacen lo mismo.

En Web lo aporta `SettingsScanHistoryRepository`, que serializa la lista a JSON en el mismo almacén
que las preferencias — `localStorage`. No es Room, pero tampoco es memoria: recargar la página ya no
pierde el historial. La alternativa ortodoxa era IndexedDB y se descartó a conciencia: unos cientos
de filas de texto ocupan decenas de kilobytes frente a los megabytes de cuota, este repositorio no
hace consultas —lee la lista entera y filtra en memoria— y la interop de IndexedDB serían cien líneas
de callbacks que ningún test de `commonTest` puede ejercitar. Así corre con `MapSettings`. Lo que sí
exige el almacén es techo (500 entradas) y tolerancia a que la escritura falle por cuota: una
detección que no cabe se sigue mostrando en pantalla, porque convertir "no cabe" en un escaneo
fallido sería peor.

Decisiones del esquema:

- Los enums se persisten por su `id` estable, nunca por `name` ni por ordinal: renombrar una
  constante de Kotlin no debe invalidar el historial del usuario.
- Una fila cuyo motor ya no existe en el catálogo se **ignora al leer** en lugar de romper el
  historial entero.
- Se usa el driver **bundled** de SQLite y no el del sistema, para que las tres plataformas corran
  la misma versión del motor. Con el driver del sistema, una consulta podría comportarse distinto en
  Android 24 que en iOS 17 — y este proyecto existe para comparar plataformas, no para pelearse
  con ellas.
- No se guarda ningún píxel: la entidad no tiene dónde (RNF-03).

### Migraciones: el primer cambio de esquema habría borrado el historial

Añadir la columna `note` (ADR-0012) obligó a mirar por primera vez qué hace esta base ante un cambio
de versión, y la respuesta era **borrarla entera**: se construía con
`fallbackToDestructiveMigration(dropAllTables = true)`. Mientras solo hubo una versión no se notó
—no había ningún salto que dar—, pero el primer `version = 2` habría vaciado el historial de todos
los usuarios en silencio, sin registro y sin recuperación posible. En una app **sin cuenta, sin copia
en la nube y sin papelera** ese historial es el único sitio donde esos datos existen; y la versión que
lo habría provocado es justo la que invita a anotarlos.

Queda así:

| | Antes | Ahora |
|---|---|---|
| Subir de versión | borra todo | `@AutoMigration(from = 1, to = 2)` |
| Bajar de versión | borra todo | borra todo — no hay alternativa |
| Esquema sin migración declarada | borra todo | **falla al abrir**, que es un fallo de desarrollo y se ve en la primera prueba |

Añadir una columna que admite `null` es exactamente lo que `@AutoMigration` resuelve sola a partir de
los esquemas exportados a `core/database/schemas/`, que ya se exportaban desde la Fase 2 sin que
nadie los usara para nada. La bajada de versión sí se queda destructiva: el código no puede conocer
un esquema del futuro, y en la práctica ocurre al saltar entre ramas en desarrollo.

#### Y ahora la migración se ejecuta, no solo se valida

Room genera `@AutoMigration` y la valida **en compilación** contra los esquemas exportados. Es
bastante: garantiza que el SQL es correcto y que el esquema resultante coincide con el declarado.

No garantiza lo único que le importa al usuario. **Un esquema correcto es perfectamente compatible
con haber borrado la tabla y haberla vuelto a crear** — que es literalmente lo que este proyecto
hacía hasta la versión anterior. Un test que validara el esquema le habría dado el visto bueno a la
migración destructiva.

Por eso `MigrationTest` (`:core:database`, `jvmTest`) no mira el esquema: **levanta una base v1 de
verdad, le escribe filas y comprueba que siguen ahí después de abrirla con el código v2.** La v1 se
construye con el `createSql` literal de `schemas/…/1.json` más las dos cosas con las que Room
reconoce una base como suya —el `room_master_table` con el `identityHash` de esa versión y el
`PRAGMA user_version`—, así que si alguien tocara el esquema v1 a posteriori el hash dejaría de
cuadrar y el test lo diría.

**La mitad simétrica está en el navegador.** Ahí no hay Room ni migraciones: el DTO guardado gana un
campo `note` **con valor por defecto**, de modo que un historial escrito por una versión anterior
—donde esa clave no existía— sigue decodificando. Sin el defecto, `load()` habría descartado el
historial entero al primer fallo de deserialización, que es su comportamiento correcto ante datos
ilegibles y aquí habría sido igual de destructivo. Las dos plataformas migran o ninguna.

### Reinsertar una lectura borraba su nota

El DAO usaba `@Insert(onConflict = REPLACE)`, y en SQLite `REPLACE` es un borrado seguido de un alta.
El id de una detección es determinista —motor, instante y valor—, así que volver a leer el mismo
código en el mismo milisegundo reemplazaba la fila y **se llevaba la nota por delante**.

Pasa a `IGNORE`. Una fila en conflicto es por construcción la misma lectura, con los mismos campos de
máquina; lo único que pudo cambiar es lo que escribió el usuario. Ignorar el alta es igual de
idempotente y no destruye nada. Es además lo que ya hacía `InMemoryScanHistoryRepository`, que
comprobaba el id antes de añadir: las tres implementaciones coinciden ahora en la misma regla, que es
la condición para que los historiales de las cuatro plataformas sigan siendo comparables.

### La poda distingue lo que el usuario anotó

`trimTo(500)` borraba por antigüedad. Con notas, eso es perder sin avisar lo único del historial que
alguien se molestó en escribir a mano. La cláusula pasa a ser `WHERE note IS NULL`, y sus dos gemelas
de `:core:data` comparten `trimmedKeepingNotes`. El techo sigue acotando lo que genera volumen —una
sesión continua deja cientos de lecturas y ninguna nota— y deja de tocar lo demás.

### El driver no se aplicaba: una extensión tapada por un miembro

La garantía del punto anterior **llevaba siendo falsa desde que se escribió**, y lo destapó
`KoinGraphTest` (§10) en su primera ejecución:

```
java.lang.IllegalArgumentException: Cannot create a RoomDatabase without
providing a SQLiteDriver via setDriver().
```

`DatabaseBuilderFactory` declaraba una **extensión** para configurar el driver bundled, el dispatcher
de consultas y la migración destructiva:

```kotlin
fun RoomDatabase.Builder<ScanDatabase>.build(): ScanDatabase = …   // ← nunca se ejecutó
```

En Kotlin **un miembro siempre gana a una extensión**, y `RoomDatabase.Builder` ya tiene su propio
`build()`. Los tres `platformModule` escribían `.create().build()` creyendo que pasaban por ahí y
llamaban al de Room.

Las consecuencias eran **distintas en cada plataforma**, y eso es lo que lo mantuvo escondido:

- **Escritorio e iOS reventaban** al tocar el historial. Y como el escáner necesita
  `SaveDetectionUseCase`, eso es al abrir la primera pantalla.
- **Android funcionaba**, cayendo al SQLite del framework cuando Room no recibe driver — es decir,
  usando justo el driver que ese código existe para evitar. Es el peor caso posible: la plataforma
  que sí se probaba a mano era la única que ocultaba el fallo.

**El compilador lo avisaba en cada build** desde el principio, y nadie leía el aviso:

```
w: This extension is shadowed by a member: 'fun build(): T'
```

La extensión pasa a llamarse `buildBundled()`, que no puede colisionar. La lección general no es
"cuidado con las extensiones" sino que **un aviso del compilador que nadie lee es un aviso que no
existe**; el ROADMAP lo registra como deuda para decidir qué hacer con los avisos del build.

---

## 12. Permisos y privacidad

```kotlin
interface PermissionController {
    suspend fun status(permission: Permission): PermissionStatus
    suspend fun request(permission: Permission): PermissionStatus
    fun openAppSettings()
}
```

| Target | Implementación |
|---|---|
| Android | `ActivityResultContracts.RequestPermission` vía un holder de Activity |
| iOS | `AVCaptureDevice.requestAccessForMediaType` |
| Desktop | Sin permiso explícito (lo gestiona el SO al abrir la webcam) |
| Web | Implícito en `getUserMedia()`; se modela el rechazo del usuario |

Garantías de privacidad (RNF-03), verificables en revisión de código:

- Ningún frame se escribe a disco ni se envía por red. Los motores procesan en memoria.
- El historial guarda el **valor decodificado**, nunca la imagen.
- El caso `RequiresDownload` (ML Kit) se comunica explícitamente al usuario antes de descargar.
- La app declara `android:usesCleartextTraffic="false"` y no incluye SDK de analítica de terceros.

#### Auditoría, con lo que se comprobó y lo que salió

No basta con enumerar garantías: lo que sigue es el resultado de buscarlas en el código, incluidos
los dos hallazgos.

| Se comprobó | Resultado |
|---|---|
| Ninguna traza escribe lo escaneado | No hay una sola llamada a `println`, `Log`, `console.log` ni `printStackTrace` en todo el repositorio |
| Ningún cliente HTTP | No hay Ktor, OkHttp, Retrofit ni `URLConnection`; el catálogo de versiones tampoco los declara |
| Ninguna analítica | Sin Firebase, Crashlytics ni equivalentes, ni en código ni en el catálogo |
| Permisos declarados | Solo `CAMERA`, con `uses-feature` no obligatorio. **No se declara `INTERNET`**, que es la garantía más fuerte: aunque alguien añadiera una llamada de red, en Android no saldría |
| Lo que se persiste | La tabla de Room y el DTO de Web guardan valor, formato, motor, fuente, instante y latencia. No hay campo donde quepa un píxel |
| Lo que sale del dispositivo | Solo por acción explícita del usuario: compartir, abrir un enlace o exportar el historial a un archivo que él elige |

**Hallazgo 1 — `fetch` en el motor de Web.** El decodificador del navegador llama a `fetch`, que es
exactamente lo que una auditoría busca. Resultó ser sobre un **data URL** construido en el momento a
partir de los bytes que ya están en memoria: `createImageBitmap` necesita un `Blob` y esa es la vía
sin arrastrar `kotlinx-browser`. No sale nada del dispositivo. Aun así se añadió un guardia que
rechaza cualquier URL que no empiece por `data:`, para que la propiedad se compruebe leyendo cuatro
líneas en vez de razonando sobre el llamante.

**Hallazgo 2 — falta declarar la ausencia de red.** No declarar `INTERNET` ya impide la salida en
Android, pero es una garantía silenciosa: no aparece en ninguna parte y el próximo que añada una
dependencia puede reintroducirla sin darse cuenta. Queda anotado aquí como la invariante que hay que
defender.

---

### 12.1 Accesibilidad (RNF-05)

El requisito pedía tres cosas: contraste AA, objetivos táctiles ≥ 48 dp y lectores de pantalla en
los resultados. Estado de cada una:

**Contraste.** Deja de ser una intención y pasa a ser un test. La paleta vive en `ScannerPalette`,
que **no depende de Compose**, y `Contrast` implementa la fórmula de WCAG 2.1; `ContrastTest` mide
**56 pares** —50 contra el umbral de texto normal (4.5:1) y 6 contra el de componentes no textuales
(3.0:1)— sobre los dos esquemas. Corre en `commonTest`, sin renderizar y sin dispositivo. Se miden
además los pares que **la UI usa de hecho** —`primary`, `tertiary` y `error` como color de texto
sobre la tarjeta—, que ninguna convención de Material cubre.

El umbral aparte de 3.0:1 existe para `outline`, que es el borde de un `OutlinedButton` o de un campo
de texto: transmite información —dónde termina el control— pero no es texto, y exigirle 4.5 obligaría
a un borde tan oscuro que la UI parecería un formulario de los noventa.

La lista de pares se declara **una sola vez** y se aplica a los dos esquemas. Antes estaba escrita
dos veces y había un test cuyo único trabajo era cazar el día en que una de las dos copias se quedara
corta; ahora ese caso no puede ocurrir y el test protege de que alguien vuelva a separarlas.

**El mismo defecto, dos veces.** Al extraer la paleta apareció que solo se declaraban `primary`,
`secondary` y `tertiary`, así que los catorce roles `on*` se quedaban en los valores de fábrica de
Material —una paleta morada que no es esta— y el texto de un botón primario en modo oscuro salía
morado. Se arreglaron los `on*`… y **volvió a pasar con los `*Container`**, que es lo que pinta un
`FilterChip` seleccionado, la `Card`, el `NavigationBar` y el indicador del ítem activo: todos salían
morados en una app cuya marca es azul. La conclusión no es "declarar más roles" sino que
`lightColorScheme()` rellena **todo** lo que no se le pase, así que la única postura estable es
declarar los ~30 roles y que no quede ninguno al azar.

**Lectores de pantalla.** Cuatro arreglos, todos por el mismo motivo: había información que solo
existía como posición o como color.

- El **visor** no producía semántica alguna: ni la superficie nativa ni el `Canvas` del overlay. Se
  describe con cuántos códigos hay dentro.
- El **estado de la sesión** es una región viva (`liveRegion`), así que arrancar, degradar de motor
  y terminar se anuncian solos. La degradación es justo lo que el objetivo G4 quiere hacer visible.
- Los **botones de acción** repetían etiqueta en cada resultado: con cinco lecturas en pantalla, un
  lector decía "Copiar" cinco veces sin decir qué. Ahora la descripción lleva el valor dentro.
- El **interruptor de escaneo continuo** y su etiqueta se fusionan en un nodo; por separado, el
  lector enfocaba el `Switch` y decía "activado" sin decir activado qué. El **slider de zoom** gana
  nombre y estado, para que lea "Zoom de la cámara, 3×" en lugar de un porcentaje suelto.

**Objetivos táctiles.** Todo lo pulsable son componentes de Material 3, que aplican
`minimumInteractiveComponentSize` (48 dp) por su cuenta; no hay ni un `Modifier.clickable` propio en
el repositorio, que es donde se rompería. No está medido sobre un dispositivo, y eso no cambia
mientras no haya emulador.

**Lo que añadió el rediseño de la pantalla de escaneo** (ADR-0010), en la misma línea de "había
información que solo existía como posición o como color":

- Los tres estados que sustituyen al visor —cargando, permiso, sin cámara— se fusionan en **un solo
  nodo** (`mergeDescendants`): icono, título y explicación son una sola idea, y por separado obligan
  a tres gestos para enterarse de una cosa.
- La lectura destacada es una **región viva**, porque "es la más reciente" se expresaba solo con la
  posición y el color del contenedor, y quien usa un lector de pantalla no ve ninguna de las dos.
- La píldora de estado sobre el visor usa `inverseSurface`/`inverseOnSurface` y no un blanco
  translúcido: sobre vídeo hay que garantizar contraste **sin saber qué está enfocando la cámara**, y
  ese par sí lo mide `ContrastTest`.
- El valor leído va en **monoespaciada** (`CodeValueStyle`). No es estética: es un dato que se coteja
  carácter a carácter contra una etiqueta impresa, y en una proporcional `1`, `l` e `I` se confunden.

---

## 13. Estrategia de calidad

### 13.1 Testing

| Nivel | Ubicación | Qué cubre | Herramientas |
|---|---|---|---|
| Unitario de dominio | `commonTest` | UseCases, política de selección, fallback, parser semántico, mappers de formato | kotlin-test, Turbine |
| Unitario de presentación | `commonTest` | Reducers de ViewModel: acción → estado esperado | kotlin-test, Turbine, dispatcher de test |
| Contrato de motor | `commonTest` | **Suite compartida** que todo motor debe pasar (§13.2), aplicada a lo instanciable sin dispositivo: el motor manual, los decoradores y la cadena completa | kotlin-test |
| Coherencia del catálogo | `commonTest` | Que los ocho descriptores sean válidos y no prometan lo que nadie implementa | kotlin-test |
| Decodificación real | `jvmTest` | ZXing (Java) decodificando imágenes que el propio ZXing genera en el test | kotlin-test |
| **Grafo de dependencias** | `desktopTest` | Que el grafo real de Koin **resuelva**: arranca los módulos y pide cada tipo que la raíz de la app consume (`KoinGraphTest`, §10) | kotlin-test, koin-core |
| Contraste de la paleta | `commonTest` | 56 pares de color contra su umbral WCAG, sobre los dos esquemas (§12.1) | kotlin-test |
| Notas, búsqueda y borrado | `commonTest` | Que anotar, buscar por nota, borrar una fila y confirmar el vaciado hagan lo que dicen, y que la poda no se lleve lo anotado | kotlin-test, turbine |
| **Grafo de Android** | `androidUnitTest` | Que el `platformModule` de Android resuelva, con un `Context` real en la JVM (`AndroidKoinGraphTest`, §10) | kotlin-test, Robolectric |
| **Migración de la base** | `jvmTest` | Que una base v1 con filas dentro siga teniéndolas tras abrirla con el código v2 (`MigrationTest`, §11) | kotlin-test |

Objetivo de cobertura: **≥ 80 % en `:core:domain` y `:core:data`**; la UI no se persigue por
cobertura sino por casos de estado representativos.

**No hay tests instrumentados, y es una decisión, no una omisión.** No va a haber emulador en CI, así
que un test que exija dispositivo es un test que nunca se ejecuta: no aporta seguridad y sí una falsa
sensación de tenerla. El hueco que esto deja —que ningún test comprueba que un motor de cámara lea un
código de verdad— está escrito con todas las letras en el ROADMAP, junto a lo que sí queda cubierto
sin dispositivo.

**Lo que esa decisión no debía dejar fuera, y dejó: que el grafo de dependencias resuelva.** El
primer arranque en un dispositivo real murió por un `Executor` declarado como `ExecutorService`
(§10), y ninguno de los tests de dominio lo habría visto: inyectan sus dobles a mano y nunca montan
el grafo. Es un caso distinto del de la cámara — ahí hace falta hardware, aquí no hace falta nada.

**Eso ya no está pendiente, y ahora por las dos mitades.** `KoinGraphTest` cubre los módulos comunes
y el `platformModule` de escritorio —y en su primera ejecución destapó el driver de la base de datos
que nunca se aplicaba (§11)—; `AndroidKoinGraphTest` cubre el de Android, que es donde estaba el
defecto original.

**El segundo obligó a matizar D6, y el matiz merece quedar escrito.** "No hay tests instrumentados"
se leía como "nada que diga Android", y no era eso: el argumento era que sin emulador en CI, un test
que exija dispositivo nunca se ejecuta y da una falsa sensación de red. Robolectric no exige
dispositivo — levanta el `Context` en la misma JVM que el resto de los tests. La regla, dicha con
precisión, es **que todo lo que este proyecto comprueba se pueda ejecutar en cada PR**; lo que la
incumple es el hardware, no el nombre de la plataforma.

La tabla de arriba dejaba ver un patrón que conviene no perder de vista aunque haya mejorado: **casi
todo lo que se comprueba son piezas, y muy poco comprueba el montaje.** El criterio de salida de la
Fase 1 dice "la app arranca en Android, Desktop y Web"; hoy hay un test que comprueba que el grafo se
monta, pero **sigue sin haber nada que ejecute la app**. Lo que cambió es dónde está exactamente la
frontera, no que haya desaparecido.

#### Un test que falla tiene que decir por qué

Encontrar el defecto del driver fue imposible hasta arreglar algo previo: con la salida por defecto
de Gradle, un fallo en CI aparecía como `java.lang.IllegalArgumentException at KoinGraphTest.kt:189`
— tipo de excepción y línea, **sin mensaje y sin causa**. Sobre un fallo de cableado, donde el
mensaje *es* toda la información, eso obliga a descargar el informe HTML del artefacto o a adivinar y
relanzar el build.

El `build.gradle.kts` raíz configura ahora `testLogging` con `exceptionFormat = FULL` y las causas,
para todos los proyectos y solo en el evento `failed`. Vale para todo el repositorio y no solo para
aquel fallo: hasta esta versión, **ningún test roto había dicho nunca por qué se rompía**.

### 13.2 Suite de contrato de motores

Pieza clave de la arquitectura, ya implementada en `:core:scanner-testing`: una batería de tests
abstracta — `abstract class BarcodeScannerEngineContractTest` — que verifica que *cualquier* implementación
respeta el SPI: que `availability()` es idempotente, que el `Flow` emite `SessionStarted` primero
y `SessionEnded` siempre, que la cancelación libera la cámara, que los formatos reportados están
dentro de los declarados en `capabilities`. Cada motor nuevo hereda la clase y aporta su factory.
Esto convierte "añadir un motor" en un proceso con red de seguridad automática. No es teórico: en su
primer uso la suite detectó una carrera real en el motor de entrada manual, que perdía en silencio
los valores enviados antes de que la sesión se suscribiera.

#### Se aplica también a los decoradores

Un decorador **es** un motor, así que pasa el mismo contrato. Y es donde más falta hace: los tres
fallos de contrato que ha tenido este proyecto estaban en decoradores y no en motores — un
`awaitClose` que impedía terminar el `Flow`, la supresión de `SessionEnded` en la cadena de fallback
y unos límites de petición que dejaban la sesión abierta para siempre. Los motores de cámara
necesitan un dispositivo para ejercitarse; los decoradores corren en `commonTest`, incluida **la
cadena completa** que monta `StartScanSessionUseCase`, que es la que llega de verdad al ViewModel.

#### Lo declarado tiene que tener quien lo cumpla

La suite comprueba que una capacidad declarada en el descriptor la implemente alguien: si dice
linterna, alguien es `CameraControlEngine`; si dice imagen estática, alguien es `ImageDecodingEngine`.
Es la clase de fallo que más veces ha aparecido aquí —algo declarado que ningún código sirve— y la
UI depende directamente de ello: dibuja el control leyendo el descriptor.

Al aplicarla a los decoradores apareció el caso real: la cadena de fallback copia el descriptor del
primer motor —linterna incluida— pero un `as? CameraControlEngine` sobre ella daba `null`, porque
quien la implementa es el motor de dentro. De ahí salió `DecoratingScannerEngine` y la función
`capability()`, que atraviesa la cadena en lugar de hacer un cast sobre la capa de fuera.

### 13.3 Análisis estático

- **Detekt** con `detekt-formatting` (ktlint embebido), configuración compartida en
  `config/detekt/detekt.yml`, ejecutado sobre todos los módulos. Build falla ante nuevos issues.

  Hasta el primer CI real **no analizaba ni un archivo**: la fuente por defecto de la tarea es
  `src/main/kotlin`, que es el layout de un proyecto JVM, y aquí el código vive en
  `src/commonMain/kotlin` y sus hermanos. Pasaba en verde porque no miraba nada, que es peor que no
  tenerlo. Ahora la tarea apunta a `src` entero. Al encenderlo aparecieron 105 hallazgos, todos
  resueltos: los umbrales **no** se subieron hasta que cupiera lo que había —eso deja la regla
  midiendo siempre lo que sea que haya—, sino que las cuatro excepciones legítimas llevan
  `@Suppress` en su sitio con el motivo al lado del código.
- **Reglas de arquitectura** verificadas en CI: `:core:domain` no puede depender de Compose ni de
  Android; `:engines:*` no puede depender de `:feature:*`.
- **SonarCloud** para deuda técnica y duplicación; sin regresión permitida en PR.
- Compilación con `allWarningsAsErrors` en módulos `:core:*`.

### 13.4 Qué encontró el primer CI

Merece su propia sección porque es el dato más útil que ha producido el proyecto sobre sí mismo.

Hasta que se habilitó Actions, nada se había compilado con Gradle: el entorno de desarrollo no
alcanza el maven de Google. Lo que sí había era un arnés sobre kotlinc que compilaba y ejecutaba el
núcleo puro —358 tests— y que atrapó bugs reales durante meses. Cuando por fin corrió Gradle
aparecieron **doce fallos encadenados**, cada uno tapado por el anterior:

| # | Dónde | Qué era |
|---|---|---|
| 1 | `build-logic` | Los convention plugins declarados `compileOnly`. Válido para clases `Plugin<Project>`, no para scripts precompilados |
| 2 | Raíz | Kotlin/Wasm aplica `LifecycleBasePlugin` **al proyecto raíz** y chocaba con un `clean` escrito a mano |
| 3 | `:core:database` | KSP 2.3.10 exige AGP ≥ 8.10 (era el riesgo R11, anotado de antemano) |
| 4 | Todos los módulos con Compose | CMP 1.11.1 no publica `iosX64`, y declarar ese target rompía la resolución de `commonMain` |
| 5 | `:core:scanner-testing` | `kotlin.test.Test` no resuelve en la JVM sin la variante de framework |
| 6 | `:core:designsystem` | `staticCompositionLocalOf { error(...) }` se infiere como `Nothing`, así que `showSnackbar` no existía en **ninguna** pantalla |
| 7 | Tres pantallas | Faltaba `import androidx.compose.runtime.getValue` para el delegado `by` |
| 8 | `:core:domain` | La dependencia con `:core:scanner-testing` estaba en el SDD y no en el build |
| 9 | Dos pantallas | `resolve()` era `@Composable` y se llamaba dentro de un `LaunchedEffect` |
| 10 | `:core:database` | Room como `implementation` cuando los tipos públicos del módulo heredan de él |
| 11 | Web | Tres repositorios de herramientas (Node, Yarn, Binaryen) que el plugin declara a nivel de proyecto |
| 12 | `:engines:browser-detector` y `:androidApp` | `ScanError.PermissionDenied` construido sin argumentos, y otro `implementation` que debía ser visible |

**Lo que esto dice del arnés local.** No fue inútil: los 358 tests que ejecutaba siguen pasando sin
un solo cambio, y los defectos que encontró eran de lógica de verdad. Pero su cobertura tenía una
forma muy concreta —`commonMain` compilable como JVM plano— y todo lo que quedaba fuera acumuló
errores en silencio: el código Compose, las fuentes de plataforma y, sobre todo, **el build**. Ocho
de los doce fallos son de configuración de Gradle o de visibilidad entre módulos, cosas que ningún
test unitario puede ver.

**Y del análisis estático.** Detekt pasaba en verde sin analizar un solo archivo, porque su fuente
por defecto es `src/main/kotlin` y aquí el código vive en `src/commonMain/kotlin`. Al apuntarlo bien
salieron 105 hallazgos. Es la misma lección que los tests instrumentados que se decidió no tener:
una comprobación que no se ejecuta es peor que ninguna, porque ocupa el sitio de la que sí haría
falta.

**Y del primer CI de iOS.** Cuando el job de iOS todavía corría en `main`, su veredicto llegó una
tanda más tarde y confirmó la misma forma: el stack compartido —modelo, dominio, `scanner-api`,
`designsystem`, `platform`, `permissions` y el motor manual— compiló a la primera, porque es
justo lo que el arnés local ya ejercitaba. Los diez errores estaban todos en los dos motores que
hablan con AVFoundation, que es código que nadie había compilado nunca. Ocho eran **el mismo
malentendido repetido**, y vale la pena escribirlo porque se repetirá:

> En cinterop, un método declarado en la interfaz principal de una clase Objective-C se traduce a
> **miembro** y no se importa; solo lo que viene de una **categoría** se traduce a extensión con un
> nombre importable. `lockForConfiguration`, `unlockForConfiguration` y el método de clase
> `defaultDeviceWithMediaType:` son lo primero; `hasTorch`, `torchMode` y `videoZoomFactor`, lo
> segundo. Tener los tres segundos importados y funcionando fue justo lo que hizo que los tres
> primeros parecieran correctos.

Los otros dos eran nulabilidad: las constantes `AVMetadataObjectType*` y la propiedad `type` de un
metadato llegan como `String?` porque el binding no puede saber que Apple no las declara nulas.

**La segunda tanda: `Dispatchers.IO` no es la misma API en JVM que en native.** Arreglados los dos
motores, compilaron `core:domain`, `core:data`, `feature:history` y `feature:scanner`, y quedó un
único error, en una línea de `commonMain` que llevaba meses compilando en Android y Escritorio:

```
DatabaseBuilderFactory.kt:22 Cannot access 'val IO': it is internal in 'kotlinx.coroutines.Dispatchers'
```

El motivo está en cómo kotlinx-coroutines publica ese dispatcher, y es distinto en cada artefacto:

| Artefacto | Cómo se declara `IO` |
|---|---|
| `-jvm` | **miembro público** del objeto `Dispatchers` |
| nativo | **miembro `internal`**, más una extensión pública en `concurrentMain` |

En Kotlin un miembro le gana a una extensión. Desde el `commonMain` de un consumidor esa extensión
ni siquiera está en el ámbito —el `commonMain` de coroutines no declara `IO`—, así que en native la
única candidata es el miembro interno. En JVM la misma línea resuelve al miembro público y compila.
La salida es pedir el dispatcher desde cada plataforma, donde la extensión sí se ve.

Es el mismo patrón que los diez errores anteriores, un nivel más arriba: **una API que parece común
y no lo es**, y que por eso no falla hasta que compila el primer target que se comporta distinto.

**La tercera tanda, y el desenlace.** Mover `Dispatchers.IO` de `commonMain` a cada plataforma era
necesario y **no suficiente**: en `iosMain` seguía fallando igual. La extensión de `concurrentMain`
no viaja con el import del receptor, así que hace falta además `import kotlinx.coroutines.IO`. Es un
import que parece no usarse —el código dice `Dispatchers.IO`, no `IO`— y por eso queda explicado en
el KDoc del propio archivo: quitarlo "limpiando imports" devuelve el error.

Con eso, el job de iOS pasó de morir al minuto y medio a **enlazar el framework completo en 12 min
48 s**. No hubo cuarta tanda: `:composeApp` y todas sus dependencias de iOS —que nunca habían
llegado a compilarse— compilaron sin un solo error. Todo el código Kotlin de iOS compila hoy. Lo que
sigue sin poder comprobarse es que la cámara funcione, y para eso hace falta un iPhone, no un runner.

---

### 13.5 CI

Implementado en `.github/workflows/verify.yml`:

| Job | Dispara | Contenido |
|---|---|---|
| `checks` | cada PR | `detekt` + tests JVM de núcleo y features. Es el primero y el más barato: si falla, no se pagan los builds de plataforma |
| `android` | cada PR | `assembleDebug` + `lintDebug` + `assembleRelease`, publica el APK y el `mapping.txt` |
| `desktop` | cada PR | `desktopJar` |
| `web` | cada PR | `wasmJsBrowserDistribution` |

El job de iOS **no está en esta tabla**: vive en un workflow aparte, `ios.yml`, y solo se dispara a
mano (`workflow_dispatch`).

La razón no es el coste del runner macOS —que también, riesgo R4— sino qué significa un check.
Enlazar el framework comprueba que el Kotlin/Native **compila**; no comprueba que la app de iOS
haga nada, y no puede hacerlo mientras no haya un dispositivo Apple con el que probarla (Fase 3
despriorizada). Como criterio de aceptación para un cambio de Android, Desktop o Web, eso no aporta
señal: lo único que producía era un `main` en rojo permanente, y un rojo que siempre está encendido
deja de leerse. `Verify` cubre ahora exactamente las tres plataformas que este proyecto puede
ejecutar, y su verde vuelve a querer decir algo.

La contrapartida está aceptada a conciencia: el código de iOS queda sin verificación automática, que
es la situación en la que se coló el `Cannot access 'val IO': it is internal`. Se compensa lanzando
`ios.yml` a mano al tocar código de iOS, y obligatoriamente antes de retomar la Fase 3.

`assembleRelease` está en cada PR y no solo al publicar por una razón concreta: R8 solo rompe cosas
cuando se ejecuta, y los fallos que produce —una clase eliminada, un nombre ofuscado que alguien
esperaba leer— no aparecen en debug. Descubrirlos al preparar una release es tarde.

---

## 14. Plan de migración

| Fase | Contenido | Criterio de salida |
|---|---|---|
| **1. Fundaciones** (este entregable) | Build KMP/CMP, version catalog, estructura de módulos, modelo de dominio, SPI completo, registro, selección + fallback, UI de catálogo y escaneo, motor de entrada manual, tests de dominio | La app arranca en Android, Desktop y Web; el catálogo lista los 8 motores con su estado; los tests de selección y fallback pasan |
| **2. Android real** ✅ | `:engines:gms-code-scanner`, `:engines:mlkit-camerax`, preview CameraX + overlay, permisos, convention plugins, historial con Room, CI en GitHub Actions | Escaneo real en Android con dos motores intercambiables en caliente |
| **3. iOS** ⏸️ | Escrito: `:engines:vision-ios`, preview con `UIKitView`, shell Xcode, `:engines:zxing-cpp`, revisión de ADR-0005 · **despriorizada**: sin dispositivos Apple no se puede compilar ni verificar nada | Escaneo real en iOS; ZXing-cpp comparable entre Android e iOS |
| **4. Web y OCR** | ✅ `:engines:browser-detector`, `:engines:mlkit-ocr` (Android), preview de Web (D14) y escaneo desde imagen (RF-07) en las cuatro plataformas · pendiente: OCR en iOS con Vision | Las cuatro plataformas escanean; OCR disponible como alternativa |
| **5. Producto** | ✅ `ComparingScannerEngine`, `EngineScoreboard`, pantalla de comparación, acciones sobre el resultado (RF-13), exportación del historial a CSV/JSON y build de release con R8 · pendiente: Play Feature Delivery, accesibilidad y auditoría de privacidad | G5 medible en la app |

### 14.1 Qué se elimina en la Fase 1

| Se elimina | Motivo |
|---|---|
| `app/` (módulo Android único) | Reemplazado por `:androidApp` + `:composeApp` |
| Groovy DSL (`*.gradle`) | Reemplazado por Kotlin DSL + version catalog |
| `MainActivity.Greeting` y tema Purple/Pink | Scaffolding de plantilla sin valor |
| `dynamicColorScheme` como única fuente de tema | Sustituido por un design system propio, con dynamic color opcional en Android |
| `play-services-code-scanner` en el módulo raíz | Se reintroduce en la Fase 2 dentro de `:engines:gms-code-scanner` |

Riesgo de la eliminación: **nulo en términos funcionales** — no hay comportamiento implementado
que preservar (§2.1). El historial de git conserva el estado previo.

---

## 15. Riesgos

| # | Riesgo | Impacto | Mitigación |
|---|---|---|---|
| R1 | Divergencia de simbologías soportadas entre motores | Medio | `supportedFormats` declarativo + la UI advierte si el filtro pedido excede lo que el motor cubre |
| R2 | El GMS Code Scanner no permite overlay ni linterna | Bajo | `providesOwnUi = true`; la UI oculta sus propios controles para ese motor |
| R3 | `BarcodeDetector` no disponible en Safari/Firefox | Medio | El motor lo comprueba en `availability()` y reporta `Unsupported` con la razón; la cadena cae a entrada manual. El fallback a ZXing-cpp en Wasm que se planteaba aquí no es viable: no existe publicación wasmJs (ADR-0008) |
| R4 | Kotlin/Native + CMP para iOS: tiempos de build largos | Medio | Cachés de Gradle en CI, build de iOS solo en `main`, no en cada PR |
| R5 | ML Kit *unbundled* requiere descarga en primer uso | Bajo | Estado `RequiresDownload` modelado en el SPI y comunicado en la UI |
| R6 | Web target sin acceso a cámara en contexto no-HTTPS | Bajo | Documentado; el motor reporta `Unsupported` con la razón |
| R7 | Sobre-modularización ralentiza el build | Medio | Convention plugins en `build-logic` (Fase 2) y medición con `--scan` |
| ~~R11~~ | ~~Room 2.7.2 y AGP 8.9.2 no se pudieron contrastar con Kotlin 2.3.20 y KSP 2.3.10~~ | — | **Se materializó y está cerrado.** El primer CI falló exactamente ahí: KSP 2.3.10 exige AGP ≥ 8.10.0 y el proyecto estaba en 8.9.2. Se subió a **8.10.0**, el mínimo que el propio mensaje de KSP nombra. Room 2.7.2 pasó sin tocar nada. Salió tal como estaba previsto —"es lo primero que dirá el CI"— y costó una línea del catálogo |
| R8 | Deriva entre el catálogo documentado y el código | Bajo | `docs/ENGINES.md` es la fuente; un test verifica que el registro y la tabla coinciden en IDs |
| ~~R9~~ | ~~No existe un binding KMP publicado de zxing-cpp~~ | — | **Cerrado por ADR-0008.** El inventario era incompleto: `io.github.zxing-cpp:kotlin-native:3.1.1` publica los tres targets de iOS con el cinterop hecho, y `:android:3.1.1` cubre Android. Se consumen los artefactos, sin cinterop propio. Deriva en R10 y en la deuda D13 |
| ~~R10~~ | ~~Los klibs de `kotlin-native` están compilados con Kotlin 2.2.0 y el proyecto está en 2.1.21~~ | — | **Cerrado**: toolchain en Kotlin 2.3.20, CMP 1.11.1, KSP 2.3.10, Gradle 8.14.5 |

---

### 9.4 Comparación de motores (G5)

`ComparingScannerEngine` ejecuta varios motores en paralelo sobre la misma petición y
`EngineScoreboard` reduce el stream a métricas por motor. La pantalla "Comparar" los expone.

Un detalle contraintuitivo del diseño: **la petición de comparación no exige escaneo continuo ni
múltiples códigos**, aunque sería lo natural. Exigirlos filtraría por capacidades y dejaría fuera
justo al Google Code Scanner, que es *one-shot* y a la vez el motor más interesante de contrastar.
Basta con pedir la misma fuente y los mismos formatos: cada motor aporta lo que sabe, el que termina
antes deja de emitir, y el marcador refleja esa diferencia — que es precisamente el dato buscado.

Lo que **sí** se excluye es comparar entre fuentes distintas: la entrada manual no participa en una
comparación de cámara, porque no es un decodificador y contrastarla no mide nada.

---

### 9.5 Acciones sobre el resultado (RF-13)

Escanear un QR con una URL y no poder abrirla deja el resultado en un callejón sin salida. RF-13
cierra el ciclo con tres acciones: **copiar**, **compartir** y **abrir**.

El reparto de responsabilidades es lo que hace que esto no se repita cuatro veces:

| Quién | Qué decide |
|---|---|
| `:core:domain` — `ResultActionsFactory` | **Qué** acciones tiene sentido ofrecer y **con qué texto** |
| `:core:platform` — `PlatformActions` | **Cómo** las ejecuta cada sistema operativo |
| ViewModel | Une las dos mitades y avisa si la acción no prosperó |

Las acciones se derivan de `BarcodeValueType` (§6.3), **no** del formato: un QR con una URL ofrece
"Abrir enlace"; el mismo QR con texto plano, no. `Email`, `Phone`, `Sms` y `GeoPoint` se traducen a
sus esquemas (`mailto:`, `tel:`, `sms:`, `geo:`); `Product` no ofrece abrir porque un EAN no apunta
a ningún sitio: elegir un buscador sería una decisión de producto disfrazada de detalle técnico.

Copiar tampoco usa el valor crudo cuando hay algo mejor: `shareableText` de un QR de WiFi devuelve
`Red: X · Clave: Y`, porque pegarle a alguien `WIFI:T:WPA;S:…;;` no le sirve de nada.

`PlatformActions` es **una sola interfaz**, no tres segregadas como las capacidades de los motores
(§7.2). La diferencia es real: un motor puede implementar unas capacidades y otras no, y la UI
necesita distinguirlo en tiempo de compilación; la plataforma, en cambio, es una sola y siempre está
presente. Lo único desigual es compartir — **en escritorio no existe una hoja de compartir** — y eso
se resuelve con la bandera `canShare`, que llega al estado y hace que el botón simplemente no se
ofrezca. Los métodos devuelven `Boolean` en lugar de lanzar porque fallar al copiar no es
excepcional: el portapapeles puede estar bloqueado y el navegador puede negar el permiso.

Compromiso registrado en Web: `copyToClipboard` y `share` devuelven `true` en cuanto la llamada
arranca, sin esperar la promesa. Esperarla exigiría puentear promesas de JS a corrutinas para un
`Boolean` cuyo único uso es decidir si mostrar un aviso.

---

### 9.6 Textos de la interfaz

Los textos viven en `composeResources` **por módulo**: cada feature tiene su `strings.xml` y no hay
un fichero global que crezca sin dueño. Son cuatro catálogos —`composeApp`, `scanner`, `history` y
`settings`— y cada uno existe **dos veces**: `values/` en inglés y `values-es/` en español.

Cuál va sin calificador no es indiferente. `values/` es el respaldo de **todo** idioma que no tenga
catálogo propio, así que con los textos originales en castellano un teléfono en alemán veía español.
El motivo completo, y el mecanismo del selector de idioma, están en
[ADR-0011](adr/ADR-0011-idioma-de-la-app-por-encima-del-sistema.md) y en §9.9.

Hay además un catálogo que **no** es de Compose: `androidApp/src/main/res/values*/strings.xml`. No es
duplicación — lo lee el **sistema** para la etiqueta del lanzador y la pantalla de información de la
app, antes de que exista un solo composable, y las otras tres plataformas no tienen `res/`.

Lo que no es evidente es qué pasa con los textos que no nace en un `@Composable`. Un ViewModel que
emite `"Copiado"` ata la lógica al idioma y, peor, obliga a los tests a afirmar sobre una frase: una
coma de más rompe un test que no verificaba nada sobre la coma. Por eso los efectos llevan un
**mensaje semántico** (`ScannerMessage`, `HistoryMessage`) y la pantalla lo traduce. Queda una
puerta abierta —`Raw`— para el texto que produce la plataforma, como el motivo que devuelve el
selector de imágenes del sistema: sustituirlo por un mensaje genérico perdería la única pista útil.

Por el mismo motivo `ResultAction` dejó de traer `label`. El dominio decide **qué** acciones tienen
sentido y de qué clase es cada una (`OpenKind.Phone` y no `"Llamar"`); cómo se llaman en pantalla es
de la UI. Antes una decisión de dominio venía con el idioma pegado.

### 9.7 Exportación del historial

El historial sale a **CSV** o **JSON**, y el reparto es el de siempre: `:core:domain` decide qué
contiene el archivo y `:core:platform` (`FileSaver`) dónde acaba. Es el tercer servicio del sistema
del módulo — [PlatformActions] son acciones instantáneas, `ImagePicker` trae algo de fuera y esto
lleva algo hacia fuera.

Se exporta **lo que se está viendo**, no todo el historial: si el usuario filtró por un motor o buscó
algo, un archivo con el conjunto entero no se parecería a la pantalla que tiene delante.

#### El dominio no redacta frases

`shareableContent()` devuelve **la estructura** de lo que se va a copiar —una red WiFi con su clave,
los campos de una vCard, o el valor crudo— y la pantalla la redacta con sus recursos. Antes componía
aquí `"Red: X · Clave: Y"`, que era español dentro del dominio y no había forma de traducirlo sin
tocar esa clase.

La consecuencia visible es que la acción del ViewModel lleva el texto ya hecho: el dominio dice qué
datos importan, la pantalla los escribe y la plataforma los ejecuta.

#### Una celda de CSV puede ser código

Excel, Numbers y Sheets ejecutan como fórmula cualquier celda que empiece por `=`, `+`, `-` o `@`.
El contenido de un código escaneado **viene de fuera**, así que un QR con `=HYPERLINK(...)` dentro
se convertiría en código corriendo en la máquina de quien abra el archivo. El exportador antepone
una comilla simple a esos valores; el precio es que en esos casos el CSV no es byte a byte lo
escaneado, y por eso el JSON —donde no hay nada que ejecutar— lo conserva intacto.

El resto del CSV sigue RFC 4180: se entrecomilla cuando el valor lleva comas, comillas o saltos de
línea, y las comillas internas se duplican. No es teórico: una vCard leída de un QR trae las tres
cosas.

**La nota del usuario pasa por exactamente el mismo guardado**, y ahí el razonamiento se invierte de
forma interesante: al valor leído se le desconfía porque viene de un código que pudo poner cualquiera,
mientras que la nota la escribe el propio dueño del archivo. Da igual — una nota que empiece por `-`
o por `=` no necesita mala intención para que la hoja de cálculo la ejecute, y las comas y los saltos
de línea son mucho más probables en texto que escribe una persona que en un código de barras. La
columna `note` va **la última** para que quien tenga un script leyendo por posición no se rompa al
añadirla.

#### Un tercer formato, que no protege nada a propósito

CSV y JSON son para herramientas. **Texto plano** —una lectura por línea, sin cabecera y sin
comillas— es para personas: lo que la gente hace de verdad con treinta códigos escaneados es pegarlos
en un correo, en un chat o en una celda, y ahí los otros dos estorban.

Ese formato **no lleva el guardado anti-fórmula**, y la decisión es del mismo tipo que la de
ponérselo al CSV: se mira quién va a abrir el archivo. Una hoja de cálculo ejecuta celdas; un cuerpo
de correo no ejecuta nada. Anteponer una comilla a un valor que empieza por `-` rompería justo lo que
este formato existe para dar —el valor tal cual, listo para pegar— sin proteger de nada. Quien lleve
los datos a una hoja tiene el CSV, que sí lo protege. Hay un test que fija las dos mitades de esta
regla, para que nadie "arregle" una de ellas por simetría.

Lo único que se toca es aplanar los saltos de línea de una nota, porque una nota multilínea rompería
el "una lectura por línea" que es toda la propuesta del formato.

Los nombres de columna y las claves JSON están en inglés y en `snake_case` aunque la app esté en
español. No es interfaz: es un archivo que abre una hoja de cálculo o consume un script, y traducir
la app no debería romper lo que alguien tenga montado encima.

### 9.8 Escaneo desde imagen (RF-07)

Escanear una foto no es una variante menor de escanear con la cámara: es la salida cuando el código
está en una captura de pantalla, en un PDF, en un correo — o cuando el usuario ha negado el permiso
de cámara.

El reparto es el mismo que en RF-13: `:core:platform` expone `ImagePicker` —**cómo** se elige el
archivo— y `:core:domain` decide **qué** hacer con él. `PickImageResult` distingue tres salidas, y
la distinción no es cosmética: *cancelar* es la salida más frecuente de un selector de archivos, y
tratarla como error haría que la app mostrara un fallo cada vez que el usuario cambia de idea.

**Ningún selector pide permisos.** El *photo picker* de Android, `UIImagePickerController` en iOS,
el diálogo de archivos de escritorio y el `<input type=file>` del navegador corren todos **fuera del
proceso de la app** y devuelven únicamente lo que el usuario elige. Pedir `READ_MEDIA_IMAGES` daría
acceso a la galería entera para leer una sola foto.

`DecodeImageUseCase` recorre la cadena de motores igual que `FallbackScannerEngine` hace en vivo, y
aquí importa más: el caso de uso natural del OCR es una etiqueta dañada que el decodificador no ve y
cuyo número impreso sí es legible. Sin cadena, ese motor no llegaría a ejecutarse nunca.

Devuelve `Detection` y no `Barcode` porque **qué motor lo leyó es el dato que este producto existe
para dar**, y aplica el mismo filtrado por formato y la misma interpretación semántica que la sesión
en vivo: un QR con una URL debe ofrecer "Abrir enlace" venga de donde venga.

#### Una excepción con nombre

`EngineStatus.canDecodeImages` deja pasar a los motores bloqueados **solo** por el permiso de
cámara. El archivo ya está en el dispositivo y no hay cámara que abrir, así que negar el permiso no
debería cerrar también esta vía — que es justo la alternativa que le queda al usuario. La excepción
es estrecha a propósito: un modelo sin descargar o un motor de una fase futura sí bloquean, porque
ahí no hay nada que ejecutar.

La regla vive en el dominio y no en la pantalla para que el selector de motores y la UI apliquen
exactamente la misma: si divergieran, el botón aparecería y no encontraría decodificador.

---

### 9.9 Marca, tema e idiomas

Todo lo de esta sección vive en `:core:designsystem` y no en una feature, por el mismo criterio de
siempre: son decisiones del producto entero y no de una pantalla.

#### La paleta se declara entera

`WhyScanTheme` fija los **~30 roles** de Material 3 y no los seis habituales. El motivo está contado
en §12.1: `lightColorScheme()` rellena con su paleta de fábrica todo lo que no se le pase, y el mismo
defecto apareció dos veces —primero en los `on*`, después en los `*Container`— antes de que quedara
claro que la única postura estable es no dejar ninguno al azar. `surfaceTint` incluido: es el color
con el que Material tiñe una superficie elevada, y dejarlo de fábrica teñía de morado cualquier
superficie con elevación.

Se mantiene la decisión de **no usar `dynamicColorScheme`** (Material You). Un tema que cambia con el
fondo de pantalla del usuario es incompatible con una UI que se superpone a un preview de cámara,
donde el contraste tiene que estar garantizado (RNF-05) — y ahora, además, el azul de WhyScan es parte
del producto.

#### Tipografía y formas

No había ninguna de las dos: `MaterialTheme` usaba las de fábrica. Dos consecuencias concretas que se
veían en pantalla: los títulos venían en `Normal` donde esta app quiere `SemiBold`, y el `bodyMedium`
con el que se pintaba el valor de un código leído traía `letterSpacing` positivo — lo peor posible
para una tirada de dígitos que alguien va a cotejar a ojo con una etiqueta.

La familia es la del sistema, a conciencia: Roboto en Android, San Francisco en iOS y la del
navegador en Web ya están optimizadas para cada plataforma, pesan cero en el binario y respetan los
ajustes de accesibilidad. Empaquetar una fuente de marca cuesta unos 300 KB por peso y es una
decisión que conviene tomar con la ficha de Play delante.

Los radios (`Radius`) están algo más redondeados que los de fábrica y se exponen **también sueltos**,
porque el visor y el overlay no son componentes de Material y aun así tienen que usar los mismos
valores. Antes el visor usaba `Spacing.md` como radio: un `dp` suelto con disfraz, que daba el número
correcto por casualidad y se habría redondeado de rebote al cambiar el margen de la app.

#### Tema claro/oscuro elegido por el usuario

`ThemeMode` (Sistema / Claro / Oscuro) se persiste con las demás preferencias de app.
`WhyScanTheme` recibe un **booleano ya resuelto** y no el modo: resolver "sistema" contra lo que el
sistema dice *ahora* es cosa de quien tiene el estado delante, y así `:core:designsystem` no depende
del dominio.

En Android hay una segunda mitad que **no pinta Compose**: los iconos de las barras del sistema. Con
`enableEdgeToEdge()` a secas siguen al modo oscuro *del sistema*, y eso solo funciona mientras los
dos coinciden. En cuanto el usuario elige un tema distinto dejan de hacerlo: con el teléfono en claro
y la app forzada a oscuro, los iconos de la barra de estado salían oscuros sobre fondo oscuro. `App`
avisa del valor resuelto por un callback y `MainActivity` reajusta el estilo — la lógica de
plataforma se queda en el shell de plataforma.

Queda un caso que esto no puede cubrir: el **fondo de arranque**, que el sistema elige por su propia
configuración antes de que nadie pueda leer las preferencias. `values-night/` cubre el caso normal
(sistema y app coinciden); resolverlo del todo exige `androidx.core:core-splashscreen`, que es una
dependencia nueva y una decisión aparte.

#### Idiomas

Inglés y español, con inglés como catálogo sin calificador y por tanto respaldo universal. El
selector propio se aplica cambiando el locale de la plataforma y descartando los `remember` del
subárbol con `key(tag)`; en Web no se ofrece porque `navigator.language` no se puede escribir desde
la página. El razonamiento completo, el callejón sin salida de `LocalComposeEnvironment` y la
incógnita pendiente en iOS están en
[ADR-0011](adr/ADR-0011-idioma-de-la-app-por-encima-del-sistema.md).

#### La marca

`WhyScanMark` es un `ImageVector` dibujado en código —cuatro esquinas de encuadre y la línea de
lectura— y no una imagen empaquetada, por dos motivos: se tiñe con el color del tema, así que
funciona en claro y en oscuro sin dos archivos; y es **la misma forma** que el icono de lanzador de
Android, con lo que la app y su icono no se pueden separar por descuido.

El icono adaptativo lleva capa `monochrome`, que es lo que da soporte a los iconos temáticos de
Android 13+, y hay PNG de respaldo para API 24 y 25, que no entienden iconos adaptativos. El
contenido ocupa 48 dp centrados en el lienzo de 108: sus esquinas quedan a 33,9 dp del centro, por
debajo de los 36 del radio seguro, así que no se recorta ni con máscara redonda.

**Antes de esto no había icono en absoluto** — el manifiesto no declaraba `android:icon` y Android
ponía su robot por defecto. Es un bloqueo duro de Play, y de los que ningún CI detecta.

### 9.10 Ciclo de vida de la sesión de escaneo

La disposición de la pantalla está en
[ADR-0010](adr/ADR-0010-dos-disposiciones-de-la-pantalla-de-escaneo.md). Lo que sí es de este
documento es **cuándo se enciende y se apaga la cámara**, porque toca al ViewModel y no a la
presentación.

La pantalla emite dos acciones que antes no existían:

```kotlin
DisposableEffect(viewModel) {
    viewModel.onAction(ScannerAction.ScreenShown)
    onDispose { viewModel.onAction(ScannerAction.ScreenHidden) }
}
```

**Al aparecer, la sesión arranca sola.** Que un escáner exija pulsar "Escanear" para escanear es
fricción que no gana nada: quien abre la app ya dijo lo que quiere abriéndola. Con dos condiciones,
las dos con motivo:

- **Si falta el permiso de cámara, no arranca.** La pantalla enseña la explicación y el botón.
  Pedir el permiso sin que el usuario haya tocado nada es la forma más rápida de que lo deniegue para
  siempre, y en Android una denegación permanente no se puede volver a pedir desde la app.
- **Si no hay ningún motor de cámara en vivo, tampoco.** Es el estado permanente del escritorio: hay
  decodificador de archivos y entrada manual, pero ninguna captura de webcam. Antes eso era un visor
  negro esperando algo que no podía pasar; ahora se dice y se ofrece la salida que sí existe.

**El arranque automático se resuelve en el observador del catálogo, no en la acción.** No es un
detalle de implementación: `refresh()` publica en un `Flow` que se colecta en otra corrutina, así que
cuando `refresh()` devuelve, el estado todavía puede tener la disponibilidad vieja. Decidir ahí era
una carrera — en un arranque en frío el catálogo aún está vacío, `hasLiveCameraEngine` da `false` y
la cámara no se abre nunca. Se intenta en los dos sitios: al recibir la acción, por si el catálogo ya
estaba cargado —volver a la pantalla no produce ninguna emisión nueva, porque el `StateFlow` no
reemite un valor igual—, y en cada emisión posterior, por si llega después.

**Al desaparecer, se apaga.** El ViewModel sobrevive a la navegación, así que sin esto la cámara
seguía capturando mientras el usuario miraba el historial o los ajustes. En una app de escaneo eso no
es solo batería. Parar es además renunciar a un arranque pendiente: si no, volver de los ajustes
reabriría la cámara que el usuario acaba de cerrar a mano con el botón de pausa.

**Los resultados en pantalla tienen tope** (cien). Una sesión continua larga los acumulaba sin
límite. Lo que se recorta no se pierde —el historial guarda todo, y ese es su trabajo— y deja de
ocupar memoria en una pantalla donde nadie se desplaza cien lecturas hacia abajo.

#### Pausado es un estado, no una espera

`ViewfinderArea` resuelve con un `when` las cosas excluyentes que pueden ocupar ese espacio, y hasta
esta versión eran cuatro: cargando, permiso, sin cámara, o cámara. **Faltaba una quinta y el `when`
la absorbía por la rama final.**

Al pausar, el ViewModel pone `activeEngineId = null` —correcto, no hay motor corriendo— y con él
desaparece la superficie de preview. Sin caso propio, eso caía en el `else` y dejaba un
`CircularProgressIndicator` **girando indefinidamente**: la señal universal de "esto está a punto de
terminar" sobre algo que no iba a terminar nunca, porque estaba esperando al usuario. Y como
`SessionBadge` sí sabía distinguirlo, la pantalla llegaba a contradecirse a sí misma: la píldora
decía "Pausado" encima de un spinner.

Ahora el spinner solo aparece mientras `SessionStatus.Starting`, que es cuando de verdad hay algo en
marcha, y pausado tiene la misma forma que los otros dos estados que sustituyen al visor —icono,
qué pasa, qué hacer—. La lección general es la de siempre con los `when`: **la rama `else` no es un
caso, es el sitio donde se esconden los que no se enumeraron.**

---

## 16. Anexo — Decisiones registradas

| ADR | Decisión |
|---|---|
| [ADR-0001](adr/ADR-0001-compose-multiplatform.md) | Adoptar Compose Multiplatform en lugar de KMP + UI nativa |
| [ADR-0002](adr/ADR-0002-scanner-engine-spi.md) | Modelar los motores como un SPI con capacidades declarativas |
| [ADR-0003](adr/ADR-0003-koin-como-di.md) | Koin como contenedor de DI en lugar de Hilt |
| [ADR-0004](adr/ADR-0004-flow-como-api-de-sesion.md) | `Flow<ScanEvent>` como API de sesión de escaneo |
| [ADR-0005](adr/ADR-0005-navegacion-propia.md) | Navegación propia mínima en la Fase 1 — revisada en la Fase 3: se mantiene, y se añade restauración de estado |
| [ADR-0006](adr/ADR-0006-reestructuracion-del-build.md) | Reestructurar el build de una vez en lugar de migrar incrementalmente |
| [ADR-0007](adr/ADR-0007-preview-como-capacidad-del-motor.md) | El preview de cámara es una capacidad del motor, no de la feature |
| [ADR-0008](adr/ADR-0008-baseline-zxing-cpp.md) | El baseline de comparación es zxing-cpp desde artefactos publicados, en Android e iOS |
| [ADR-0009](adr/ADR-0009-play-feature-delivery-aplazado.md) | Play Feature Delivery se aplaza: incompatible con KMP, exige Play Store y no hay medición |
| [ADR-0010](adr/ADR-0010-dos-disposiciones-de-la-pantalla-de-escaneo.md) | La pantalla de escaneo tiene dos disposiciones —producto y banco de pruebas— y no una con condicionales |
| [ADR-0011](adr/ADR-0011-idioma-de-la-app-por-encima-del-sistema.md) | El idioma de la app se fija cambiando el locale de la plataforma: `LocalComposeEnvironment` es `internal` |
| [ADR-0012](adr/ADR-0012-la-nota-es-del-historial-no-de-la-deteccion.md) | La nota del usuario es un tercer nivel del modelo (`HistoryEntry`) y no un campo de `Detection` |
