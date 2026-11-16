package org.dergigi.feather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.DateFormat
import java.util.Date

internal const val ATTESTATION_KIND = 31871
internal val LocalAttestationNotes = compositionLocalOf<Map<String, Event>> { emptyMap() }
private val LocalAttestationDepth = compositionLocalOf { 0 }

/** The assertion is a reference, never a reply edge or an endorsement by Feather. */
internal fun attestationTarget(event: Event): NostrContent.Reference? {
    if (event.kind != ATTESTATION_KIND) return null
    val address = event.tags.firstNotNullOfOrNull { tag ->
        if (tag.firstOrNull() != "a") return@firstNotNullOfOrNull null
        val parts = tag.getOrNull(1)?.split(':', limit = 3) ?: return@firstNotNullOfOrNull null
        if (parts.size != 3) return@firstNotNullOfOrNull null
        val kind = parts[0].toIntOrNull()?.takeIf { it in 30000..39999 } ?: return@firstNotNullOfOrNull null
        val author = Protocol.publicKey(parts[1]) ?: return@firstNotNullOfOrNull null
        NostrContent.Reference("$kind:$author:${parts[2]}", author = author, kind = kind,
            relays = listOfNotNull(tag.getOrNull(2)?.let(Protocol::relay)))
    }
    val target = address ?: event.tags.firstNotNullOfOrNull { tag ->
        if (tag.firstOrNull() != "e") return@firstNotNullOfOrNull null
        tag.getOrNull(1)?.takeIf { it.length == 64 }?.let(Protocol::publicKey)?.let { id ->
            NostrContent.Reference(id, relays = listOfNotNull(tag.getOrNull(2)?.let(Protocol::relay)),
                author = tag.getOrNull(3)?.let(Protocol::publicKey))
        }
    }
    return target?.takeUnless { it.id == event.id || it.id == ThreadRelations.address(event) }?.let(NostrContent::navigable)
}

/** attestr links carry a public NIP-19 address; no website request is necessary. */
internal fun attestationWebReference(raw: String): NostrContent.Reference? {
    val url = raw.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" || url.host != "attestr.xyz" || url.port != 443 ||
        url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    val path = url.pathSegments
    if (path.size != 2 || path[0] != "attestations" || !path[1].startsWith("naddr1")) return null
    return NostrContent.decode(path[1])?.takeIf { it.kind == ATTESTATION_KIND }
}

@Composable
internal fun AttestationContent(event: Event, profiles: Map<String, Profile>, compact: Boolean,
    onReference: (NostrContent.Reference) -> Unit) {
    val status = remember(event.id) { event.tagValue("s") }
    val label = stringResource(when (status) {
        "valid" -> R.string.ui_attestation_valid
        "invalid" -> R.string.ui_attestation_invalid
        "revoked" -> R.string.ui_attestation_revoked
        "verifying" -> R.string.ui_attestation_verifying
        else -> R.string.ui_attestation_unknown
    })
    val target = remember(event.id) { attestationTarget(event) }
    val notes = LocalAttestationNotes.current
    val depth = LocalAttestationDepth.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(when (status) {
                    "valid" -> Icons.Default.CheckCircle
                    "invalid", "revoked" -> Icons.Default.Close
                    else -> Icons.Default.Info
                }, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ui_attestation_status, label), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            listOf("valid_from" to R.string.ui_attestation_from, "valid_to" to R.string.ui_attestation_until).forEach { (tag, resource) ->
                val date = remember(event.id, tag, androidx.compose.ui.platform.LocalConfiguration.current) {
                    event.tagValue(tag)?.toLongOrNull()?.takeIf { it in 0..253402300799L }?.let {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000))
                    }
                }
                if (date != null) Text(stringResource(resource, date), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (target != null) {
                if (depth == 0 && target.external == null) {
                    // A single assertion preview; nested attestations retain a link to their target.
                    CompositionLocalProvider(LocalAttestationDepth provides 1) {
                        EmbeddedNote(target, notes[target.id], profiles, "${event.id}:${target.id}", onReference, compact)
                    }
                } else if (!LocalContentFilter.current.hides(target)) {
                    Text(stringResource(R.string.ui_attestation_subject), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onReference(target) }.padding(vertical = 8.dp))
                }
            }
            if (event.content.isNotBlank()) NoteSelection(event) {
                LinkText(richNoteText(event.content, event, profiles, onReference), style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (compact) 8 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
