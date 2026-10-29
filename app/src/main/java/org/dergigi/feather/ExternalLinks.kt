package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent

/** Retain the actual destination even when a Nostr link is rendered as a short name. */
internal fun NostrContent.Reference.copyableLink(original: String? = null): String {
    if (hashtag != null) return original ?: "#$hashtag"
    if (original != null && !original.startsWith("#[")) {
        return if (original.startsWith("nostr:", true) || original.startsWith("http", true)) original else "nostr:$original"
    }
    external?.let { return if (it.startsWith("http", true)) it else "nostr:$it" }
    if (profile) return "nostr:${Protocol.npub(id)}"
    val address = id.split(':', limit = 3)
    return if (address.size == 3 && kind != null) "nostr:${NAddress.create(kind, address[1], address[2], relay = relays.firstOrNull()?.normalizeRelayUrl())}"
    else "nostr:${NEvent.create(id, author, kind, relay = relays.firstOrNull()?.normalizeRelayUrl())}"
}

/** Explicit app routing also works when Android has not verified NewPipe's web links. */
internal fun Context.openWebLink(url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http")) return
    fun launch(packageName: String? = null, nonBrowser: Boolean = false): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (packageName != null) setPackage(packageName)
            if (nonBrowser && Build.VERSION.SDK_INT >= 30) addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        })
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    val host = uri.host?.lowercase(Locale.ROOT)
    val youtube = host == "youtu.be" || host == "youtube.com" || host?.endsWith(".youtube.com") == true ||
        host == "youtube-nocookie.com" || host?.endsWith(".youtube-nocookie.com") == true
    if (host == "zapstore.dev" && uri.path?.startsWith("/apps/") == true) {
        if (launch("dev.zapstore.app")) return
    }
    if (youtube) {
        // Prefer the user's requested lightweight client, then the official app.
        if (launch("org.schabi.newpipe") || launch("com.google.android.youtube")) return
        if (Build.VERSION.SDK_INT >= 30 && launch(nonBrowser = true)) return
    }
    if (!launch()) AppMessages.notify(this, "No app can open this link.")
}

/** Normalize public profile websites off-main when reading metadata. */
internal fun profileWebsite(value: String?): String? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 && it.none(Char::isWhitespace) } ?: return null
    val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull() ?: return null
    return url.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
}
