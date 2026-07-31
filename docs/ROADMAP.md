# Roadmap de migración

Cada fase es entregable e independientemente verificable. Una fase no se cierra hasta que su
criterio de salida se cumple en CI.

---

## Fase 1 — Fundaciones ✅ (entregable actual)

Convertir el repositorio en un proyecto Compose Multiplatform con la arquitectura de motores
completa, aunque todavía sin motores de cámara reales.

- [x] Build KMP/CMP: Kotlin DSL, version catalog, Gradle, Kotlin, AGP (versiones al día en `libs.versions.toml`)
- [x] Targets `android`, `iosX64/iosArm64/iosSimulatorArm64`, `jvm`, `wasmJs`
- [x] Estructura de módulos `core/`, `engines/`, `feature/`, `composeApp/`, `androidApp/`
- [x] Modelo de dominio: `Barcode`, `BarcodeFormat` (17 simbologías), `BarcodeValueType`, `Detection`
- [x] Scanner Engine SPI completo: contrato, capacidades, disponibilidad, eventos
- [x] Catálogo de los 7 motores con capacidades declaradas y estado por fase
- [x] Registro, política de selección automática y cadena de fallback
- [x] Motor de entrada manual (100 % `commonMain`) — la app escanea desde el día uno
- [x] Parser semántico de valores (URL, WiFi, vCard, email, teléfono, geo, producto)
- [x] MVI de la feature de escaneo + UI de catálogo y resultados
- [x] Design system propio (tokens, tema claro/oscuro)
- [x] Tests de dominio: selección, fallback, parser, catálogo, comparador y marcador
- [x] Suite de contrato de motores (`BarcodeScannerEngineContractTest`), aplicada también a los
      decoradores y a la cadena completa que llega al ViewModel
- [x] Comparador de motores en paralelo + métricas por motor (objetivo G5)
- [x] `build-logic/` con convention plugins
- [x] SDD, 7 ADRs y catálogo de motores documentados

**Criterio de salida:** la app arranca en Android, Desktop y Web; el catálogo lista los 7 motores
con su estado real; los tests de `:core:domain` y `:core:data` pasan en CI.

---

## Fase 2 — Android real ✅

- [x] `:engines:gms-code-scanner` — Google Code Scanner, sin permisos
- [x] `:engines:mlkit-camerax` — ML Kit Barcode + CameraX, con linterna, zoom y decodificación de imagen
- [x] `PermissionController` actual de Android + flujo de denegación permanente
- [x] Arranque de Koin por plataforma (`initKoin`) con `Context` en Android
- [x] Preview de Android como capacidad del motor (`CameraPreviewEngine`, ADR-0007)
- [x] Overlay común de detección sobre `cornerPoints` normalizados (`ScanOverlay`)
- [x] Controles de linterna en la UI, derivados de las capacidades declaradas
- [x] Historial persistente con Room KMP (`:core:database` + `:feature:history`)
- [x] Navegación entre escáner e historial, con botón atrás de Android
- [x] CI en GitHub Actions: detekt + tests + Android + Desktop + Web en cada PR, iOS en `main`
- [x] Higiene del repo: `.editorconfig` alineado con detekt, `.idea/` fuera del control de versiones
- [x] Preferencias persistentes con `multiplatform-settings` (D2) y control de zoom en la UI (D8)
- [x] `ScanRequest.timeoutMillis` implementado (`DeadlineScannerEngine`): estaba en el modelo desde la Fase 1 sin que ningún código lo cumpliera
- [x] Decisión sobre los tests instrumentados: **no los va a haber**. Sin emulador en CI, un test
      que exija dispositivo es un test que nunca se ejecuta y que da una falsa sensación de red

**Criterio de salida:** escaneo real en Android alternando dos motores en caliente, con fallback
verificable desactivando Play Services.

> Pendiente de la primera compilación con Gradle: el entorno donde se escribió esta fase no tenía
> acceso a `dl.google.com`, así que las APIs de ML Kit y CameraX están sin compilar. El núcleo puro
> sí está verificado (328 tests en verde con kotlinc).

---

## Fase 3 — iOS ⏸️ despriorizada

> **Sin dispositivos Apple no se puede verificar nada de esto.** El código está escrito y sigue en
> el repositorio, pero deja de marcar el ritmo: compilar Kotlin/Native exige macOS y probarlo exige
> un iPhone o un simulador, así que cualquier fallo aquí solo aparecería al llegar a esa máquina.
> Lo que se hace mientras tanto es lo que Android, Escritorio y Web sí pueden confirmar.

- [x] `:engines:vision-ios` — `AVCaptureSession` + `AVCaptureMetadataOutput`, con linterna y zoom
- [x] Preview de iOS (`UIKitView` con `AVCaptureVideoPreviewLayer`) vía `CameraPreviewEngine`
- [x] `IosPermissionController` sobre `AVCaptureDevice`
- [x] `iosApp/` — fuentes Swift e `Info.plist` con `NSCameraUsageDescription`
- [ ] `iosApp.xcodeproj` — se crea en Xcode siguiendo `iosApp/README.md` (requiere macOS)
- [ ] Primera compilación de todo el código iOS: nada de esto se ha compilado aún
- [x] **Motor baseline decidido** (cerraba R9): se consumen los artefactos publicados de zxing-cpp,
      sin cinterop propio — ver [ADR-0008](adr/ADR-0008-baseline-zxing-cpp.md)
- [x] **Kotlin 2.3.20** (cerraba R10): los klibs de zxing-cpp están compilados con 2.2.0 y el
      proyecto estaba en 2.1.21. Se subió a 2.3.20 exacto porque es con la que están compilados CMP
      1.11.1, Koin 4.2.2 y KSP 2.3.10 — emparejar exacto reduce la superficie de fallo. Gradle a
      8.14.5. Room y AGP se quedan: viven en el maven de Google, inalcanzable desde aquí
- [x] `:engines:zxing-cpp` — `io.github.zxing-cpp:android` en Android y `:kotlin-native` en iOS.
      Dos adaptadores y ningún `commonMain`: las dos publicaciones no comparten API, solo el núcleo
      C++ — que es lo que hace justa la comparación. En iOS usa `AVCaptureVideoDataOutput` y no la
      salida de metadatos, porque esa ya trae su propio decodificador dentro y el baseline dejaría
      de serlo. Decodifica también imágenes estáticas, lo que da a iOS su primer decodificador de
      archivos
- [ ] Revisión de ADR-0005: el grafo ya tiene 2 destinos; migrar a `navigation-compose` si llega a 6 o aparecen deep links

- [ ] CI: `linkDebugFrameworkIosSimulatorArm64` en runner macOS

**Criterio de salida:** escaneo real en iOS; ZXing-cpp produce resultados comparables entre
Android e iOS sobre el mismo set de imágenes de referencia.

---

## Fase 4 — Web y OCR

- [x] `:engines:browser-detector` — `BarcodeDetector` con detección de soporte del navegador y de
      contexto seguro, más decodificación de imagen estática vía `createImageBitmap`
- [x] `:engines:mlkit-ocr` — Text Recognition en Android + `OcrCodeInterpreter`: lee el número
      impreso bajo el código y solo lo emite si el dígito de control cuadra
- [x] Preview de Web (D14): el `<video>` vive en el documento, sobre el canvas, y el composable solo
      le dice qué rectángulo ocupar. Tapa el overlay, y eso pasa a ser una capacidad declarada
      (`occludesOverlay`) en vez de un dibujo invisible
- [ ] OCR en iOS: ML Kit se distribuye por CocoaPods, que este proyecto no usa. La alternativa sin
      dependencias es `VNRecognizeTextRequest` del framework Vision, reutilizando `OcrCodeInterpreter`
- [x] Escaneo desde imagen/galería (RF-07) con selector en las cuatro plataformas: *photo picker*
      en Android, `UIImagePickerController` en iOS, `JFileChooser` en escritorio e `<input
      type=file>` en Web. Ninguno pide permisos: los cuatro corren fuera de la app y devuelven solo
      lo elegido
- [x] Suite de contrato contra lo que se puede ejercitar sin dispositivo: los decoradores y la
      cadena completa. Los motores de cámara quedan cubiertos solo por lo declarativo (ver D6)

> El fallback web a ZXing-cpp compilado a Wasm que figuraba aquí se ha retirado: no existe
> publicación wasmJs (ADR-0008). El respaldo del navegador es la entrada manual.

**Criterio de salida:** las cuatro plataformas escanean con al menos dos motores cada una; el OCR
recupera correctamente EAN-13 impresos sobre códigos dañados.

---

## Fase 5 — Producto

- [x] Comparador: ejecutar varios motores sobre la misma petición (`ComparingScannerEngine`)
- [x] Métricas de latencia y acierto por motor (`EngineScoreboard`)
- [x] Pantalla "Comparar" con el marcador en vivo — **cierra G5**
- [x] `ScanRequest.continuous` y `allowMultiple` se respetan de verdad (`RequestLimitsScannerEngine`):
      antes solo los cumplía el motor manual, así que el interruptor de escaneo continuo no tenía
      efecto sobre la cámara y una sesión puntual no terminaba nunca
- [x] Los `ScannerEffect` llegan a la UI vía un `SnackbarHost` único en la raíz: se emitían a un
      flujo que nadie colectaba
- [x] Filtro de formatos en pantalla (RF-06), y `DismissError` / `Refresh` cableados
- [x] Atribución de eventos al motor: `FrameAnalyzed` y `Failed` llevan `engineId`, así que las
      métricas de frames y de fallos por motor dejan de estar siempre en cero
- [x] Acciones sobre el resultado (RF-13): copiar, compartir y abrir, derivadas del **significado**
      del código y no de su formato (`ResultActionsFactory`), ejecutadas por `PlatformActions` en las
      cuatro plataformas. Sin esto, escanear un QR con una URL no llevaba a ningún lado
- [x] Exportación del historial a CSV y JSON (`HistoryExporter` + `FileSaver`). Exporta lo que se
      está viendo, no todo: un archivo que no se parece a la pantalla es una sorpresa. El CSV
      neutraliza los valores que una hoja de cálculo ejecutaría como fórmula — el contenido de un
      código escaneado viene de fuera y no es de fiar
- [ ] Play Feature Delivery para los motores pesados de Android (RNF-06)
- [x] Historial de Web persistente (D9) y textos de compartir fuera del dominio (D15)
- [ ] Accesibilidad completa (RNF-05) y auditoría de privacidad (RNF-03)

**Criterio de salida:** el usuario puede responder, dentro de la app y con datos, la pregunta
"¿qué motor funciona mejor para este código en este dispositivo?".

---

## Qué cubre a los motores de cámara sin emulador

La decisión de no tener tests instrumentados deja un hueco real y conviene decir exactamente cuál es
y qué lo compensa:

| Se comprueba sin dispositivo | Sigue sin comprobarse |
|---|---|
| Que el descriptor de los siete motores es coherente: IDs únicos, fases válidas, sin prometer control de cámara con UI propia (`ScannerEngineCatalogTest`) | Que el motor **lea** un código real |
| Que la selección, el fallback, los límites de petición y el plazo se comportan según el contrato, incluida la cadena completa que llega al ViewModel | Que la cámara arranque, y que se libere al cancelar |
| Que lo declarado tenga quien lo cumpla, en todo lo instanciable sin `Context` | Lo mismo en los motores de Android e iOS, que necesitan `Context` o `AVCaptureSession` |
| Que el proyecto **compile** para Android, Escritorio y Web, incluida la build de release con R8 | — |

El riesgo que queda es el de siempre en este tipo de app: el código de cámara solo se prueba
usándola. Lo que sí evita el diseño es que un fallo ahí se lleve por delante al resto — el SPI
mantiene la lógica de selección, degradación y presentación fuera de los motores, y esa parte sí
está cubierta.

---

## Deuda técnica aceptada en la Fase 1

Registrada de forma explícita para que no se olvide:

| # | Deuda | Se salda en |
|---|---|---|
| ~~D1~~ | ~~Sin convention plugins: cada módulo repite su configuración KMP~~ | **Saldada**: `build-logic/` con `testscanner.kmp.library`, `.kmp.compose` y `.android.application` |
| ~~D2~~ | ~~Preferencias en memoria, no persistidas~~ | **Saldada**: `multiplatform-settings` en las cuatro plataformas |
| ~~D3~~ | ~~Historial en memoria~~ | **Saldada**: Room KMP en Android, iOS y Desktop. En Web sigue en memoria porque Room no tiene target wasmJs |
| D4 | Navegación propia sin deep links ni restauración de estado | Fase 3 (revisión ADR-0005) |
| ~~D5~~ | ~~Strings hardcodeados en la UI~~ | **Saldada**: `composeResources` por módulo. Los ViewModels emiten mensajes semánticos (`ScannerMessage`, `HistoryMessage`) y `ResultAction` dejó de traer etiqueta: el dominio dice qué acción, la UI cómo se llama |
| ~~D6~~ | ~~La suite de contrato no se ejecuta contra motores de cámara reales~~ | **Cerrada como no-objetivo**: no habrá emulador en CI, así que ningún test puede exigir dispositivo. Lo cubre lo que sí corre sin él — ver más abajo |
| ~~D8~~ | ~~El zoom se declara como capacidad pero no hay control en la UI~~ | **Saldada**: slider derivado de `canControlZoom` |
| ~~D10~~ | ~~RF-07 sin UI ni selector de archivos~~ | **Saldada**: `ImagePicker` en las cuatro plataformas y `DecodeImageUseCase` recorriendo la cadena de motores. Un motor bloqueado por el permiso de cámara sigue sirviendo para leer un archivo |
| ~~D12~~ | ~~RF-13 (copiar, compartir, abrir enlace) sin implementar~~ | **Saldada**: `PlatformActions` en `:core:platform` con implementación en las cuatro plataformas. En escritorio no hay hoja de compartir y el botón no se ofrece (`canShare`) |
| ~~D11~~ | ~~La comparación necesita dos motores de cámara y solo Android los tenía~~ | **Saldada**: con ZXing-cpp, iOS tiene dos (Vision y ZXing-cpp) y Android cuatro |
| ~~D9~~ | ~~El historial de Web es de sesión~~ | **Saldada**, aunque no con IndexedDB: se guarda como JSON en el almacén de la plataforma, con los mismos campos que la tabla de Room. Unas cientos de filas de texto no justifican una base de datos, y esto corre en `commonTest` mientras que IndexedDB serían cien líneas de interop que nadie puede probar |
| ~~D7~~ | ~~`:androidApp` sin ProGuard/R8 configurado para release~~ | **Saldada**: `minify` y `shrinkResources` activados, con reglas cortas y justificadas, y `assembleRelease` en CI para que R8 se ejecute de verdad |
| ~~D14~~ | ~~El motor de Web escanea pero no muestra visor~~ | **Saldada**: el `<video>` se coloca sobre el canvas desde `onGloballyPositioned`. A cambio tapa el overlay, declarado con `occludesOverlay` |
| ~~D15~~ | ~~El texto que se copia de un WiFi lo compone el dominio~~ | **Saldada**: `shareableContent()` devuelve la estructura y la pantalla la redacta con sus recursos. La acción del ViewModel lleva el texto ya hecho |
| D13 | Desktop y Web se quedan sin el baseline de comparación: zxing-cpp no publica artefacto JVM ni wasmJs (ADR-0008). En Desktop hoy no hay ningún decodificador; el candidato es `com.google.zxing:core`, y entraría al catálogo **como motor propio**, no con el nombre de zxing-cpp. El selector de imágenes de escritorio ya existe: lo que falta es el decodificador | Fase 5 |
