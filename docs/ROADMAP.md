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
- [x] Tests de dominio: selección, fallback, parser, catálogo
- [x] SDD, 6 ADRs y catálogo de motores documentados

**Criterio de salida:** la app arranca en Android, Desktop y Web; el catálogo lista los 7 motores
con su estado real; los tests de `:core:domain` y `:core:data` pasan en CI.

---

## Fase 2 — Android real

- [ ] `build-logic/` con convention plugins (`kmp-library`, `kmp-compose`, `android-app`)
- [ ] `:engines:gms-code-scanner` — Google Code Scanner, sin permisos
- [ ] `:engines:mlkit-camerax` — ML Kit Barcode + CameraX, con overlay propio
- [ ] `CameraPreview` actual de Android (`PreviewView` en `AndroidView`)
- [ ] `PermissionController` actual de Android + flujo de denegación permanente
- [ ] Historial persistente con Room KMP (`:feature:history`)
- [ ] Suite de contrato de motores ejecutándose en `androidTest`
- [ ] CI: `assembleDebug` + lint + detekt en cada PR

**Criterio de salida:** escaneo real en Android alternando dos motores en caliente, con fallback
verificable desactivando Play Services.

---

## Fase 3 — iOS

- [ ] `iosApp/` — proyecto Xcode con host SwiftUI sobre `MainViewController`
- [ ] `:engines:vision-ios` — `AVCaptureSession` + `VNDetectBarcodesRequest`
- [ ] `CameraPreview` actual de iOS (`UIKitView` con `AVCaptureVideoPreviewLayer`)
- [ ] `:engines:zxing-cpp` — mismo decodificador en Android, iOS y Desktop
- [ ] Revisión de ADR-0005: migrar a `navigation-compose` multiplataforma si el grafo lo justifica
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

- [ ] Comparador: ejecutar dos motores sobre el mismo stream y contrastar resultados
- [ ] Métricas de latencia, FPS y tasa de acierto por motor (cierra G5)
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
| D1 | Sin convention plugins: cada módulo repite su configuración KMP | Fase 2 |
| D2 | Preferencias en memoria, no persistidas | Fase 2 (`multiplatform-settings`) |
| D3 | Historial en memoria | Fase 2 (Room KMP) |
| D4 | Navegación propia sin deep links ni restauración de estado | Fase 3 (revisión ADR-0005) |
| D5 | Strings hardcodeados en la UI, sin `composeResources` | Fase 2 |
| D6 | Sin suite de contrato ejecutada contra motores reales (no hay motores de cámara aún) | Fase 2 |
| D7 | `:androidApp` sin ProGuard/R8 configurado para release | Fase 2 |
