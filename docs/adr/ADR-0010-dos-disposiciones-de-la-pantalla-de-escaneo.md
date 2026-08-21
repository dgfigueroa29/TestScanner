# ADR-0010 — Dos disposiciones para la pantalla de escaneo, no una con condicionales

- **Estado:** Aceptada
- **Fecha:** 2026-08-21

## Contexto

Este proyecto nació como **banco de pruebas de motores de escaneo** (§1.1 del SDD) y su pantalla
principal lo reflejaba literalmente: un `LazyColumn` con el visor de cámara como primer elemento,
seguido del catálogo de los ocho motores con su disponibilidad y sus capacidades, el filtro de las
diecisiete simbologías, el interruptor de escaneo continuo, el estado de la sesión con el id del
motor activo, y por fin los resultados con su latencia en milisegundos.

Al decidir publicar en Play Store, esa pantalla deja de ser adecuada, y no por estética:

**1. El visor se va de la pantalla justo cuando hace falta.** Un visor que es el primer elemento de
una lista desplazable desaparece en cuanto llega el segundo resultado y la lista crece. Es el momento
exacto en que el usuario quiere seguir apuntando.

**2. Obliga a una decisión que el usuario no puede tomar.** Elegir entre `mlkit-camerax` y
`zxing-cpp` exige saber qué es un decodificador. Quien abre un lector de códigos quiere leer un
código.

**3. Expone identificadores internos.** "Escaneando con mlkit-camerax" es el `id` de un módulo
Gradle en la cara del usuario.

Pero el banco de pruebas **no es un accidente que haya que borrar**: es el objetivo G5 del proyecto,
tiene su comparador, sus métricas y su ADR-0008. Tirarlo para hacer sitio a un lector convertiría dos
años de arquitectura de motores en código muerto.

## Decisión

La app tiene **dos modos**, gobernados por una preferencia persistida (`AppPreferences.advancedMode`,
apagada por defecto), y la pantalla de escaneo tiene **dos disposiciones distintas** para ellos:

| | Modo básico (por defecto) | Modo avanzado |
|---|---|---|
| Visor | Ocupa todo el alto disponible; **no** se desplaza | Proporción fija 3:4, se desplaza con el resto |
| Resultados | Hoja inferior que empuja el visor hacia arriba | Tarjetas al final de la lista |
| Catálogo de motores | No aparece | Lista completa con disponibilidad y capacidades |
| Filtro de formatos | No aparece | Chips de las diecisiete simbologías |
| Estado de sesión | "Escaneando…" | "Escaneando con mlkit-camerax" |
| Metadatos de lectura | Formato | Formato · motor · latencia |
| Comparador | Fuera de la navegación | Destino propio en la barra |

Lo importante de la decisión no es la lista de diferencias sino **que son dos composables distintos**
(`ScannerLayout` y `WorkbenchLayout`) y no un solo layout con `if (advancedMode)` repartidos.

## Por qué dos composables y no condicionales

Porque las dos pantallas responden a **preguntas distintas**, y esa diferencia es estructural y no
de contenido:

- Leer un código pregunta *"¿qué dice esto que tengo delante?"*. La respuesta quiere el visor
  ocupando todo y el resultado a mano, sin desplazar.
- Comparar motores pregunta *"¿cuál lee mejor en este dispositivo?"*. La respuesta quiere los ocho
  motores y sus métricas a la vista, y el visor es una pieza más entre ellos.

Un solo layout con condicionales tiene que elegir una de las dos jerarquías y disfrazarla de la otra.
El resultado sirve mal para las dos: o el visor está en una lista —y se va— o el catálogo no cabe.

Los componentes que **sí** son comunes se comparten de verdad: el visor y sus estados
(`ViewfinderArea`), la tarjeta de resultado (`DetectionCard`), la entrada manual. Lo que se duplica
es la composición, que es justo lo que difiere.

## Consecuencias

**Positivas**
- La app publicable es un lector de códigos, sin que el banco de pruebas desaparezca ni se degrade.
- Los controles **de cámara** —linterna, zoom, desde imagen, pausa— viven sobre el visor en los dos
  modos, porque son controles del dispositivo y no del banco de pruebas. La separación pasa por
  "¿esto es información de diagnóstico?" y no por "¿esto es un control?".
- `ScannerViewModel` no se entera: el modo llega como parámetro a los composables y el estado es el
  mismo. La decisión es de presentación y se queda ahí.

**Negativas y su gestión**
- Dos disposiciones que mantener. Se acota compartiendo los componentes y dejando en cada layout solo
  el orden y la jerarquía.
- La lista de destinos **cambia en caliente** al apagar el modo avanzado, y el comparador puede estar
  en el backstack. Lo resuelve `Navigator.pruneTo`, que poda el backstack entero y no solo la cima —
  un destino retirado enterrado más abajo haría que el botón atrás volviera a él (§9.3 del SDD).

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Un solo layout con `if (advancedMode)` repartidos | Obliga a elegir una jerarquía y disfrazarla de la otra; el visor acaba dentro de una lista o el catálogo no cabe |
| Borrar el banco de pruebas | Convierte en código muerto el objetivo G5, el comparador, las métricas por motor y ADR-0008 |
| Dos aplicaciones (`:androidApp` y `:workbenchApp`) | Duplica el shell, el icono, la ficha y el mantenimiento para separar lo que un interruptor ya separa |
| Dejar el modo avanzado siempre visible pero al final de la lista | El problema no era el orden sino que el visor viviera dentro de la lista |
