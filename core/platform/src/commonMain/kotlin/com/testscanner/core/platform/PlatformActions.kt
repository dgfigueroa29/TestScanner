package com.testscanner.core.platform

/**
 * Servicios del sistema para hacer algo con un resultado: copiarlo, compartirlo, abrirlo.
 *
 * Vive en su propio módulo — y no en el dominio — por el mismo motivo que `PermissionController`:
 * son capacidades del sistema operativo, y el dominio no debe saber que existe un portapapeles.
 *
 * ### Por qué una interfaz y no tres
 * Las capacidades de los motores están segregadas porque un motor puede implementar unas sí y otras
 * no, y la UI necesita distinguirlo en tiempo de compilación. Aquí no: la plataforma es una sola y
 * siempre está presente. Lo único desigual es **compartir**, que en escritorio no existe, y eso se
 * resuelve con [canShare] en lugar de con una interfaz aparte que solo tendría un implementador.
 *
 * Los métodos devuelven `Boolean` en vez de lanzar porque fallar al copiar no es excepcional: el
 * portapapeles puede estar bloqueado por el sistema, el navegador puede negar el permiso, y la UI
 * solo necesita saber si avisar al usuario o no.
 */
interface PlatformActions {

    /** Si el sistema ofrece una hoja de compartir. En escritorio no la hay. */
    val canShare: Boolean

    suspend fun copyToClipboard(text: String): Boolean

    suspend fun share(text: String): Boolean

    /** Abre el enlace en la app que corresponda. `false` si nada sabe manejar ese esquema. */
    suspend fun openUrl(url: String): Boolean
}

/** Implementación inerte para tests y para plataformas donde alguna acción no aplica. */
class NoOpPlatformActions(override val canShare: Boolean = false) : PlatformActions {
    override suspend fun copyToClipboard(text: String): Boolean = false
    override suspend fun share(text: String): Boolean = false
    override suspend fun openUrl(url: String): Boolean = false
}
