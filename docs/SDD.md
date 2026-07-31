# Software Design Document — TestScanner Multiplatform

| Campo | Valor |
|---|---|
| Proyecto | TestScanner |
| Documento | Software Design Document (SDD) |
| Versión | 1.3 |
| Estado | Vigente — Fases 1, 2, 4 y 5 cerradas en código salvo lo listado como pendiente; la 3 (iOS) escrita pero despriorizada por falta de dispositivos. Todo pendiente de la primera compilación con Gradle |
| Fecha | 2026-07-31 |
| Autor | Equipo TestScanner |
| Alcance de esta versión | Migración de app Android monolítica a Compose Multiplatform + arquitectura de motores de escaneo intercambiables |

---

## 1. Introducción

### 1.1 Propósito

Este documento define la arquitectura objetivo de **TestScanner** tras su migración desde una
aplicación Android de módulo único hacia una aplicación **Compose Multiplatform (CMP)**.

El objetivo funcional principal del producto es:

> Ofrecer **múltiples alternativas de escaneo intercambiables** para **todo tipo de códigos de
> barras y QR**, permitiendo comparar motores, degradar con elegancia cuando uno no está
> disponible, y funcionar sobre Android, iOS, Desktop y Web.

TestScanner no es solo un lector de códigos: es un **banco de pruebas de motores de escaneo**.
Esa naturaleza es lo que dicta la decisión arquitectónica central del documento — el
**Scanner Engine SPI** (§7).

### 1.2 Alcance

| Dentro de alcance | Fuera de alcance (por ahora) |
|---|---|
| Arquitectura multiplataforma y estructura de módulos Gradle | Backend propio / sincronización en la nube |
| SPI de motores de escaneo, registro, selección y fallback | Generación de códigos (encoding) |
| Modelo de dominio de códigos y resultados | Cuentas de usuario / autenticación |
| Capa de presentación MVI en Compose Multiplatform | Analítica de producto y A/B testing |
| Gestión de permisos de cámara multiplataforma | Publicación en stores |
| Estrategia de testing, calidad y CI | Facturación / monetización |

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
TestScanner/
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
TestScanner/
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
│   ├── designsystem/                  # CMP: tema, tokens, componentes reutilizables
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
│   └── history/                       # historial, filtrable por motor
│
├── composeApp/                        # raíz CMP: App(), navegación, wiring de DI
│                                      # targets: android, iosArm64/SimulatorArm64, jvm, wasmJs
├── androidApp/                        # shell Android: Application + MainActivity
├── iosApp/                            # shell iOS: proyecto Xcode + SwiftUI host
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
```

`Detection` envuelve a `Barcode` en lugar de añadirle campos: el `Barcode` es lo que existe en el
mundo; la `Detection` es el evento de haberlo visto con un motor concreto en un instante. Esa
separación es la que habilita G5 (comparabilidad entre motores).

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
medición que hace útil a TestScanner.

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
dependencia externa. El grafo tiene tres destinos — escanear, comparar e historial; Android le cede
el botón atrás del sistema y todas las plataformas usan la barra inferior. Razón: la navegación
multiplataforma de Jetpack está aún en versiones alpha/beta y no queremos que su ciclo de releases
bloquee el nuestro en la fase de fundaciones.

La revisión prevista se hizo y **la decisión se mantiene**: tres destinos y ningún deep link no
alcanzan el umbral de migración, que sigue siendo seis destinos o el primer deep link. Ver
`docs/adr/ADR-0005`.

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

---

## 10. Inyección de dependencias

**Koin 4.x.** Hilt no es multiplataforma (depende de procesamiento de anotaciones sobre el modelo
de componentes de Android), por lo que no es una opción aquí. Ver `docs/adr/ADR-0003`.

```
appModule              (composeApp)   → wiring raíz, arranca Koin
├── platformModule     (expect/actual) → motores de la plataforma, permisos, dispatchers
├── dataModule         (core:data)    → Registry, repositorios
├── domainModule       (core:domain)  → UseCases
└── scannerModule      (feature:scanner) → ViewModels
```

Convenciones: constructor injection siempre; ningún `Context` en ViewModels; los dispatchers se
inyectan (`DispatcherProvider`) para que los tests puedan sustituirlos por `UnconfinedTestDispatcher`.

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
todos los pares y falla por debajo de 4.5:1. Corre en `commonTest`, sin renderizar y sin
dispositivo. Se miden además los pares que **la UI usa de hecho** —`primary`, `tertiary` y `error`
como color de texto sobre la tarjeta—, que ninguna convención de Material cubre.

Al extraer la paleta apareció un defecto que el contraste no habría detectado: solo se declaraban
`primary`, `secondary` y `tertiary`, así que todos los roles `on*` se quedaban en los valores por
defecto de Material —de una paleta morada que no es esta—. El texto de un botón primario en modo
oscuro salía morado. Ahora se declaran los catorce.

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

Objetivo de cobertura: **≥ 80 % en `:core:domain` y `:core:data`**; la UI no se persigue por
cobertura sino por casos de estado representativos.

**No hay tests instrumentados, y es una decisión, no una omisión.** No va a haber emulador en CI, así
que un test que exija dispositivo es un test que nunca se ejecuta: no aporta seguridad y sí una falsa
sensación de tenerla. El hueco que esto deja —que ningún test comprueba que un motor de cámara lea un
código de verdad— está escrito con todas las letras en el ROADMAP, junto a lo que sí queda cubierto
sin dispositivo.

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
- **Reglas de arquitectura** verificadas en CI: `:core:domain` no puede depender de Compose ni de
  Android; `:engines:*` no puede depender de `:feature:*`.
- **SonarCloud** para deuda técnica y duplicación; sin regresión permitida en PR.
- Compilación con `allWarningsAsErrors` en módulos `:core:*`.

### 13.4 CI

Implementado en `.github/workflows/verify.yml`:

| Job | Dispara | Contenido |
|---|---|---|
| `checks` | cada PR | `detekt` + tests JVM de núcleo y features. Es el primero y el más barato: si falla, no se pagan los builds de plataforma |
| `android` | cada PR | `assembleDebug` + `lintDebug` + `assembleRelease`, publica el APK y el `mapping.txt` |
| `desktop` | cada PR | `desktopJar` |
| `web` | cada PR | `wasmJsBrowserDistribution` |
| `ios` | solo `main` | `linkDebugFrameworkIosSimulatorArm64` en runner macOS |

`ios` no corre en los PR a propósito: un runner de macOS cuesta unas diez veces más que uno de Linux
y el enlazado de Kotlin/Native es lento (riesgo R4). Los PR ya cubren los otros tres targets, y el
código de iOS es mayoritariamente `commonMain` compilado en ellos.

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
un fichero global que crezca sin dueño.

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

Se exporta **lo que se está viendo**, no todo el historial: si el usuario filtró por un motor, un
archivo con el conjunto entero no se parecería a la pantalla que tiene delante.

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
