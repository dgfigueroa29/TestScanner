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
  crash, no un error de compilación. Es la desventaja real frente a Hilt y se mitiga con:
  - un test en `commonTest` que ejecuta `checkModules()` sobre el grafo completo y falla en CI si
    falta cualquier binding;
  - **constructor injection obligatoria** — prohibido `by inject()` dentro de clases de dominio o
    datos; solo se permite en el punto de entrada de la UI.
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
