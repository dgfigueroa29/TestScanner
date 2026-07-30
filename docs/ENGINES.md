# Catálogo de motores de escaneo

Fuente de verdad del catálogo. El registro en código (`ScannerEngineCatalog`) debe coincidir con
esta tabla — hay un test que verifica que los IDs y las fases no divergen.

---

## Tabla maestra

| ID | Nombre | Plataformas | Fuente | Fase | Dependencia |
|---|---|---|---|:---:|---|
| `GMS_CODE_SCANNER` | Google Code Scanner | Android | Cámara (UI propia) | 2 ✅ | `com.google.android.gms:play-services-code-scanner` |
| `MLKIT_CAMERAX` | ML Kit + CameraX | Android | Cámara | 2 ✅ | `com.google.mlkit:barcode-scanning` + `androidx.camera:*` |
| `VISION_IOS` | Vision / AVFoundation | iOS | Cámara | 3 | Framework del sistema |
| `ZXING_CPP` | ZXing-cpp | Android, iOS, Desktop | Cámara + imagen | 3 | Binding KMP de zxing-cpp |
| `BROWSER_DETECTOR` | BarcodeDetector API | Web | Cámara + imagen | 4 | API del navegador |
| `MLKIT_OCR` | ML Kit Text Recognition | Android, iOS | Cámara + imagen | 4 | `com.google.mlkit:text-recognition` |
| `MANUAL_INPUT` | Entrada manual | Todas | Teclado | **1** | Ninguna |

---

## Matriz de formatos por motor

Leyenda: ✅ soportado · ⚠️ parcial o dependiente de versión · ❌ no soportado

| Formato | GMS | ML Kit | Vision | ZXing-cpp | Browser | OCR | Manual |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| QR Code | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Data Matrix | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| Aztec | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| PDF417 | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| EAN-13 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| EAN-8 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| UPC-A | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| UPC-E | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Code 39 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Code 93 | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ |
| Code 128 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Codabar | ✅ | ✅ | ⚠️ | ✅ | ⚠️ | ❌ | ✅ |
| ITF | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ |
| DataBar / RSS | ❌ | ❌ | ⚠️ | ✅ | ❌ | ❌ | ✅ |
| MaxiCode | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Micro QR | ❌ | ❌ | ⚠️ | ✅ | ❌ | ❌ | ✅ |
| rMQR | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |

Las marcas ⚠️ del OCR reflejan que el motor no decodifica la simbología: **lee el número impreso
bajo el código** y el dominio infiere el formato validando su checksum. Solo funciona con
simbologías cuyo valor va impreso en texto (típicamente 1D de producto).

---

## Capacidades por motor

| Capacidad | GMS | ML Kit | Vision | ZXing-cpp | Browser | OCR | Manual |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Cámara en vivo | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Imagen estática | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Múltiples códigos a la vez | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Escaneo continuo | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| UI propia del motor | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Linterna | ❌ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ |
| Zoom | ❌ | ✅ | ✅ | ✅ | ⚠️ | ✅ | ❌ |
| Puntos de esquina | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Confianza reportada | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Requiere permiso de cámara | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Requiere red | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Descarga en tiempo de ejecución | ✅ | ⚠️ | ❌ | ❌ | ❌ | ⚠️ | ❌ |

`GMS_CODE_SCANNER` no requiere permiso de cámara porque el escaneo ocurre en un proceso de Google
Play Services, fuera de la app. Es su ventaja distintiva y la razón de que encabece la prioridad
en Android para escaneos puntuales.

---

## Prioridad de selección automática (RF-04)

Orden por defecto cuando el usuario no ha fijado un motor. El selector recorre la lista, descarta
los que no están `Available` y los que no cubren los formatos pedidos, y devuelve la cadena
resultante como preferido + fallbacks.

| Plataforma | Cadena por defecto |
|---|---|
| Android | `GMS_CODE_SCANNER` → `MLKIT_CAMERAX` → `ZXING_CPP` → `MLKIT_OCR` → `MANUAL_INPUT` |
| iOS | `VISION_IOS` → `ZXING_CPP` → `MLKIT_OCR` → `MANUAL_INPUT` |
| Desktop | `ZXING_CPP` → `MANUAL_INPUT` |
| Web | `BROWSER_DETECTOR` → `ZXING_CPP` → `MANUAL_INPUT` |

Excepciones de la política:

- Si el `ScanRequest` pide **escaneo continuo** o **múltiples códigos**, `GMS_CODE_SCANNER` queda
  descartado por capacidades y `MLKIT_CAMERAX` encabeza la cadena en Android.
- Si el `ScanRequest` pide **imagen estática**, solo entran motores con `ScanSource.StaticImage`.
- `MANUAL_INPUT` cierra siempre la cadena: garantiza que nunca hay un estado "no se puede escanear".

---

## Cómo añadir un motor

1. Crear el módulo `engines/<nombre>/` con target(s) de la(s) plataforma(s) que soporte.
2. Depender únicamente de `:core:scanner-api`, `:core:model` y del SDK correspondiente.
3. Implementar `BarcodeScannerEngine` y, si aplica, `ImageDecodingEngine` / `CameraControlEngine`.
4. Declarar un `ScannerEngineDescriptor` honesto — las capacidades declaradas se contrastan con el
   comportamiento real en la suite de contrato.
5. Añadir el ID a `ScannerEngineId`, la fila a este documento y la entrada a
   `ScannerEngineCatalog`.
6. Registrarlo en el `platformEngines()` del target correspondiente en `:composeApp`.
7. Heredar `BarcodeScannerEngineContractTest` aportando la factory del motor.
8. Añadir el módulo a `settings.gradle.kts`.

Ningún paso toca `:feature:scanner` ni `:core:domain`. Si un motor nuevo obliga a modificarlos, es
señal de que el SPI se quedó corto y hay que extenderlo de forma explícita — no a parchear la UI.
