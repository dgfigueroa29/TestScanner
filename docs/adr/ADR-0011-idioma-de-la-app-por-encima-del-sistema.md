# ADR-0011 — El idioma de la app se fija por encima del sistema, cambiando el locale de la plataforma

- **Estado:** Aceptada
- **Fecha:** 2026-08-21

## Contexto

La app se publica en inglés y español. La mitad fácil es la de siempre: dos catálogos de
`composeResources` por módulo. La decisión que sí tiene fondo son **dos**, y conviene separarlas.

### Cuál de los dos idiomas va sin calificador

Los recursos de Compose resuelven por calificador, y la carpeta **sin** calificador —`values/`— es el
respaldo de **todo** idioma que no tenga catálogo propio. Con los textos originales en `values/`, un
teléfono en alemán, japonés o portugués veía **castellano**: no el idioma que entiende, ni el que la
mayoría de la gente usaría como segunda opción.

### Cómo se ofrece un idioma distinto al del sistema

Un selector propio no es un capricho de completitud. La gente que vive entre dos idiomas tiene el
teléfono en uno y prefiere ciertas apps en el otro, y cambiar el idioma del sistema entero para leer
un código de barras no es una alternativa.

El primer intento fue el que documentan varios ejemplos de Compose Multiplatform: sustituir el
entorno de recursos con `LocalComposeEnvironment`. **No compila con Compose Multiplatform 1.11.1**:

```
Cannot access 'interface ComposeEnvironment : Any': it is internal in file.
Cannot access 'val LocalComposeEnvironment: ProvidableCompositionLocal<ComposeEnvironment>':
    it is internal in file.
```

Tanto la interfaz como su `CompositionLocal` son `internal` a la librería. No hay forma de proveerlos
desde fuera, y el `ResourceEnvironment` tampoco se puede construir a mano porque su constructor
también lo es.

## Decisión

**1. El catálogo sin calificador es el inglés.** Los textos en castellano viven en `values-es/`. Un
dispositivo en español ve español; cualquier otro ve inglés.

**2. El idioma elegido se aplica cambiando el locale de la plataforma y tirando el subárbol de
Compose.** Es el camino por donde la librería sí mira: `stringResource` resuelve el idioma leyendo
`androidx.compose.ui.text.intl.Locale.current`, dentro de un `remember` cuya clave es ese locale. De
ahí salen las dos piezas, y hacen falta las dos:

```kotlin
@Composable
fun ProvideAppLanguage(tag: String?, content: @Composable () -> Unit) {
    ApplyPlatformLanguage(tag)      // expect/actual: cambia el locale por defecto
    key(tag) { content() }          // descarta los `remember` del subárbol entero
}
```

Sin `ApplyPlatformLanguage` no cambia de dónde se lee; sin `key` los `remember` internos de cada
`stringResource` conservan el texto anterior, porque para Compose no ha cambiado ningún estado
observable.

En Android la cadena tiene un eslabón que no se ve y que hace que baste con `Locale.setDefault`:
`Locale.current` sale de `android.os.LocaleList.getDefault()`, y **esa se recalcula sola** cuando
cambia `java.util.Locale.getDefault()` —lo dice su contrato: reordena la lista para dejar arriba el
locale por defecto—. No hace falta tocar la `Configuration`, que además está depreciada.

**3. Donde la plataforma no puede honrarlo, el selector no se ofrece.**
`PlatformSupportsLanguageOverride` lo decide, y es `false` en Web: el idioma sale de
`navigator.language`, que una página no puede escribir. Un control inerte es peor que no tenerlo.

**4. Android declara además `localeConfig`.** Con `res/xml/locales_config.xml` la app aparece en
Ajustes → Aplicaciones → Scanly → Idioma, que es donde mucha gente lo busca, y Play lo usa para su
ficha. Es complementario al selector propio, no un sustituto.

## Consecuencias

**Positivas**
- Un teléfono en cualquier idioma que no sea español ve inglés y no castellano.
- El mecanismo no depende de ninguna API interna de la librería, así que una actualización de Compose
  Multiplatform no puede romperlo por hacer público o privado algo que no le corresponde a esta app.
- La limitación de Web queda expresada en el código —una constante por plataforma— y no en un
  comentario que nadie lee.

**Negativas y su gestión**
- `Locale.setDefault` es **global y destructivo**: una vez cambiado no hay forma de preguntarle al
  proceso cuál era el original. Por eso cada actual guarda el valor del sistema la primera vez que
  pasa por ahí; sin eso, "seguir al sistema" no tendría a dónde volver.
- `key(tag)` recompone el subárbol entero al cambiar de idioma. Es caro, y ocurre una vez cada vez
  que alguien cambia el idioma: exactamente el momento en el que un parpadeo es aceptable.
- **En iOS está sin verificar.** El actual escribe `AppleLanguages` en `NSUserDefaults`, que es el
  mecanismo estándar de la plataforma. Si Compose en iOS lee `NSLocale.preferredLanguages` el cambio
  es inmediato como en Android; si lee `currentLocale`, no lo será hasta reabrir la app. Este
  proyecto compila iOS pero no lo ejecuta —no hay dispositivo—, así que la incógnita queda anotada en
  el código y es lo primero que hay que mirar el día que haya un iPhone delante.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `LocalComposeEnvironment` con un `ResourceEnvironment` propio | **No compila**: la interfaz, su `CompositionLocal` y el constructor del entorno son `internal` en CMP 1.11.1 |
| Español en `values/` y inglés en `values-es`… | Al revés no tiene sentido, pero el original —español sin calificador— hacía que un alemán viera castellano |
| Solo `localeConfig` y el selector del sistema (Android 13+) | Deja fuera a Android 12 y anteriores, y a las otras tres plataformas |
| `AppCompatDelegate.setApplicationLocales` | Obliga a meter `androidx.appcompat` en un proyecto que usa `ComponentActivity`, y no resuelve nada fuera de Android |
| Resolver los textos a mano desde un mapa propio | Tira a la basura el sistema de recursos, sus calificadores y su `Res` generado, para reimplementarlos peor |
