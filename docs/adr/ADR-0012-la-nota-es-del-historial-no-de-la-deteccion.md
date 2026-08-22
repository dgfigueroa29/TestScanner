# ADR-0012 — La nota del usuario es del historial, no de la detección

- **Estado:** Aceptada
- **Fecha:** 2026-08-22

## Contexto

Al usar la app apareció una carencia que ninguna especificación había previsto: **un código leído no
dice para qué es.** `7501234567893` es exacto, verificable y completamente inútil dentro de una lista
de doscientas filas cuando lo que uno recuerda es "el del pedido de marzo". El historial guardaba el
dato y perdía el significado.

La petición —dejar asociar una nota o texto de referencia a una lectura— parece un campo más. La
decisión de fondo es **dónde vive ese campo**, y hay dos sitios plausibles.

### Lo que ya existía

El modelo tenía dos niveles y la separación estaba razonada desde la Fase 1:

- `Barcode`: **qué dice** el código. Valor, formato, tipo semántico.
- `Detection`: **quién lo leyó y cuándo**. Motor, instante, latencia, origen. Esa separación es lo
  que habilita el objetivo G5, comparar motores: el mismo código físico produce detecciones
  distintas y esas diferencias son el dato interesante.

`Detection` es además el tipo que atraviesa el SPI entero: lo producen los ocho motores, lo envuelven
los seis decoradores (`FormatFiltering`, `RequestLimits`, `SemanticParsing`, `Deadline`, `Fallback`,
`DistinctDetections`), lo compara `ComparingScannerEngine` y lo puntúa `EngineScoreboard`.

## Decisión

**Un tercer nivel: `HistoryEntry(detection, note)`.** La nota no entra en `Detection`.

```kotlin
data class HistoryEntry(
    val detection: Detection,
    val note: String? = null,
)
```

El repositorio queda asimétrico a propósito — **entra una `Detection` y salen `HistoryEntry`s**:

```kotlin
interface ScanHistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntry>>
    suspend fun save(detection: Detection)
    suspend fun setNote(detectionId: String, note: String?)
    suspend fun delete(detectionId: String)
    suspend fun clear()
}
```

## Por qué no un campo en `Detection`

Habría sido una línea. Los tres motivos por los que no:

**1. Es un dato de otra naturaleza, y de otro momento.** Una `Detection` describe algo que ya pasó:
nada de lo que hay dentro puede cambiar después, porque el pasado no cambia. Una nota la escribe una
persona **más tarde** y la reescribe cuando quiere. Son las dos únicas dimensiones que importan aquí
—quién lo produce y cuándo— y difieren en las dos.

**2. Contamina la mitad del sistema que nunca la usará.** Ocho motores, seis decoradores, el
comparador, el marcador y la suite de contrato pasarían a acarrear un campo que en todo ese recorrido
vale siempre `null`. Un campo que solo tiene sentido en un extremo del sistema no pertenece al tipo
que recorre el sistema entero.

**3. Rompe una igualdad de la que depende el resto.** `Detection` es un `data class` y su igualdad se
usa de verdad: en la deduplicación, en el comparador y en una veintena de tests. Con la nota dentro,
"estas dos lecturas son la misma" pasaría a depender de si alguien escribió algo — y peor, la misma
lectura dejaría de ser igual a sí misma de un momento a otro.

El coste es real y es un tipo más, con su desempaquetado en la pantalla del historial. Se acepta:
está acotado a una feature.

## Consecuencias

**Positivas**

- El SPI de motores no se enteró: ni un archivo de `engines/` ni de `core/scanner-api` cambió.
- La búsqueda del historial mira valor **y** nota, que es media razón de que la nota exista.
- La exportación gana una columna sin que el formato del archivo dependa del modelo interno: el DTO
  de salida ya era propio (§9.7 del SDD).

**Negativas y su gestión**

- Un nivel más de anidamiento en la UI del historial (`entry.detection.barcode.rawValue`). Se acota
  desempaquetando `detection` una vez al principio de cada composable.
- La nota se normaliza —`""` y los espacios sueltos son `null`— y eso hay que hacerlo en un solo
  sitio o las tres implementaciones divergirán. Vive en `ScanHistory.setNote`, con
  `HistoryEntry.normalizeNote` como la función que las tres comparten.

## Lo que salió debajo, que era más grave que la funcionalidad

Añadir una columna obligó a mirar cómo se comportaba la base ante un cambio de esquema, y ahí había
**dos defectos latentes** que nunca se habían disparado porque nunca había habido una versión 2.

### La primera migración habría borrado el historial de todo el mundo

`buildBundled()` pedía `fallbackToDestructiveMigration(dropAllTables = true)`: *ante cualquier cambio
de esquema, borra la base y empieza de cero*. En silencio, sin registro y sin recuperación posible.
En una app sin cuenta, sin copia en la nube y sin papelera, ese historial es el único sitio donde
esos datos existen. Y la versión que lo habría provocado es justo la que invita al usuario a
anotarlo.

Ahora la subida va por `@AutoMigration(from = 1, to = 2)` —añadir una columna que admite `null` es
exactamente lo que resuelve sola a partir de los esquemas exportados— y lo destructivo queda **solo
para las bajadas de versión**, donde no hay alternativa porque el código no puede conocer un esquema
del futuro, y que en la práctica ocurre al saltar entre ramas en desarrollo.

La mitad simétrica está en el navegador: el campo `note` del DTO guardado lleva valor por defecto, de
modo que un historial escrito por una versión anterior sigue decodificando. Sin él, `load()` habría
descartado el historial entero al primer fallo de deserialización.

### Reinsertar una lectura borraba su nota

El DAO usaba `@Insert(onConflict = REPLACE)`, que en SQLite es un borrado seguido de un alta. El id
de una detección es determinista —motor, instante y valor—, así que volver a leer el mismo código en
el mismo milisegundo reemplazaba la fila **y se llevaba la nota por delante**.

Pasa a `IGNORE`. Una fila en conflicto es por construcción la misma lectura, con los mismos campos de
máquina, y lo único que puede haber cambiado es lo que escribió el usuario. Ignorar el alta es igual
de idempotente y no destruye nada. Es además lo que ya hacía el historial en memoria, que comprobaba
el id antes de añadir: las tres implementaciones coinciden ahora en la misma regla.

### La poda no distingue lo que importa

`trimTo(500)` borraba por antigüedad. Con notas, eso es perder sin avisar lo único del historial que
alguien se molestó en escribir a mano. La cláusula pasa a ser `WHERE note IS NULL`, y sus dos gemelas
en `:core:data` comparten `trimmedKeepingNotes`. El techo sigue acotando lo que genera volumen —una
sesión continua deja cientos de lecturas y ninguna nota— y deja de tocar lo demás.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| `Detection.note: String?` | Contamina los ocho motores y los seis decoradores con un campo siempre `null`, y mete un dato mutable del usuario en la igualdad de la que dependen la deduplicación y el comparador |
| Tabla `notes` aparte con clave foránea | La relación es uno a uno con la fila del historial; una tabla para eso es una unión en cada lectura a cambio de nada |
| Guardar la nota en las preferencias, indexada por id | Dos almacenes que hay que mantener sincronizados y que se desincronizan a la primera poda, exportación o borrado |
| Un `Map<String, String>` de notas en el ViewModel | No sobrevive a cerrar la app, que es justo el caso de uso: la nota existe para reconocer algo *mañana* |
