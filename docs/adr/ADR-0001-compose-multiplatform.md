# ADR-0001 — Compose Multiplatform en lugar de KMP con UI nativa

- **Estado:** Aceptada
- **Fecha:** 2026-07-30
- **Contexto del proyecto:** Migración de WhyScan desde Android monolítico

## Contexto

WhyScan debe correr en Android, iOS, Desktop y Web. Existen dos estrategias:

1. **KMP + UI nativa por plataforma** — lógica compartida en Kotlin, UI en Compose (Android),
   SwiftUI (iOS), Compose Desktop y algo web.
2. **Compose Multiplatform** — lógica *y* UI compartidas, con `expect/actual` solo donde la
   plataforma es inevitable.

La UI de WhyScan es mayoritariamente **chrome alrededor de una superficie de cámara**:
selector de motor, catálogo de capacidades, overlay de detección, lista de resultados, historial.
Es UI de datos, no interacción con idioms de plataforma profundos.

## Decisión

Adoptar **Compose Multiplatform**.

## Justificación

- La UI compartible es casi toda la UI. Con la opción 1 escribiríamos cuatro veces la misma
  pantalla de catálogo de motores, que es puramente declarativa sobre `ScannerCapabilities`.
- El equipo ya es competente en Compose; SwiftUI sería una segunda curva de aprendizaje sin
  beneficio proporcional para este producto.
- El punto verdaderamente nativo — el preview de cámara — se aísla en **un solo `expect
  @Composable`** (§9.2 del SDD). El resto del diseño visual, incluido el overlay dibujado encima,
  es común.
- Objetivo G1 (≥ 85 % de código en `commonMain`) es inalcanzable con la opción 1.

## Consecuencias

**Positivas**
- Un solo lugar donde cambiar el diseño; imposible que las plataformas deriven visualmente.
- Los tests de UI sobre `XContent` stateless valen para todas las plataformas.

**Negativas y su gestión**
- La app no se sentirá 100 % nativa en iOS. Aceptable: es una herramienta técnica, no una app de
  consumo masivo. Se compensa usando gestos y transiciones de plataforma en `:core:designsystem`.
- Los tiempos de build de iOS (Kotlin/Native) son altos. Mitigado limitando el build de iOS a
  `main` y usando cachés de Gradle en CI (riesgo R4 del SDD).
- El target Web (Wasm) aún tiene aristas: peso inicial de descarga y accesibilidad limitada. Se
  asume como target secundario, no como superficie principal.

## Alternativas descartadas

| Alternativa | Motivo del descarte |
|---|---|
| KMP + SwiftUI | Duplica el esfuerzo de UI sin beneficio en un producto de UI declarativa |
| Flutter | Descarta el ecosistema Kotlin y obliga a *bindings* manuales hacia ML Kit, Vision y ZXing |
| React Native | Mismo problema de *bindings*, peor acceso a la cámara en tiempo real |
| Solo Android | Contradice el objetivo del producto de comparar motores **entre plataformas** |
