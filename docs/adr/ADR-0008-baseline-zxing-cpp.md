# ADR-0008 — El baseline de comparación es zxing-cpp desde artefactos publicados, en Android e iOS

- **Estado:** Aceptada
- **Fecha:** 2026-07-31
- **Resuelve:** riesgo R9 del SDD §15
- **Corrige:** la fila `ZXING_CPP` de `docs/ENGINES.md`, que declaraba Desktop como plataforma soportada

## Contexto

El SDD plantea ZXing-cpp como el **baseline de comparación justa**: el mismo decodificador
ejecutándose en varias plataformas, para poder separar "este motor lee mejor" de "este teléfono
tiene mejor cámara". Sin un motor común, comparar ML Kit en Android contra Vision en iOS mezcla dos
variables y G5 deja de ser medible.

El riesgo R9 se registró al no encontrar ningún binding KMP publicado. Esa afirmación era
**incompleta**. Inventario real de `repo1.maven.org`, comprobado el 2026-07-31:

| Artefacto | Última versión | Targets | API |
|---|---|---|---|
| `io.github.zxing-cpp:android` | 3.1.1 | Android (arm64-v8a, armeabi-v7a, x86, x86_64) | `zxingcpp.BarcodeReader` con `Options`/`Result` anidados; `read(ImageProxy)` y `read(Bitmap, Rect, Int)` |
| `io.github.zxing-cpp:kotlin-native` | 3.1.1 | iOS arm64/x64/simulatorArm64, macOS, tvOS, watchOS, linux, androidNative | paquete `zxingcpp`: `BarcodeReader`, `Barcode`, `BarcodeFormat`, `ImageView`; el cinterop **ya viene hecho y publicado** |
| JVM / Desktop | — | **no existe publicación** | — |
| wasmJs | — | **no existe publicación** | — |

Es decir: el baseline sí existe justamente en las dos plataformas donde hay algo que comparar.
Desktop hoy no tiene **ningún** motor de cámara —solo entrada manual—, así que ahí no hay
comparación que falsear.

## Decisión

**Consumir los artefactos publicados. No se hace cinterop propio sobre zxing-cpp.**

`:engines:zxing-cpp` se implementa con dos `actual`:

- **Android** → `io.github.zxing-cpp:android`
- **iOS** → `io.github.zxing-cpp:kotlin-native`

Las dos superficies **no comparten forma** (una anida `Options`/`Result` dentro de `BarcodeReader`,
la otra los expone como tipos de primer nivel), así que el módulo no puede ser una implementación
única en `commonMain`: son dos adaptadores contra el mismo núcleo C++. Lo que importa para G5 es que
el **decodificador** sea el mismo, no que el binding lo sea.

**Desktop y Web quedan fuera del baseline, explícitamente.** No es una omisión pendiente: no hay
artefacto, y forzarlo con otro decodificador rompería la premisa. Cuando Desktop necesite decodificar
—que será con RF-07, escaneo desde imagen, en la Fase 4— el candidato es `com.google.zxing:core`, y
entrará al catálogo **como un motor distinto**, con su propio `ScannerEngineId`. Un decodificador
distinto con el nombre de otro convierte la tabla comparativa en ruido.

### Coste: hay que subir Kotlin

Los klibs publicados de `kotlin-native` están compilados con **Kotlin 2.2.0** (`abi_version=2.2.0`,
verificado en el manifiesto de las cuatro versiones publicadas: 3.0.0, 3.0.2, 3.1.0 y 3.1.1). El
proyecto está en **2.1.21**, y un compilador no puede leer un klib producido por uno más nuevo.

Subir a Kotlin ≥ 2.2 es, por tanto, **prerrequisito de la Fase 3** y arrastra KSP y Compose
Multiplatform. No es un coste que imponga esta decisión por sí sola: el ecosistema ya va por Kotlin
2.3.21, CMP 1.11.1 y KSP 2.3.x, así que el pin actual estaba viejo de antes. La decisión solo lo
convierte en bloqueante.

## Alternativas descartadas

**cinterop propio sobre zxing-cpp.** Da las tres plataformas y control total de la versión del
núcleo C++. Se descarta porque duplica trabajo ya publicado y mantenido por el propio proyecto
zxing-cpp, y porque exige toolchain nativo en CI para compilar el `.def` y las librerías estáticas
de cada target. El único beneficio real sería Desktop, que es donde menos falta hace.

**`com.google.zxing:core` como baseline.** Es Java puro, cubre Android y Desktop, y no necesita
toolchain nativo. Se descarta **como baseline** porque no cubre iOS —que es exactamente la mitad de
la comparación que interesa— y porque es un decodificador distinto de zxing-cpp: llamarlos igual
mezclaría las dos variables que G5 quiere separar. Sigue siendo el candidato para Desktop, con
identidad propia.

**Renunciar al baseline portable.** Dejaría G5 sin control: cualquier diferencia entre plataformas
sería inatribuible. Se descarta mientras exista un artefacto que lo evita.

## Consecuencias

**Positivas**
- El baseline existe en Android e iOS sin escribir ni una línea de código nativo.
- `zxing-cpp` 3.1.1 trae simbologías que ni ML Kit ni Vision leen (DataBar, DX Film Edge, Code 39
  extendido), así que el motor no solo sirve de control: amplía la cobertura real de G3.
- El artefacto de Android acepta `ImageProxy` de CameraX directamente, que es lo que ya produce el
  pipeline de `:engines:mlkit-camerax`.

**Negativas y su gestión**
- **Dos adaptadores, no uno.** Se asume: el SPI ya está diseñado para eso, y el contrato común lo
  verifica `BarcodeScannerEngineContractTest` en ambas plataformas.
- **Subir Kotlin sin poder compilar aún.** El bump se hace como primer paso de la Fase 3 y con CI
  disponible, no a ciegas junto al motor.
- **El artefacto de Android arrastra `androidx.camera:camera-core`.** Ya está en el grafo por
  `:engines:mlkit-camerax`; hay que alinear la versión en el catálogo, no añadir una nueva.
- **Desktop y Web sin baseline.** Registrado como deuda D13, no como olvido.
