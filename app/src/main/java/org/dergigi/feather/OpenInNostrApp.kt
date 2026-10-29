package org.dergigi.feather

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun Event.nostrPointer(relay: String? = null): String = tagValue("d")?.takeIf { kind in 30000..39999 }?.let {
    NAddress.create(kind, pubKey, it, relay = relay?.let(Protocol::relay)?.let { it.normalizeRelayUrl() })
} ?: NEvent.create(id, pubKey, kind, relay = relay?.let(Protocol::relay)?.let { it.normalizeRelayUrl() })

/** Resolve saved provenance off-main without fetching the event again. */
internal suspend fun Context.nostrPointer(event: Event): String =
    (applicationContext as FeatherApplication).repository.reference(event)

/** Explicit chooser, even if Feather is the default nostr: handler. Resolve packages off-main. */
internal suspend fun Context.openInNostrApp(event: Event) = openInNostrApp(nostrPointer(event))

internal suspend fun Context.openInNostrApp(pointer: String) {
    try {
        val chooser = withContext(Dispatchers.IO) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse("nostr:$pointer"))
            @Suppress("DEPRECATION")
            val handlers = packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
            if (handlers.none {
                it.activityInfo.packageName != packageName && it.activityInfo.exported &&
                    it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled
            }) return@withContext null

            val excluded = (handlers.filter { it.activityInfo.packageName == packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) } +
                ComponentName(this@openInNostrApp, MainActivity::class.java)).distinct().toTypedArray()
            Intent.createChooser(view, "Open in another Nostr app").apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            if (chooser == null) AppMessages.notify(this, "No other compatible Nostr app installed.")
            else startActivity(chooser)
        }
    } catch (error: ActivityNotFoundException) {
        AppMessages.show("No other compatible Nostr app available.", error.toString())
    } catch (error: SecurityException) {
        AppMessages.show("Could not open another Nostr app.", error.toString())
    }
}
