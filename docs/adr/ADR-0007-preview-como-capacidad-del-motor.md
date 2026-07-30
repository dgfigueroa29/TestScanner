# ADR-0007 — El preview de cámara es una capacidad del motor, no de la feature

- **Estado:** Aceptada
- **Fecha:** 2026-07-30
- **Sustituye a:** el diseño esbozado en §9.2 del SDD v1.0 (`expect @Composable CameraPreview`)

## Contexto

La superficie de vídeo es lo único de la pantalla que es **irreduciblemente nativo**, y cada motor
la produce de forma distinta: `PreviewView` de CameraX en Android, `AVCaptureVideoPreviewLayer` en
iOS, un `<video>` alimentado por `getUserMedia()` en el navegador. El Google Code Scanner no produce
ninguna, porque abre su propia pantalla a pantalla completa.

El SDD v1.0 proponía un `expect @Composable fun CameraPreview(controller, modifier)` en la capa de
UI. Al implementarlo aparecieron dos problemas que no se veían sobre el papel:

1. El `actual` de Android necesita el tipo concreto del motor para obtener su
   `LifecycleCameraController`. Eso obliga a que `:feature:scanner` dependa de
   `:engines:mlkit-camerax`, y entonces **añadir un motor de cámara pasa a tocar la feature** — lo
   contrario de RNF-07.
2. Evitar esa dependencia exigiría exponer un handle opaco (`Any`) desde el SPI y hacer un cast por
   motor en la UI. Es el `when (engineId)` que ADR-0002 existe para eliminar, disfrazado.

## Decisión

El preview pasa a ser una **capacidad opcional del motor**, en la línea de `CameraControlEngine` e
`ImageDecodingEngine`:

```kotlin
// :core:scanner-ui
interface CameraPreviewEngine {
    @Composable fun CameraPreview(modifier: Modifier)
}
```

La pantalla hace `(engine as? CameraPreviewEngine)?.CameraPreview(modifier)` y **no nombra ningún
motor**. El overlay de detección se sigue pintando en Compose común encima de esa superficie.

Se crea `:core:scanner-ui` — separado de `:core:scanner-api` — para que el SPI de dominio siga
siendo Compose-free y los motores que no pintan nada (OCR sobre imagen, entrada manual) no arrastren
Compose.

## Justificación

- **Coste constante de añadir un motor.** El motor de iOS traerá su `UIKitView` y el de Wasm su
  `<video>`; ninguno de los dos tocará `:feature:scanner`.
- **La capacidad es verificable en compilación.** Un motor sin preview simplemente no implementa la
  interfaz, y la UI no muestra visor — igual que ocurre con la linterna.
- **Coordenadas comparables.** Al quedar el overlay en Compose común, los `cornerPoints` se
  normalizan a `[0, 1]` en cada motor (que es quien conoce el tamaño del frame) y la UI los mapea
  sabiendo cómo escala el preview. Si el modelo llevara píxeles, el overlay dependería de la
  resolución de análisis de cada motor y dejaría de ser comparable entre ellos — que es justo lo que
  el producto quiere medir.

## Consecuencias

**Positivas**
- `:feature:scanner` no depende de ningún módulo de motor y no lo hará nunca.
- El 100 % del diseño visual sigue siendo compartido; solo la superficie de vídeo es nativa.

**Negativas y su gestión**
- **Los módulos de motor con cámara dependen de Compose.** Es real y se asume: son módulos de
  plataforma, no de dominio. Los motores sin preview (`:engines:manual`, y el OCR sobre imagen de la
  Fase 4) no lo hacen, porque la dependencia está en `:core:scanner-ui` y no en el SPI.
- Un `@Composable` dentro de una interfaz obliga a que el módulo tenga el compilador de Compose. Es
  una línea en el `build.gradle.kts` del motor.
- El cast `as? CameraPreviewEngine` está concentrado en una única clase (`EnginePreviewResolver`) en
  lugar de repartido por la UI. Si aparecieran más capacidades de UI, esa clase es el sitio donde
  crecerían.
