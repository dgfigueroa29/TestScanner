# ADR-0006 — Reestructurar el build de una sola vez

- **Estado:** Aceptada
- **Fecha:** 2026-07-30

## Contexto

El repositorio de partida tenía: módulo Android único, Groovy DSL, AGP 8.0.2, Kotlin 1.8.10,
`compileSdk 34`, Java 8, sin version catalog, y `MainActivity` con un `Greeting("Android")` de
plantilla.

La opción conservadora en una migración es **incremental**: añadir módulos KMP junto al `:app`
existente y migrar pantalla por pantalla, manteniendo la app funcionando en todo momento.

## Decisión

**Reestructurar el build completo en un solo paso**, eliminando `app/` y sustituyéndolo por
`:androidApp` + `:composeApp` + módulos `core`/`engines`/`feature`.

## Justificación

- **No hay funcionalidad que preservar.** La app no escanea nada: la dependencia
  `play-services-code-scanner` está declarada pero **ningún código la usa**. No existe lógica de
  negocio, ni persistencia, ni tests reales. Una migración incremental protegería un activo
  inexistente.
- **El toolchain es incompatible, no obsoleto.** Kotlin 1.8.10 y AGP 8.0.2 no soportan Compose
  Multiplatform ni el target Wasm. La migración del toolchain no es opcional ni gradual: hay que
  saltar a Kotlin 2.x, y ese salto invalida el `compose_version` fijado a mano, el Java 8 y el
  Groovy DSL de todas formas.
- **La convivencia tiene un coste real.** Mantener `:app` y `:composeApp` en paralelo obliga a
  duplicar tema, manifiesto, iconos y punto de entrada, y a razonar sobre dos grafos de
  dependencias durante semanas — a cambio de proteger un `Text("Hello Android!")`.
- **El historial de git es la red de seguridad.** El estado previo queda en `main` y en los
  commits anteriores; revertir es un `git revert`, no una reconstrucción.

## Consecuencias

**Positivas**
- Un solo modelo mental desde el primer día; nadie escribe código en el módulo condenado.
- El version catalog y el Kotlin DSL entran limpios, sin período de coexistencia con Groovy.

**Negativas y su gestión**
- **Hay un intervalo en el que la app no compila** hasta que la Fase 1 cierra. Mitigado
  entregando la Fase 1 completa y verificable como una unidad, y manteniendo `main` intacto hasta
  que la rama de migración pase CI.
- Se pierde el scaffolding de tema e iconos generado por Android Studio. Coste real: minutos.
- Los `:engines:*` de las Fases 2–4 no existen aún, así que la app de la Fase 1 solo escanea por
  entrada manual. Es una app incompleta, no una app rota: el catálogo declara explícitamente qué
  motores están `NotImplemented` y en qué fase llegan.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Migración incremental con `:app` en paralelo | Protege un activo que no existe, a cambio de coste real de coexistencia |
| Solo documentar y no tocar el build | Deja el SDD sin verificación; las decisiones de estructura no se validan hasta que se escriben |
| Empezar un repositorio nuevo | Pierde el historial y la trazabilidad de por qué se migró |
