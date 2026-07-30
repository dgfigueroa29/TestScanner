# Roadmap de migración

Cada fase es entregable e independientemente verificable. Una fase no se cierra hasta que su
criterio de salida se cumple en CI.

---

## Fase 1 — Fundaciones ✅ (entregable actual)

Convertir el repositorio en un proyecto Compose Multiplatform con la arquitectura de motores
completa, aunque todavía sin motores de cámara reales.

- [x] Build KMP/CMP: Kotlin DSL, version catalog, Gradle 8.13, Kotlin 2.1, AGP 8.9
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
- [x] Suite de contrato de motores (`BarcodeScannerEngineContractTest`)
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
- [ ] Suite de contrato ejecutándose contra los motores de cámara en `androidTest`

**Criterio de salida:** escaneo real en Android alternando dos motores en caliente, con fallback
verificable desactivando Play Services.

> Pendiente de la primera compilación con Gradle: el entorno donde se escribió esta fase no tenía
> acceso a `dl.google.com`, así que las APIs de ML Kit y CameraX están sin compilar. El núcleo puro
> sí está verificado (82 tests en verde con kotlinc).

---

## Fase 3 — iOS

- [ ] `iosApp/` — proyecto Xcode con host SwiftUI sobre `MainViewController`
- [ ] `:engines:vision-ios` — `AVCaptureSession` + `VNDetectBarcodesRequest`
- [ ] `CameraPreview` actual de iOS (`UIKitView` con `AVCaptureVideoPreviewLayer`)
- [ ] `:engines:zxing-cpp` — mismo decodificador en Android, iOS y Desktop
- [ ] Revisión de ADR-0005: el grafo ya tiene 2 destinos; migrar a `navigation-compose` si llega a 6 o aparecen deep links

- [ ] CI: `linkDebugFrameworkIosSimulatorArm64` en runner macOS

**Criterio de salida:** escaneo real en iOS; ZXing-cpp produce resultados comparables entre
Android e iOS sobre el mismo set de imágenes de referencia.

---

## Fase 4 — Web y OCR

- [ ] `:engines:browser-detector` — `BarcodeDetector` con detección de soporte del navegador
- [ ] Fallback a ZXing-cpp compilado a Wasm cuando el navegador no expone la API
- [ ] `:engines:mlkit-ocr` — Text Recognition + inferencia de formato con validación de checksum
- [ ] Escaneo desde imagen/galería (RF-07) en las cuatro plataformas
- [ ] Selector de archivos multiplataforma

**Criterio de salida:** las cuatro plataformas escanean con al menos dos motores cada una; el OCR
recupera correctamente EAN-13 impresos sobre códigos dañados.

---

## Fase 5 — Producto

- [x] Comparador: ejecutar varios motores sobre la misma petición (`ComparingScannerEngine`)
- [x] Métricas de latencia y acierto por motor (`EngineScoreboard`)
- [x] Pantalla "Comparar" con el marcador en vivo — **cierra G5**
- [ ] Exportación del historial (CSV/JSON) y acciones sobre resultados
- [ ] Play Feature Delivery para los motores pesados de Android (RNF-06)
- [ ] Accesibilidad completa (RNF-05) y auditoría de privacidad (RNF-03)

**Criterio de salida:** el usuario puede responder, dentro de la app y con datos, la pregunta
"¿qué motor funciona mejor para este código en este dispositivo?".

---

## Deuda técnica aceptada en la Fase 1

Registrada de forma explícita para que no se olvide:

| # | Deuda | Se salda en |
|---|---|---|
| ~~D1~~ | ~~Sin convention plugins: cada módulo repite su configuración KMP~~ | **Saldada**: `build-logic/` con `testscanner.kmp.library`, `.kmp.compose` y `.android.application` |
| ~~D2~~ | ~~Preferencias en memoria, no persistidas~~ | **Saldada**: `multiplatform-settings` en las cuatro plataformas |
| ~~D3~~ | ~~Historial en memoria~~ | **Saldada**: Room KMP en Android, iOS y Desktop. En Web sigue en memoria porque Room no tiene target wasmJs |
| D4 | Navegación propia sin deep links ni restauración de estado | Fase 3 (revisión ADR-0005) |
| D5 | Strings hardcodeados en la UI, sin `composeResources` | Fase 2 |
| D6 | La suite de contrato existe y la pasa el motor manual, pero aún no se ejecuta contra motores de cámara reales | Fase 2 |
| ~~D8~~ | ~~El zoom se declara como capacidad pero no hay control en la UI~~ | **Saldada**: slider derivado de `canControlZoom` |
| D10 | RF-07 (escanear desde imagen) está en el dominio y en el motor ML Kit, pero sin UI ni selector de archivos | Fase 4 |
| D11 | La comparación necesita dos motores de cámara: hasta ZXing-cpp (Fase 3) solo es utilizable en Android | Fase 3 |
| D9 | El historial de Web es de sesión: Room KMP no soporta wasmJs. Requiere un almacén propio sobre IndexedDB | Fase 4 |
| D7 | `:androidApp` sin ProGuard/R8 configurado para release | Fase 2 |
