# Reglas de R8 para la build de release (deuda D7 saldada).
#
# Este archivo es corto a propósito. Cada regla `-keep` es una porción del binario que R8 no puede
# tocar, así que una lista larga y defensiva anula el motivo de activar R8. Lo que ya viene cubierto
# por las reglas de consumo de cada librería no se repite aquí:
#
#   - zxing-cpp trae `-keep class zxingcpp.** { *; }` en su AAR, que es lo que necesita su JNI:
#     el código C++ resuelve los métodos por nombre y renombrarlos los rompería en silencio.
#   - ML Kit, Play Services, CameraX, Room y Compose traen las suyas.
#   - Koin no necesita ninguna: calcula la clave de cada definición con `Class.getName()`, no con
#     los metadatos de Kotlin, así que un nombre ofuscado sigue siendo consistente en ejecución.
#     (Comprobado sobre `org.koin.mp.KoinPlatformTools.getClassName` de koin-core 4.2.2.)
#   - kotlinx-serialization tampoco: la exportación usa el serializador explícito
#     (`ExportedHistory.serializer()`) en lugar de la variante `reified`, que resolvería por
#     reflexión. Ver `HistoryExporter`.
#
# Lo que queda son avisos, no `-keep`.

# Kotlin/Native y las corrutinas referencian clases que no están en el binario de Android. No es un
# problema: son ramas de otras plataformas que R8 nunca va a alcanzar.
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# R8 avisa de anotaciones de tiempo de compilación que no viajan al APK.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.kotlin.**

# Las trazas de una build ofuscada son ilegibles sin esto, y `mapping.txt` por sí solo no basta si
# la línea se perdió. Cuesta unos pocos KB.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
