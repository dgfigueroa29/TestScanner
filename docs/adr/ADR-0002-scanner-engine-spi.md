# ADR-0002 — Motores de escaneo como SPI con capacidades declarativas

- **Estado:** Aceptada
- **Fecha:** 2026-07-30

## Contexto

El objetivo principal del producto es ofrecer **varias alternativas de escaneo**. La forma ingenua
de conseguirlo es un `when (motorSeleccionado)` en el ViewModel o en un repositorio, con una rama
por motor.

Ese enfoque se rompe rápido porque los motores **no son intercambiables de forma uniforme**:

- El GMS Code Scanner abre su propia UI a pantalla completa y no acepta overlay, ni linterna, ni
  modo continuo.
- ML Kit necesita descargar su modelo la primera vez.
- La `BarcodeDetector` del navegador puede no existir según el navegador del usuario.
- El OCR no decodifica: infiere.

Un `when` obliga a que la UI conozca esas particularidades, y cada motor nuevo toca la UI, el
ViewModel y el dominio.

## Decisión

Definir un **SPI** (`BarcodeScannerEngine`) con tres piezas:

1. Un **contrato mínimo** que todo motor cumple: `id`, `descriptor`, `availability()`, `scan()`.
2. **Interfaces segregadas opcionales** para capacidades que no todos tienen:
   `ImageDecodingEngine`, `CameraControlEngine`.
3. **Capacidades como datos** (`ScannerCapabilities`): la UI y el selector razonan sobre un objeto
   de datos, no sobre el tipo concreto del motor.

## Justificación

- **La UI se vuelve genérica.** La pantalla de catálogo se renderiza recorriendo descriptores. Los
  controles de linterna aparecen si `capabilities.supportsTorch`. No hay ninguna referencia a un
  motor concreto en `:feature:scanner`.
- **El contrato no miente.** Poner `setTorch()` en la interfaz base obligaría al GMS Code Scanner
  a lanzar `UnsupportedOperationException`: un contrato que promete lo que no cumple. Con
  interfaces segregadas, la ausencia de la capacidad es verificable en tiempo de compilación
  (`engine as? CameraControlEngine`).
- **La indisponibilidad es dato, no error.** `EngineAvailability` distingue *no soportado*,
  *falta permiso*, *falta descarga* y *aún no implementado*. Eso permite que el catálogo esté
  completo desde la Fase 1 y que la UI explique al usuario **por qué** un motor no se puede usar.
- **Coste de añadir un motor: constante.** Un módulo nuevo + una entrada en el catálogo. Ni el
  dominio ni la UI cambian (RNF-07).

## Consecuencias

**Positivas**
- La política de selección y fallback es lógica pura sobre datos → testeable en `commonTest` sin
  cámara ni dispositivo.
- Los motores son sustituibles y eliminables; ninguno es estructural.

**Negativas y su gestión**
- Más indirección: leer el código requiere entender el SPI antes que cualquier motor concreto.
  Mitigado con este ADR y §7 del SDD como lectura obligatoria de onboarding.
- Un motor puede **declarar capacidades falsas**. Mitigado con la suite de contrato
  (`BarcodeScannerEngineContractTest`, §13.2), que contrasta lo declarado con lo observado.
- Las capacidades declaradas pueden quedar obsoletas frente a cambios del SDK. Mitigado revisando
  `docs/ENGINES.md` en cada bump de dependencia de un motor.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `when (engineId)` en el ViewModel | Cada motor nuevo toca UI + dominio; imposible testear sin dispositivo |
| Una interfaz "gorda" con todos los métodos | Obliga a `UnsupportedOperationException`; contrato deshonesto |
| Un motor único con adaptadores internos | Impide comparar motores, que es el objetivo del producto |
| Herencia (`AbstractScannerEngine`) | El fallback y la telemetría se componen mejor como decoradores |
