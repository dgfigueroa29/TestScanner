# ADR-0005 — Navegación propia mínima en la Fase 1

- **Estado:** Aceptada — revisada en la Fase 3, se mantiene
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

El grafo pasó de uno a **tres destinos**: escanear, comparar e historial. La decisión se mantiene:
tres destinos con una barra inferior no justifican una dependencia, y el `Navigator` sigue siendo una
clase de 30 líneas testeable sin Compose. Android ya le cede el botón atrás del sistema.

El umbral de la revisión no cambia: **seis destinos o la primera necesidad de deep links** obligan a
migrar a `navigation-compose` multiplataforma, reimplementando `Navigator` sin tocar pantallas.

## Revisión (Fase 3) — deuda D4 saldada

Se ejecutó la revisión programada. El grafo sigue en **tres destinos** y no ha aparecido ninguna
necesidad de deep links, así que **el umbral no se alcanza y la navegación propia se mantiene**.

Lo que sí resultó ser un defecto real es la otra mitad de la deuda: **no había restauración de
estado**. Al recrearse la Activity, el usuario volvía a la pantalla de escaneo desde donde estuviera,
porque el backstack vivía solo en memoria. Conviene ser precisos sobre la causa: esto no era
consecuencia de tener navegación propia — `navigation-compose` tampoco guarda el backstack solo—
sino de no haberlo guardado nunca.

Y conviene ser igual de precisos sobre **cuándo** pasaba, porque no es el caso que uno esperaría.
`MainActivity` declara `configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"`,
así que **girar el teléfono no la recrea** — es deliberado, para no reiniciar la cámara al rotar. Lo
que sí la recrea es que el sistema mate el proceso mientras la app está en segundo plano, y los
cambios de configuración que la Activity no declara: el tamaño de letra o el idioma. Son menos
frecuentes que una rotación y por eso el defecto no saltaba a la vista, pero pierden más: el usuario
vuelve a una app que ha olvidado dónde estaba.

Se resolvió sin agregar dependencias:

- `Destination` expone un `id` estable **escrito a mano**. No se deriva de `::class.simpleName`
  porque R8 lo ofusca, y restaurar dejaría de encontrar el destino justo en release (el mismo error
  que ya se corrigió en `BarcodeValueType`).
- `Navigator.saveState()` devuelve el backstack como `List<String>` y `restoreState(ids)` lo
  reconstruye ignorando lo que no reconozca; si no queda nada utilizable deja el backstack intacto,
  para que una app actualizada sobre estado viejo no arranque vacía.
- `MainActivity` lo guarda en `onSaveInstanceState` y lo restaura en `onCreate`. Viaja como ids y no
  como objetos: `Destination` no necesita ser `Parcelable`, y así el estado guardado no queda atado
  a la representación interna. Es el mecanismo que sobrevive a la muerte del proceso, que es
  justamente el caso que quedaba sin cubrir.

Escritorio y Web no participan: no tienen recreación de Activity. En Web recargar la página reinicia
la navegación, lo cual es el comportamiento esperado de una recarga y no está en la deuda.

El umbral de migración sigue igual: **seis destinos o la primera necesidad de deep links**.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `navigation-compose` multiplataforma | API aún en movimiento; se adoptará en Fase 3, cuando el grafo lo justifique |
| Voyager | Buena API, pero impone su propio modelo de ScreenModel que compite con nuestro MVI |
| Decompose | Potente y correcto, pero su modelo de componentes es una decisión arquitectónica mayor que no queremos tomar antes de tener el SPI validado |
