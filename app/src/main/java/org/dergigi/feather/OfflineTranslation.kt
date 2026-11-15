package org.dergigi.feather

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dev.davidv.translator.ErrorType
import dev.davidv.translator.ITranslationCallback
import dev.davidv.translator.ITranslationService
import dev.davidv.translator.TranslationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal const val TRANSLATOR_PACKAGE = "dev.davidv.translator"
internal const val TRANSLATOR_WEBSITE = "https://github.com/DavidVentura/offline-translator/releases/latest"
internal class TranslatorUnavailable : Exception()
internal class TranslatorFailure(message: String?, val unavailableLanguage: String? = null) : Exception(message)

/** Public MIT-licensed AIDL interface; explicit local IPC only, no HTTP translation fallback. */
internal suspend fun translateOffline(context: Context, text: String, language: String): String = withContext(Dispatchers.IO) {
    require(text.isNotBlank() && text.length <= 20_000)
    val connected = CompletableDeferred<ITranslationService>()
    val result = CompletableDeferred<String>()
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) connected.completeExceptionally(TranslatorUnavailable())
            else connected.complete(ITranslationService.Stub.asInterface(binder))
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            val error = TranslatorFailure("Translator disconnected")
            connected.completeExceptionally(error)
            result.completeExceptionally(error)
        }
        override fun onNullBinding(name: ComponentName?) { connected.completeExceptionally(TranslatorUnavailable()) }
        override fun onBindingDied(name: ComponentName?) { onServiceDisconnected(name) }
    }
    var bound = false
    try {
        bound = context.bindService(Intent("dev.davidv.translator.ITranslationService")
            .setComponent(ComponentName(TRANSLATOR_PACKAGE, "$TRANSLATOR_PACKAGE.AidlTranslationService")),
            connection, Context.BIND_AUTO_CREATE)
        if (!bound) throw TranslatorUnavailable()
        val service = withTimeout(10_000) { connected.await() }
        withTimeout(90_000) {
            service.translate(text, "", language, object : ITranslationCallback.Stub() {
                override fun onTranslationResult(translatedText: String?) {
                    if (translatedText.isNullOrBlank() || translatedText.length > 80_000)
                        result.completeExceptionally(TranslatorFailure(null))
                    else result.complete(translatedText)
                }
                override fun onTranslationError(error: TranslationError?) {
                    val language = error?.takeIf { it.type == ErrorType.DETECTED_BUT_UNAVAILABLE }
                        ?.language?.trim()?.take(80)?.takeIf { it.isNotEmpty() && it != "und" }
                    result.completeExceptionally(TranslatorFailure(error?.message, language))
                }
            })
            result.await()
        }
    } finally {
        if (bound) runCatching { context.unbindService(connection) }
    }
}
