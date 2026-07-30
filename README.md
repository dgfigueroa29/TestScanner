# TestScanner

Banco de pruebas de **motores de escaneo de códigos de barras y QR**, en Compose Multiplatform.

El objetivo no es leer un código: es poder **elegir entre varias alternativas de escaneo**,
compararlas y degradar con elegancia cuando una no está disponible — sobre Android, iOS, Desktop
y Web con un único código base.

---

## Estado actual — Fase 1 (fundaciones)

| | |
|---|---|
| Arquitectura y SPI de motores | ✅ completos |
| Catálogo de los 7 motores con capacidades | ✅ declarado |
| Selección automática + cadena de fallback | ✅ implementados y testeados |
| Motor de entrada manual | ✅ funcional en las 4 plataformas |
| Motores de cámara (GMS, ML Kit, Vision, ZXing, navegador, OCR) | ⏳ fases 2–4 |

La app arranca y el catálogo muestra las siete alternativas con su estado real; los motores aún no
implementados se declaran como tales, con la fase en la que llegan. Ver `docs/ROADMAP.md`.

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
core/domain         casos de uso, políticas de selección y decoradores del SPI
core/data           registro de motores, preferencias e historial
core/designsystem   tema y componentes Compose compartidos
core/permissions    abstracción de permisos por plataforma
engines/*           un módulo por alternativa de escaneo
feature/scanner     MVI + pantalla de escaneo
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
./gradlew check                                      # tests + detekt
```

iOS se construye desde `iosApp/` en Xcode (requiere macOS).

---

## Añadir un motor de escaneo

El coste es constante: **un módulo y una entrada en el catálogo**. Ni la UI ni el dominio cambian.
Los ocho pasos están en [`docs/ENGINES.md`](docs/ENGINES.md#cómo-añadir-un-motor).
