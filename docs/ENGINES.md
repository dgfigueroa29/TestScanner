# Catálogo de motores de escaneo

Fuente de verdad del catálogo. El registro en código (`ScannerEngineCatalog`) debe coincidir con
esta tabla — hay un test que verifica que los IDs y las fases no divergen.

---

## Tabla maestra

| ID | Nombre | Plataformas | Fuente | Fase | Dependencia |
|---|---|---|---|:---:|---|
| `GMS_CODE_SCANNER` | Google Code Scanner | Android | Cámara (UI propia) | 2 ✅ | `com.google.android.gms:play-services-code-scanner` |
| `MLKIT_CAMERAX` | ML Kit + CameraX | Android | Cámara | 2 ✅ | `com.google.mlkit:barcode-scanning` + `androidx.camera:*` |
| `VISION_IOS` | Vision / AVFoundation | iOS | Cámara | 3 ✅ | Framework del sistema |
| `ZXING_CPP` | ZXing-cpp | Android, iOS | Cámara + imagen | 3 ✅ | `io.github.zxing-cpp:android` (Android) y `:kotlin-native` (iOS) — [ADR-0008](adr/ADR-0008-baseline-zxing-cpp.md) |
| `ZXING_JAVA` | ZXing (Java) | Desktop | Imagen | 5 ✅ | `com.google.zxing:core` |
| `BROWSER_DETECTOR` | BarcodeDetector API | Web | Cámara + imagen | 4 ✅ | API del navegador |
| `MLKIT_OCR` | ML Kit Text Recognition | Android, iOS | Cámara + imagen | 4 ✅ Android | `com.google.mlkit:text-recognition` |
| `MANUAL_INPUT` | Entrada manual | Todas | Teclado | **1** | Ninguna |

---

## Matriz de formatos por motor

Leyenda: ✅ soportado · ⚠️ parcial o dependiente de versión · ❌ no soportado

| Formato | GMS | ML Kit | Vision | ZXing-cpp | ZXing Java | Browser | OCR | Manual |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| QR Code | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| Data Matrix | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| Aztec | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| PDF417 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| EAN-13 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| EAN-8 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| UPC-A | ✅ | ✅ | ❌¹ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| UPC-E | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Code 39 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Code 93 | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ |
| Code 128 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Codabar | ✅ | ✅ | ⚠️ | ✅ | ✅ | ⚠️ | ❌ | ✅ |
| ITF | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ✅ |
| DataBar / RSS | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ |
| MaxiCode | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ |
| Micro QR | ❌ | ❌ | ✅ | ✅ | ❌⁶ | ❌ | ❌ | ✅ |
| rMQR | ❌ | ❌ | ❌ | ✅ | ❌⁶ | ❌ | ❌ | ✅ |

¹ AVFoundation no tiene un tipo UPC-A: devuelve esos códigos como EAN-13 con un cero delante, que
es lo que son. Declararlo como soportado sería prometer una distinción que el sistema no hace.

² El motor de iOS usa `AVCaptureMetadataOutput`, que solo trabaja sobre vídeo en vivo. La imagen
estática llegará con RF-07 usando `VNDetectBarcodesRequest` del framework Vision.

³ La `BarcodeDetector` API no controla la cámara: solo recibe imágenes. La linterna se podría pedir
por constraints de `MediaStreamTrack`, pero solo la soportan algunos navegadores de Android; hasta
que se implemente, el motor no declara la capacidad y la UI no muestra los controles.

⁴ En Web el visor no está *dentro* del árbol de Compose: no hay equivalente de `AndroidView` ni de
`UIKitView`, así que el `<video>` vive en el documento y el composable solo le dice qué rectángulo
ocupar. Va **encima** del canvas —el tema pinta su fondo en toda la superficie, así que detrás no se
vería—, y por eso tapa el overlay de detección. El motor lo declara con `occludesOverlay` y la
pantalla deja de pintarlo, en lugar de ejecutar un dibujo que nadie ve.

⁵ `com.google.mlkit:text-recognition` es la variante *bundled*: el modelo latino viaja en el APK, a
diferencia del detector de códigos, que sí se descarga en el primer uso.

⁶ `com.google.zxing.BarcodeFormat` no tiene constantes para Micro QR ni rMQR. El port a C++ sí las
lee, y esa diferencia entre dos motores del mismo linaje es justo el tipo de dato que la app existe
para producir.

⁷ ZXing devuelve dos puntos en los códigos lineales y cuatro en los 2D, así que no son las esquinas
que espera el overlay. Se declara `reportsCornerPoints = false` y no se reportan: construir un
rectángulo a partir de dos puntos sería dibujar una suposición.

Las marcas ⚠️ del OCR reflejan que el motor no decodifica la simbología: **lee el número impreso
bajo el código** y el dominio infiere el formato validando su checksum. Solo funciona con
simbologías cuyo valor va impreso en texto (típicamente 1D de producto).

---

## Capacidades por motor

| Capacidad | GMS | ML Kit | Vision | ZXing-cpp | ZXing Java | Browser | OCR | Manual |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Cámara en vivo | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ |
| Imagen estática | ❌ | ✅ | ⏳² | ✅ | ✅ | ✅ | ✅ | ❌ |
| Múltiples códigos a la vez | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Escaneo continuo | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ |
| UI propia del motor | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Linterna | ❌ | ✅ | ✅ | ✅ | ❌ | ❌³ | ✅ | ❌ |
| Zoom | ❌ | ✅ | ✅ | ✅ | ❌ | ❌³ | ✅ | ❌ |
| Puntos de esquina (normalizados) | ❌ | ✅ | ✅ | ✅ | ❌⁷ | ✅ | ✅ | ❌ |
| Superficie de preview propia | ❌ | ✅ | ✅ | ✅ | ❌ | ✅⁴ | ✅ | ❌ |
| Confianza reportada | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Requiere permiso de cámara | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ |
| Requiere red | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Descarga en tiempo de ejecución | ✅ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌⁵ | ❌ |

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
| Desktop | `ZXING_JAVA` (solo imagen) → `MANUAL_INPUT` |
| Web | `BROWSER_DETECTOR` → `MANUAL_INPUT` |

Excepciones de la política:

- Si el `ScanRequest` pide **escaneo continuo** o **múltiples códigos**, `GMS_CODE_SCANNER` queda
  descartado por capacidades y `MLKIT_CAMERAX` encabeza la cadena en Android.
- Si el `ScanRequest` pide **imagen estática**, solo entran motores con `ScanSource.StaticImage`.
- `MANUAL_INPUT` cierra siempre la cadena: garantiza que nunca hay un estado "no se puede escanear".
- **En escritorio, una petición de cámara en vivo cae directamente a `MANUAL_INPUT`**: `ZXING_JAVA`
  no declara esa fuente, así que el selector lo descarta antes de elegirlo. El decodificador está
  ahí, pero no hay captura de webcam que lo alimente.
- **Web no tiene respaldo tras el navegador**: zxing-cpp no publica artefacto wasmJs (ADR-0008), así
  que listarlo en esa cadena sería una entrada muerta. Lo que la cierra es la entrada manual.

---

## Cómo añadir un motor

1. Crear el módulo `engines/<nombre>/` con target(s) de la(s) plataforma(s) que soporte.
2. Depender únicamente de `:core:scanner-api`, `:core:model` y del SDK correspondiente.
3. Implementar `BarcodeScannerEngine` y, si aplica, las capacidades opcionales:
   `ImageDecodingEngine`, `CameraControlEngine`, `TextInputEngine` y `CameraPreviewEngine`
   (esta última si el motor aporta superficie de vídeo — ver ADR-0007).
4. Declarar un `ScannerEngineDescriptor` honesto — las capacidades declaradas se contrastan con el
   comportamiento real en la suite de contrato.
5. Añadir el ID a `ScannerEngineId`, la fila a este documento y la entrada a
   `ScannerEngineCatalog`.
6. Registrarlo en el `platformModule()` del target correspondiente en `:composeApp`.
7. Heredar `BarcodeScannerEngineContractTest` aportando la factory del motor.
8. Añadir el módulo a `settings.gradle.kts`.

Ningún paso toca `:feature:scanner` ni `:core:domain`. Si un motor nuevo obliga a modificarlos, es
señal de que el SPI se quedó corto y hay que extenderlo de forma explícita — no a parchear la UI.

El paso 7 no es opcional: la suite de contrato es lo que impide que un motor declare capacidades que
luego no cumple, y las capacidades declaradas son de lo que dependen el selector y la UI entera.
