# ADR-0004 — `Flow<ScanEvent>` como API de sesión de escaneo

- **Estado:** Aceptada
- **Fecha:** 2026-07-30

## Contexto

La API de escaneo puede modelarse de tres formas:

1. `suspend fun scan(): Result<Barcode>` — un resultado, se suspende hasta detectar.
2. `fun scan(onDetected: (Barcode) -> Unit)` + `fun stop()` — callback con control manual.
3. `fun scan(request: ScanRequest): Flow<ScanEvent>` — stream de eventos.

Los requisitos que condicionan la elección: escaneo continuo (RF-10), detección de múltiples
códigos a la vez, overlay en vivo con puntos de esquina, y medición de latencia por motor (G5).

## Decisión

Opción 3: **`fun scan(request: ScanRequest): Flow<ScanEvent>`**.

## Justificación

- **Un `suspend` de un resultado no cubre el modo continuo.** Obligaría a una segunda API
  (`scanContinuously`), duplicando la gestión del ciclo de vida en cada motor y en el fallback.
- **El ciclo de vida se vuelve estructural.** Cancelar la corrutina que colecta el `Flow` apaga la
  cámara en el `awaitClose` del `callbackFlow`. No hay un `stop()` que alguien pueda olvidarse de
  llamar, ni fugas de sesión al rotar la pantalla o navegar hacia atrás.
- **Los eventos intermedios tienen valor.** `FrameAnalyzed` alimenta las métricas de FPS y latencia
  que sustentan G5 (comparar motores). Con callbacks habría que añadir un segundo canal.
- **Los errores transitorios no matan la sesión.** `ScanEvent.Failed` es un elemento del stream;
  un frame corrupto no cierra la cámara. Solo un fallo fatal se sigue de `SessionEnded`.
- **El fallback se compone.** `FallbackScannerEngine` es un `Flow` que, ante `Failed` fatal, cambia
  al siguiente motor y sigue emitiendo. Con `suspend` sería try/catch anidado; con callbacks sería
  una máquina de estados a mano.

## Consecuencias

**Positivas**
- Testeable con Turbine sin cámara: un motor falso emite la secuencia de eventos deseada.
- Backpressure y `conflate()` disponibles cuando el análisis va más lento que los frames.

**Negativas y su gestión**
- Cada motor debe implementar `callbackFlow` + `awaitClose` correctamente. La suite de contrato
  (§13.2 del SDD) verifica que la cancelación libera recursos.
- El GMS Code Scanner es intrínsecamente *one-shot* (abre su UI, devuelve un resultado). Se adapta
  emitiendo `SessionStarted → Detected → SessionEnded` y declarando
  `supportsContinuousScan = false`, de modo que el selector no lo elija cuando se pide continuo.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `suspend fun scan(): Result<Barcode>` | No modela modo continuo, múltiples códigos ni telemetría |
| Callbacks + `stop()` | Ciclo de vida manual, propenso a fugas; fallback difícil de componer |
| `SharedFlow` caliente en un singleton | La sesión de cámara debe ser fría y ligada al consumidor |
