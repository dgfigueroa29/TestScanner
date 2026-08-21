# Scanly

Lector de **códigos de barras y QR** en Compose Multiplatform, sin cuenta, sin rastreo y sin red.

Debajo hay un **banco de pruebas de motores de escaneo**: la app no lee un código de una sola
manera, sino que elige entre varias alternativas, las compara y degrada con elegancia cuando una no
está disponible — sobre Android, iOS, Desktop y Web con un único código base.

Las dos cosas conviven porque son la misma app en dos modos. Por defecto Scanly es un lector: se
apunta y se lee. El **modo avanzado** (Ajustes → Avanzado) devuelve el catálogo de los ocho motores,
el comparador en paralelo y las latencias por lectura.

> **Sobre los nombres.** El producto se llama Scanly y el `applicationId` de Play es
> `com.scanly.app`. El repositorio, los paquetes de Kotlin y los módulos siguen siendo
> `com.testscanner.*` **a propósito**: renombrarlos tocaría doscientos archivos para cambiar algo que
> ningún usuario ve, y el `applicationId` —que sí es la identidad pública y permanente en Play— ya
> está donde tiene que estar.

---

## Estado actual — arranca en Android; compila en las cuatro plataformas

| | |
|---|---|
| Arquitectura y SPI de motores | ✅ completos |
| Catálogo de los 8 motores con capacidades | ✅ declarado |
| Selección automática + cadena de fallback | ✅ implementados y testeados |
| Suite de contrato que todo motor debe pasar | ✅ implementada, y aplicada a los decoradores y a la cadena completa |
| Comparador de motores con marcador en vivo (G5) | ✅ implementado y en la UI |
| Motor de entrada manual | ✅ funcional en las 4 plataformas |
| Google Code Scanner y ML Kit + CameraX (Android) | ✅ implementados y compilando |
| Historial persistente | ✅ Room en Android, iOS y Desktop; en Web, JSON en el almacén del navegador. **El driver bundled no se aplicaba** hasta esta versión: ver más abajo |
| Preferencias persistentes | ✅ las cuatro plataformas |
| CI en GitHub Actions | ✅ **en verde**: detekt, tests, Android (con R8), Desktop y Web |
| Vision / AVFoundation (iOS) | ✅ implementado; **todo el Kotlin de iOS enlaza**, a demanda en el workflow `iOS (manual)`. Falta el dispositivo, no la compilación |
| Arranque en un dispositivo real | ✅ **primera vez en agosto de 2026**, y encontró un defecto de DI que el CI no podía ver (D18) |
| `targetSdk` 36 (requisito de Play) | ✅ con el atrás adaptado al *predictive back* |
| BarcodeDetector del navegador (Web) | ✅ implementado, con visor sobre el canvas |
| OCR con ML Kit Text Recognition (Android) | ✅ implementado; en iOS irá con Vision, no con ML Kit |
| Escaneo desde imagen (RF-07) | ✅ selector en las cuatro plataformas, sin pedir permisos |
| Exportación del historial | ✅ CSV y JSON, guardado en las cuatro plataformas |
| ZXing-cpp (Android + iOS) | ✅ implementado — el mismo decodificador C++ en ambas, que es lo que hace comparables las lecturas |
| Acciones sobre el resultado (RF-13) | ✅ copiar, compartir y abrir, según el significado del código |
| Navegación | ✅ propia, con backstack que sobrevive a que el sistema mate el proceso |
| Build de release con R8 | ✅ `minify` y `shrinkResources`, con `assembleRelease` en CI |
| Marca, icono y tema | ✅ Scanly: icono adaptativo con capa monocroma, paleta con los ~30 roles de Material 3, escala tipográfica y de formas propias |
| Selector de tema claro/oscuro | ✅ Sistema / Claro / Oscuro, persistido, con las barras del sistema siguiendo al tema **de la app** |
| Idiomas inglés y español | ✅ los cuatro catálogos en `values/` (inglés, respaldo de cualquier idioma) y `values-es/`, con selector propio ([ADR-0011](docs/adr/ADR-0011-idioma-de-la-app-por-encima-del-sistema.md)) y `localeConfig` para el selector por app de Android 13+ |
| Pantalla de escaneo | ✅ cámara a pantalla completa con el resultado en una hoja que la empuja, no que la tapa; la sesión arranca sola y se apaga al salir ([ADR-0010](docs/adr/ADR-0010-dos-disposiciones-de-la-pantalla-de-escaneo.md)) |
| Lecturas repetidas | ✅ suprimidas en el dominio con ventana de dos segundos. Antes, tres segundos apuntando a un QR escribían noventa filas en el historial |
| Que el grafo de Koin resuelva | ✅ `KoinGraphTest` en los módulos comunes y en escritorio — cierra media D18. Falta el `platformModule` de Android |
| Accesibilidad (RNF-05) | ✅ contraste AA **verificado por test** (56 pares, los dos temas), y semántica para lectores de pantalla |
| Privacidad (RNF-03) | ✅ auditada: sin trazas, sin cliente HTTP, sin analítica y sin permiso `INTERNET` |
| ZXing en Java (Desktop) | ✅ el único decodificador de escritorio; **verificado de verdad**, decodificando imágenes generadas en el test |

El catálogo muestra las ocho alternativas con su estado real; los motores aún no implementados se
declaran como tales, con la fase en la que llegan. Ver `docs/ROADMAP.md`.

Lo que queda fuera por ahora, y por qué:

- **iOS está despriorizado**, no abandonado. Probarlo exige un dispositivo, que no lo hay; lo que sí
  se puede es **compilarlo**, y para eso está el workflow `iOS (manual)` — Actions → Run workflow.
  Está fuera de `Verify` a propósito: compilar no es probar, y un check que nadie puede satisfacer
  con una prueba real solo servía para dejar `main` en rojo de forma permanente. Ese trabajo **ya
  está terminado**: los errores estuvieron concentrados en los dos motores de AVFoundation y en el
  `import kotlinx.coroutines.IO` que en Kotlin/Native no viaja con el receptor, y con eso el
  framework entero enlaza. Falta el `iosApp.xcodeproj`, que solo se crea desde Xcode, y un iPhone.
- **No hay tests instrumentados y no los va a haber.** Sin emulador en CI, un test que exija
  dispositivo nunca se ejecuta y da una falsa sensación de red. El ROADMAP dice exactamente qué queda
  cubierto sin dispositivo y qué no.
- **Escritorio lee archivos pero no cámara**: hay decodificador (ZXing en Java) y no hay captura de
  webcam, así que una sesión en vivo cae a la entrada manual.
- **El APK de Android carga con los cuatro motores de la plataforma.** RNF-06 se cumple entre
  plataformas y no dentro de Android; Play Feature Delivery se aplazó a conciencia y con condición
  de entrada ([ADR-0009](docs/adr/ADR-0009-play-feature-delivery-aplazado.md)).
- **Web no tiene respaldo tras el navegador**: zxing-cpp no publica artefacto wasmJs, así que quien
  cierra esa cadena es la entrada manual.

> **Verificado en CI.** El proyecto compila entero: Android (debug, lint y release con R8),
> Escritorio y Web, más detekt y los tests en cada PR. iOS se enlaza a demanda, en el workflow
> `iOS (manual)`.
>
> Lo que el CI **no** comprueba es que la app arranque: sin tests instrumentados nadie ejecuta la
> `MainActivity`, así que un fallo de arranque no lo detecta ningún check. No es teórico — el primer
> arranque en un dispositivo real murió por un `Executor` registrado en Koin con el tipo equivocado,
> con el CI en verde todo el tiempo. Está contado en `docs/adr/ADR-0003`.
>
> **Parte de ese hueco ya está tapado, y hay que decir cuál.** `KoinGraphTest` arranca el grafo real
> y resuelve cada tipo que la raíz de la app consume; corre en un test JVM normal, sin emulador. En
> su primera ejecución encontró un defecto que llevaba meses en producción y que ningún check veía:
>
> - **La base de datos nunca recibía su driver.** `:core:database` declaraba una *extensión*
>   `build()` sobre `RoomDatabase.Builder` para configurar el driver bundled, y en Kotlin **un
>   miembro siempre gana a una extensión**: los tres `platformModule` llamaban al `build()` de Room y
>   esa configuración no se ejecutó nunca. Escritorio e iOS reventaban al abrir la primera pantalla;
>   Android funcionaba cayendo al SQLite del framework — justo el driver que ese código existe para
>   evitar, así que la garantía de "la misma versión de SQLite en las cuatro plataformas" llevaba
>   siendo falsa desde que se escribió.
> - **El compilador lo avisaba en cada build** (`This extension is shadowed by a member`) y nadie
>   leía el aviso. Queda registrado como deuda D19: o se limpian todos los avisos, o se acepta el
>   ruido explícitamente.
> - Encontrarlo exigió antes arreglar otra cosa: **un test que fallaba en CI no decía por qué**. La
>   salida por defecto de Gradle daba el tipo de excepción y la línea, sin mensaje ni causa. El
>   `build.gradle.kts` raíz configura ahora `testLogging` con `exceptionFormat = FULL`.
>
> Lo que sigue sin cubrir: el `platformModule` de **Android** —que es donde estaba el defecto
> original de D18 y necesita `androidUnitTest`— y que la app se abra y lea un código, que sigue
> necesitando un dispositivo.
>
> Hasta que se activó Actions nada de esto se había compilado nunca —el entorno de desarrollo no
> alcanza el maven de Google—, y el primer CI encontró **doce fallos encadenados**, desde el
> `build-logic` que no resolvía sus plugins hasta un `ScanError` construido sin argumentos en el
> motor de Web. Están todos arreglados y cada uno explicado en su commit.

---

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/SDD.md`](docs/SDD.md) | Documento de diseño: requisitos, arquitectura, SPI, calidad, plan de migración |
| [`docs/ENGINES.md`](docs/ENGINES.md) | Catálogo de motores: formatos, capacidades y prioridad por plataforma |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fases, criterios de salida y deuda técnica aceptada |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura con su contexto y sus consecuencias |

Lectura mínima para tocar código: **§7 del SDD** (el Scanner Engine SPI) y **ADR-0002**.

---

## Estructura

```
core/model          modelo puro: Barcode, BarcodeFormat, Detection, ScanRequest
core/scanner-api    el SPI + el catálogo declarativo de motores
core/scanner-ui     capacidad de UI del motor: CameraPreviewEngine
core/scanner-testing suite de contrato que todo motor hereda
core/domain         casos de uso, políticas de selección y decoradores del SPI
core/data           registro de motores, preferencias e historial
core/designsystem   tema, paleta, tipografía, formas, marca y cambio de idioma en caliente
core/permissions    abstracción de permisos por plataforma
core/platform       acciones del sistema: copiar, compartir, abrir, elegir imagen, guardar archivo
core/database       Room KMP: historial persistente (sin target wasmJs)
engines/*           un módulo por alternativa de escaneo
feature/scanner     MVI, pantalla de escaneo y comparador de motores
feature/history     historial filtrable por motor
feature/settings    tema, idioma y modo avanzado
composeApp          raíz Compose Multiplatform y composition root de la DI
androidApp          shell de Android
iosApp              shell de iOS (Xcode)
playstore/          material de la ficha de Play (icono 512×512)
```

La regla de dependencias es estricta: un módulo `engines/*` depende solo de `:core:scanner-api`
y de su SDK nativo. Nunca de `:feature:*`, ni de `:core:data`, ni de otro motor.

---

## Marca, tema e idiomas

**El tema.** `ScanlyTheme` declara los ~30 roles de color de Material 3, y no solo los seis
habituales. No es exhaustividad por gusto: `lightColorScheme()` rellena con su paleta de fábrica todo
lo que no se le pase, así que un `FilterChip` seleccionado o el indicador del ítem activo de la barra
salían **morados** en una app cuya marca es azul. `ContrastTest` mide 50 pares de color a 4.5:1 y 6
más a 3.0:1, sobre los dos esquemas, con aritmética de WCAG en `commonTest`: sin dispositivo y sin
renderizar nada.

**El selector claro/oscuro** vive en Ajustes y persiste. En Android hay una segunda mitad que no
pinta Compose: los iconos de las barras del sistema. Con `enableEdgeToEdge()` a secas siguen al modo
oscuro *del sistema*, y en cuanto el usuario elige un tema distinto dejan de coincidir — teléfono en
claro y app en oscuro daba iconos oscuros sobre fondo oscuro. `MainActivity` recibe el valor ya
resuelto y reajusta el estilo de las barras.

**Los idiomas.** Los cuatro catálogos de textos viven en `values/` (inglés) y `values-es/`. El
inglés está en la carpeta **sin calificador** a propósito: es el respaldo de cualquier idioma que no
sea español, así que un teléfono en alemán ve inglés y no castellano. El selector propio va por
encima del idioma del sistema cambiando el locale de la plataforma y tirando el subárbol de
Compose con `key(tag)`, y `androidApp` declara `localeConfig` para que Scanly aparezca además en el
selector de idioma por app de Android 13+.

Ese mecanismo es el segundo intento. El primero sustituía el entorno de recursos con
`LocalComposeEnvironment`, que es lo que documentan varios ejemplos y **no compila con Compose
Multiplatform 1.11.1**: esa interfaz y su `CompositionLocal` son `internal` a la librería. Lo dice
`AppLanguage.kt` con el error exacto al lado, para que nadie lo vuelva a intentar.

En Web el selector **no se muestra**: el idioma sale de `navigator.language`, que una página no
puede escribir. Preferimos no ofrecer el control a ofrecerlo roto —
`PlatformSupportsLanguageOverride` es lo que lo decide, y es `false` solo ahí.

**El icono** se dibuja dos veces, y las dos copias lo dicen: `ScanlyMark` como `ImageVector` para la
UI y `ic_launcher_foreground.xml` para el lanzador, con las mismas coordenadas escaladas. Lleva capa
`monochrome`, así que se tiñe con los iconos temáticos de Android 13+, y hay PNG de respaldo para
API 24 y 25, que no entienden iconos adaptativos. Antes de esto **no había icono en absoluto**: el
manifiesto no declaraba `android:icon` y Android ponía su robot por defecto.

El razonamiento completo del idioma está en
[ADR-0011](docs/adr/ADR-0011-idioma-de-la-app-por-encima-del-sistema.md).

---

## La pantalla de escaneo

Hay **dos disposiciones**, no una con condicionales, y el motivo está en
[ADR-0010](docs/adr/ADR-0010-dos-disposiciones-de-la-pantalla-de-escaneo.md): leer un código y
comparar motores son preguntas distintas y quieren jerarquías distintas.

En el modo por defecto el visor ocupa todo el alto y los resultados llegan en una hoja que **lo
empuja hacia arriba en lugar de taparlo** — por eso no es un `ModalBottomSheet`: quien escanea en
serie mira el resultado y apunta al siguiente sin tocar la pantalla. Antes el visor era el primer
elemento de un `LazyColumn` y se iba de la pantalla en cuanto llegaba el segundo resultado.

**La sesión arranca sola** al aparecer la pantalla y se apaga al salir. Lo segundo no es una
optimización: el ViewModel sobrevive a la navegación, así que la cámara seguía capturando mientras el
usuario miraba el historial. El arranque automático **no** dispara la petición de permiso — pedirlo
sin que el usuario haya tocado nada es la forma más rápida de que lo deniegue para siempre; en su
lugar la pantalla explica para qué se usa la cámara y ofrece el botón.

**Las lecturas repetidas se suprimen en el dominio**, no en la UI, y eso importa: a treinta frames
por segundo, tres segundos apuntando a un QR emitían noventa lecturas idénticas que se escribían
**una a una en el historial persistente**. No era ruido visual sino corrupción de los datos del
usuario. La regla es una ventana de dos segundos y no "una vez por sesión", porque volver a leer el
mismo código es un caso de uso real — contar unidades iguales en un inventario.

---

## Cómo construir

```bash
./gradlew :androidApp:assembleDebug                  # Android
./gradlew :composeApp:run                            # Desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun    # Web
./gradlew detekt                                     # análisis estático
./gradlew check                                      # tests + detekt
```

iOS se construye desde `iosApp/` en Xcode (requiere macOS).

---

## Añadir un motor de escaneo

El coste es constante: **un módulo y una entrada en el catálogo**. Ni la UI ni el dominio cambian.
Los ocho pasos están en [`docs/ENGINES.md`](docs/ENGINES.md#cómo-añadir-un-motor).
