# ADR-0005 — Navegación propia mínima en la Fase 1

- **Estado:** Aceptada — con revisión programada en la Fase 3
- **Fecha:** 2026-07-30

## Contexto

Compose Multiplatform no trae navegación en su núcleo. Las opciones son
`org.jetbrains.androidx.navigation:navigation-compose` (el port multiplataforma de Jetpack
Navigation, con releases que aún se mueven entre alpha y beta), librerías de terceros como Voyager
o Decompose, o una implementación propia.

En la Fase 1 el grafo tiene **cuatro destinos** (`Scanner`, `EngineCatalog`, `Result`, `History`) y
ninguna necesidad de deep links, animaciones compartidas ni grafos anidados.

## Decisión

Implementar un navegador propio mínimo en `:composeApp`: `sealed interface Destination` más un
backstack en un `StateFlow`, con manejo del botón atrás por plataforma.

## Justificación

- **No pagamos complejidad por adelantado.** Cuatro destinos sin deep links no justifican una
  dependencia con API en movimiento.
- **No atamos las fundaciones a un ciclo de releases ajeno.** Un cambio incompatible en una alpha
  bloquearía la fase en la que se está construyendo todo lo demás.
- **Es reversible por diseño.** El backstack vive detrás de una interfaz `Navigator` en un único
  archivo; migrar significa reimplementar esa interfaz, no reescribir pantallas.

## Consecuencias

**Positivas**
- Cero dependencias, cero sorpresas de versión, comportamiento totalmente bajo nuestro control.
- La navegación es testeable como lógica pura.

**Negativas y su gestión**
- No hay deep links, restauración de estado ni transiciones estándar. Aceptable en Fase 1; ninguno
  es requisito hasta la Fase 3.
- Riesgo real de que el navegador propio crezca por acumulación hasta convertirse en una librería
  mediocre. **Mitigación explícita: revisión obligatoria en la Fase 3.** Si para entonces hay más
  de seis destinos o aparece la necesidad de deep links, se migra a `navigation-compose`
  multiplataforma sin discusión.

## Revisión (Fase 2)

Con la llegada del historial el grafo pasó de uno a **dos destinos**. La decisión se mantiene: dos
destinos con una barra inferior no justifican una dependencia, y el `Navigator` sigue siendo una
clase de 30 líneas testeable sin Compose. Android ya le cede el botón atrás del sistema.

El umbral de la revisión no cambia: **seis destinos o la primera necesidad de deep links** obligan a
migrar a `navigation-compose` multiplataforma, reimplementando `Navigator` sin tocar pantallas.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `navigation-compose` multiplataforma | API aún en movimiento; se adoptará en Fase 3, cuando el grafo lo justifique |
| Voyager | Buena API, pero impone su propio modelo de ScreenModel que compite con nuestro MVI |
| Decompose | Potente y correcto, pero su modelo de componentes es una decisión arquitectónica mayor que no queremos tomar antes de tener el SPI validado |
