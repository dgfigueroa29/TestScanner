package com.whyscan.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.whyscan.core.platform.PlatformActions

/**
 * Acciones del sistema en Android.
 *
 * Todas se lanzan desde el `Context` de aplicación, no desde una Activity: el controlador es un
 * singleton del grafo y retener la Activity filtraría memoria en cada rotación. El precio es
 * `FLAG_ACTIVITY_NEW_TASK`, obligatorio al arrancar una Activity fuera de otra.
 */
class AndroidPlatformActions(private val context: Context) : PlatformActions {

    override val canShare: Boolean = true

    override suspend fun copyToClipboard(text: String): Boolean = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }.isSuccess

    override suspend fun share(text: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // `createChooser` y no el intent pelado: sin él, Android recuerda una app por defecto y el
        // usuario pierde la posibilidad de elegir a dónde manda cada resultado.
        return start(Intent.createChooser(intent, null))
    }

    override suspend fun openUrl(url: String): Boolean =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private companion object {
        const val CLIP_LABEL = "WhyScan"
    }
}
