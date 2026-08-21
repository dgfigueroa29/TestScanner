# ADR-0003 — Koin como contenedor de DI

- **Estado:** Aceptada
- **Fecha:** 2026-07-30

## Contexto

El estándar del equipo para Android es **Hilt**. La migración a KMP obliga a revisarlo: Hilt
genera código a partir del modelo de componentes de Android (`Application`, `Activity`,
`ViewModel` de AndroidX) mediante procesamiento de anotaciones sobre clases Java/Android. **No
existe para iOS, Desktop ni Web.**

## Decisión

Usar **Koin 4.x** como contenedor de DI en todos los módulos multiplataforma.

## Justificación

- Es la opción con soporte KMP maduro y adopción real, incluida integración con Compose
  Multiplatform y con el `ViewModel` multiplataforma de AndroidX.
- Los módulos se declaran en `commonMain` y se completan por plataforma con un
  `expect val platformModule: Module` — encaja exactamente con el patrón que ya usamos para los
  motores.
- No requiere procesamiento de anotaciones → no penaliza los tiempos de build, que ya son el
  riesgo R4 del proyecto.

## Consecuencias

**Positivas**
- Un único grafo de dependencias para las cuatro plataformas.
- Sustituir implementaciones en tests es trivial (`loadKoinModules` con dobles).

**Negativas y su gestión**
- **Koin resuelve en tiempo de ejecución**, no en compilación: una dependencia faltante es un
  crash, no un error de compilación. Es la desventaja real frente a Hilt y se mitigaría con:
  - un test que ejecute `verify()` sobre cada módulo y falle en CI si falta cualquier binding
    — **nunca se implementó**, ver la revisión de abajo;
  - **constructor injection obligatoria** — prohibido `by inject()` dentro de clases de dominio o
    datos; solo se permite en el punto de entrada de la UI. Esta sí se cumple.
- El equipo debe aprender Koin. Coste bajo: la superficie de API que usamos es pequeña
  (`module {}`, `single`, `factory`, `viewModelOf`).

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Hilt | No es multiplataforma. Punto final |
| kotlin-inject / kotlin-inject-anvil | Verificación en compilación, pero KSP en todos los targets encarece el build y la comunidad es menor |
| Inyección manual (composition root a mano) | Viable al inicio, insostenible al crecer el grafo con 8 motores y varias features |
| Metro / Dagger KMP | Demasiado recientes para apostar las fundaciones del proyecto |

## Nota de revisión

Si en la Fase 5 el grafo crece hasta hacer doloroso el diagnóstico de fallos en runtime, se
reevaluará **kotlin-inject**. La abstracción está contenida: los módulos de Koin viven en el borde
de cada capa y ninguna clase de dominio o datos conoce a Koin.

## Revisión — la mitigación que estaba escrita y no existía

**La decisión se mantiene, pero esta ADR describía una salvaguarda que nunca se implementó**, y el
riesgo que decía cubrir se materializó exactamente como estaba anunciado.

El primer arranque de la app en un dispositivo real murió con
`NoDefinitionFoundException: No definition found for type 'java.util.concurrent.Executor'`. La causa:
`platformModule` de Android declaraba `single<ExecutorService> { … }` mientras los tres motores de
cámara piden un `Executor` en su constructor. **Koin indexa cada definición por el tipo con el que se
declara y resuelve por igualdad exacta: no recorre supertipos.** `ExecutorService` es un `Executor`
para el compilador y no para el contenedor.

Tres cosas que conviene separar:

1. **El riesgo estaba bien identificado.** "Una dependencia faltante es un crash, no un error de
   compilación" es literalmente lo que pasó, y estaba escrito aquí desde el primer día.
2. **La mitigación estaba escrita como si existiera.** No existía: no hay ningún test que monte el
   grafo. Una salvaguarda documentada y no implementada es peor que no tenerla, porque quien lee la
   ADR deja de buscar el agujero.
3. **El fallo concreto es más sutil que "falta un binding".** No faltaba: estaba, con otro tipo. Un
   `checkModules()` mental o una lectura del módulo no lo ven — hay que cruzar el tipo *declarado*
   con el tipo del *parámetro del constructor*, que son dos archivos distintos en dos módulos
   distintos.

Queda como deuda **D18** con la forma que sí funciona: `verify()` de `koin-test`, que recorre por
reflexión los constructores de cada definición y comprueba que cada parámetro tenga quien lo
satisfaga, **sin instanciar nada**. Al no instanciar, no necesita `Context` real ni emulador, así que
corre como test JVM y no choca con la decisión de no tener tests instrumentados (D6).

La regla de estilo que se deriva y aplica desde ya: **declarar el tipo que se consume, no el que
devuelve la fábrica.**
