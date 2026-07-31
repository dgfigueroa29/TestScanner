# TestScanner

Banco de pruebas de **motores de escaneo de códigos de barras y QR**, en Compose Multiplatform.

El objetivo no es leer un código: es poder **elegir entre varias alternativas de escaneo**,
compararlas y degradar con elegancia cuando una no está disponible — sobre Android, iOS, Desktop
y Web con un único código base.

---

## Estado actual — los 8 motores escritos, iOS pendiente de máquina

| | |
|---|---|
| Arquitectura y SPI de motores | ✅ completos |
| Catálogo de los 8 motores con capacidades | ✅ declarado |
| Selección automática + cadena de fallback | ✅ implementados y testeados |
| Suite de contrato que todo motor debe pasar | ✅ implementada, y aplicada a los decoradores y a la cadena completa |
| Comparador de motores con marcador en vivo (G5) | ✅ implementado y en la UI |
| Motor de entrada manual | ✅ funcional en las 4 plataformas |
| Google Code Scanner y ML Kit + CameraX (Android) | ✅ implementados, sin compilar aún |
| Historial persistente | ✅ Room en Android, iOS y Desktop; en Web, JSON en el almacén del navegador |
| Preferencias persistentes | ✅ las cuatro plataformas |
| CI en GitHub Actions | ✅ detekt, tests, Android, Desktop, Web y iOS |
| Vision / AVFoundation (iOS) | ✅ implementado, sin compilar aún |
| BarcodeDetector del navegador (Web) | ✅ implementado, con visor sobre el canvas |
| OCR con ML Kit Text Recognition (Android) | ✅ implementado; en iOS irá con Vision, no con ML Kit |
| Escaneo desde imagen (RF-07) | ✅ selector en las cuatro plataformas, sin pedir permisos |
| Exportación del historial | ✅ CSV y JSON, guardado en las cuatro plataformas |
| ZXing-cpp (Android + iOS) | ✅ implementado — el mismo decodificador C++ en ambas, que es lo que hace comparables las lecturas |
| Acciones sobre el resultado (RF-13) | ✅ copiar, compartir y abrir, según el significado del código |
| Navegación | ✅ propia, con backstack que sobrevive a rotar la pantalla |
| Build de release con R8 | ✅ `minify` y `shrinkResources`, con `assembleRelease` en CI |
| ZXing en Java (Desktop) | ✅ el único decodificador de escritorio; **verificado de verdad**, decodificando imágenes generadas en el test |

El catálogo muestra las ocho alternativas con su estado real; los motores aún no implementados se
declaran como tales, con la fase en la que llegan. Ver `docs/ROADMAP.md`.

Lo que queda fuera por ahora, y por qué:

- **iOS está despriorizado**, no abandonado: el código está escrito, pero compilarlo exige macOS y
  probarlo un dispositivo, así que no marca el ritmo. Lo que avanza es lo que Android, Escritorio y
  Web pueden confirmar.
- **No hay tests instrumentados y no los va a haber.** Sin emulador en CI, un test que exija
  dispositivo nunca se ejecuta y da una falsa sensación de red. El ROADMAP dice exactamente qué queda
  cubierto sin dispositivo y qué no.
- **Escritorio lee archivos pero no cámara**: hay decodificador (ZXing en Java) y no hay captura de
  webcam, así que una sesión en vivo cae a la entrada manual.
- **Web no tiene respaldo tras el navegador**: zxing-cpp no publica artefacto wasmJs, así que quien
  cierra esa cadena es la entrada manual.

> **Sin compilar con Gradle todavía.** El CI de `.github/workflows/verify.yml` es lo que dará el
> primer veredicto completo en cuanto se abra un PR.
> El entorno donde se desarrolló no tenía acceso a
> `dl.google.com`, así que no hubo Android SDK ni artefactos de AGP/Compose. Lo verificado son los
> **353 tests del núcleo puro**, compilados y ejecutados con kotlinc 2.3.20 — el mismo compilador al que apunta el build. Todo lo que necesita
> Gradle — `build-logic`, las versiones del catálogo, los motores de plataforma y el código
> Compose — está pendiente de la primera compilación.

---

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/SDD.md`](docs/SDD.md) | Documento de diseño: requisitos, arquitectura, SPI, calidad, plan de migración |
| [`docs/ENGINES.md`](docs/ENGINES.md) | Catálogo de motores: formatos, capacidades y prioridad por plataforma |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fases, criterios de salida y deuda técnica aceptada |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura con su contexto y sus consecuencias |

Lectura mínima para tocar código: **§7 del SDD** (el Scanner Engine SPI) y **ADR-0002**.

---

## Estructura

```
core/model          modelo puro: Barcode, BarcodeFormat, Detection, ScanRequest
core/scanner-api    el SPI + el catálogo declarativo de motores
core/scanner-ui     capacidad de UI del motor: CameraPreviewEngine
core/scanner-testing suite de contrato que todo motor hereda
core/domain         casos de uso, políticas de selección y decoradores del SPI
core/data           registro de motores, preferencias e historial
core/designsystem   tema y componentes Compose compartidos
core/permissions    abstracción de permisos por plataforma
core/platform       acciones del sistema: copiar, compartir, abrir, elegir imagen, guardar archivo
core/database       Room KMP: historial persistente (sin target wasmJs)
engines/*           un módulo por alternativa de escaneo
feature/scanner     MVI, pantalla de escaneo y comparador de motores
feature/history     historial filtrable por motor
composeApp          raíz Compose Multiplatform y composition root de la DI
androidApp          shell de Android
iosApp              shell de iOS (Xcode)
```

La regla de dependencias es estricta: un módulo `engines/*` depende solo de `:core:scanner-api`
y de su SDK nativo. Nunca de `:feature:*`, ni de `:core:data`, ni de otro motor.

---

## Cómo construir

```bash
./gradlew :androidApp:assembleDebug                  # Android
./gradlew :composeApp:run                            # Desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun    # Web
./gradlew detekt                                     # análisis estático
./gradlew check                                      # tests + detekt
```

iOS se construye desde `iosApp/` en Xcode (requiere macOS).

---

## Añadir un motor de escaneo

El coste es constante: **un módulo y una entrada en el catálogo**. Ni la UI ni el dominio cambian.
Los ocho pasos están en [`docs/ENGINES.md`](docs/ENGINES.md#cómo-añadir-un-motor).
