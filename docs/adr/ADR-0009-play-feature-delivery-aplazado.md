# ADR-0009 — Play Feature Delivery se aplaza, con condición de entrada

- **Estado:** Aceptada
- **Fecha:** 2026-07-31

## Contexto

RNF-06 dice que el binario no debe crecer por motores que el usuario no usa. Hoy se cumple **entre
plataformas** y no **dentro** de una: el binario de escritorio no contiene ML Kit y el de iOS no
contiene Play Services, porque cada motor se agrega desde el *source set* que le corresponde. Pero
el APK de Android enlaza los cinco motores de Android, los use quien los use.

El roadmap apuntaba a **Play Feature Delivery** como la solución. Al ir a implementarlo aparecen
tres obstáculos que no son de esfuerzo sino de forma.

**1. Un módulo de característica dinámica no puede ser un módulo KMP.** El plugin
`com.android.dynamic-feature` es del modelo de aplicación de Android; no convive con el plugin de
Kotlin Multiplatform y su `androidTarget()`. Los cuatro motores pesados son módulos KMP: tres solo
con `androidTarget()`, y `:engines:zxing-cpp` además con los tres targets de iOS, así que ese no es
convertible en absoluto sin partirlo en dos. Los otros tres perderían su `commonMain`, que es de
donde toman el SPI.

**2. Rompe el cableado directo.** Hoy `platformModule()` construye cada motor por su nombre. Un
módulo entregado bajo demanda no está en el classpath al arrancar, así que habría que resolverlo por
reflexión o por `ServiceLoader` y manejar el caso "todavía no instalado" como un estado más del
motor. Lo segundo encaja bien —`EngineAvailability` ya modela `RequiresDownload`— pero lo primero
introduce en el proyecto justo lo que el SPI evita: una indirección que ningún test de contrato
puede recorrer.

**3. Solo funciona distribuyendo por Play.** `SplitInstallManager` necesita que la app venga de Play
Store. En una instalación por `adb`, por APK suelto o por cualquier otra tienda, los módulos no se
descargan nunca. Para una app que hoy no se distribuye, el resultado sería un mecanismo que no se
ejercita en ninguno de los escenarios reales del proyecto.

## Decisión

**Aplazar Play Feature Delivery.** No se convierte ningún módulo a característica dinámica en esta
fase. RNF-06 se declara **cumplido entre plataformas y no cumplido dentro de Android**, y así queda
escrito en lugar de darse por hecho.

## Condición de entrada

Se retoma cuando se cumplan **las dos**:

1. La app se distribuye por Play Store — sin eso el mecanismo no se ejecuta nunca.
2. Hay una medición real del APK que muestre cuánto pesa cada motor. Hoy no la hay: el entorno de
   desarrollo no alcanza el maven de Google, así que ni siquiera se pueden mirar los tamaños de los
   artefactos de ML Kit. Optimizar sin medir sería elegir qué partir por corazonada.

Si al medir resulta que el peso se concentra en un solo motor, la primera opción a evaluar no es
Play Feature Delivery sino **sacar ese motor del binario por defecto** —una variante de producto—,
que no exige tienda ni reflexión.

## Consecuencias

**Positivas**
- No se parte la estructura KMP por una optimización que hoy no se puede medir ni ejercitar.
- El incumplimiento queda registrado y acotado, en vez de esconderse tras un requisito marcado como
  hecho.

**Negativas y su gestión**
- El APK de Android carga con los cuatro motores aunque el usuario use uno. Se acepta a sabiendas
  mientras la app no se distribuya: hoy no hay ningún usuario pagando ese coste.
- El día que se retome habrá más motores que mover. Lo mitiga la propia arquitectura: un motor ya
  vive aislado en su módulo y solo conoce el SPI, que es la parte cara de este cambio y ya está
  hecha.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Convertir los motores a `dynamic-feature` ahora | Incompatible con KMP; y sin distribución por Play no se ejercitaría |
| Variantes de producto (con y sin ML Kit) | Es la opción más probable **después** de medir, no antes: hoy no se sabe qué variante haría falta |
| Confiar en que R8 elimine lo que no se usa | No puede: los motores se registran explícitamente en el grafo de Koin, así que son alcanzables por definición |
