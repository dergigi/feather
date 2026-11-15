package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import org.json.JSONObject
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Display previews, like ants-android: amounts are invoice/proof face values, not new payment or redemption confirmations.
internal data class ZapActivity(val sender: String?, val recipient: String, val amount: String?, val comment: String, val unit: String = "sats")
private val zapPreviews = object : LinkedHashMap<String, ZapActivity?>(128, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ZapActivity?>?) = size > 1024
}

/** Called only by background repository/composable work. Never perform crypto on the UI thread. */
internal fun zapActivity(event: Event): ZapActivity? = synchronized(zapPreviews) {
    if (zapPreviews.containsKey(event.id)) return@synchronized zapPreviews[event.id]
    val result = runCatching {
        require(event.kind in listOf(9735, 9321))
        val recipient = requireNotNull(event.tagValue("p")?.let(Protocol::publicKey))
        if (event.kind == 9321) {
            val proofs = event.tags.filter { it.firstOrNull() == "proof" }
            require(proofs.isNotEmpty() && proofs.size <= 1000)
            require(proofs.sumOf { it.getOrNull(1)?.length ?: 0 } <= 262144)
            val amount = proofs.fold(BigDecimal.ZERO) { total, tag ->
                val raw = JSONObject(requireNotNull(tag.getOrNull(1))).get("amount").toString()
                require(raw.length in 1..18 && raw.all { it in '0'..'9' })
                val value = BigDecimal(raw)
                require(value.signum() > 0)
                total + value
            }.stripTrailingZeros().toPlainString()
            val unit = event.tagValue("unit")?.trim()?.takeIf { it.isNotEmpty() } ?: "sat"
            require(unit.length <= 12 && unit.all { it.isLetterOrDigit() })
            return@runCatching ZapActivity(event.pubKey, recipient, amount,
                event.content.withoutObjectMarkers().take(2000), if (unit == "sat") "sats" else unit)
        }
        val description = requireNotNull(event.tagValue("description"))
        require(description.length <= 32768)
        val request = Event.fromJson(description)
        require(request.kind == 9734 && request.verify() && request.tagValue("p") == recipient &&
            request.tagValue("e") == event.tagValue("e") && request.tagValue("a") == event.tagValue("a") &&
            (event.tagValue("P") == null || event.tagValue("P") == request.pubKey))
        val anonymous = request.tags.any { it.firstOrNull() == "anon" }
        val amount = runCatching { LnInvoiceUtil.getAmountInSats(requireNotNull(event.tagValue("bolt11"))) }
            .getOrNull()?.takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString()
        ZapActivity(request.pubKey.takeUnless { anonymous }, recipient, amount, request.content.withoutObjectMarkers().take(2000))
    }.getOrNull()
    zapPreviews[event.id] = result
    result
}

internal fun zapNoteReference(event: Event): NostrContent.Reference? =
    event.tagValue("e")?.let(Protocol::publicKey)?.let { NostrContent.Reference(it) }
        ?: bookmarkReferencesForZap(event)

private fun bookmarkReferencesForZap(event: Event): NostrContent.Reference? {
    val address = event.tagValue("a") ?: return null
    val parts = address.split(':', limit = 3)
    if (parts.size != 3) return null
    val kind = parts[0].toIntOrNull() ?: return null
    val author = Protocol.publicKey(parts[1]) ?: return null
    return NostrContent.Reference(address, author = author, kind = kind)
}

internal fun zapActivityReferences(event: Event): List<NostrContent.Reference> {
    val payment = zapActivity(event) ?: return emptyList()
    return listOfNotNull(payment.sender?.let { NostrContent.Reference(it, profile = true) },
        NostrContent.Reference(payment.recipient, profile = true), zapNoteReference(event))
}

@Composable
internal fun ZapActivityCard(event: Event, state: FeedState, onReference: (NostrContent.Reference) -> Unit) {
    val payment by produceState<ZapActivity?>(null, event.id) {
        value = withContext(Dispatchers.Default) { zapActivity(event) }
    }
    val zap = payment ?: return
    val nutzap = event.kind == 9321
    val paymentColor = if (nutzap) Color(0xFFC084FC) else Color(0xFFFFB300)
    if (zap.sender in state.mutedAuthors || zap.recipient in state.mutedAuthors) return
    // Separate complete entries more than the header/comment/note within each entry.
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.align(Alignment.End)) { NoteTimestamp(event) }
        // Equal profile widths keep the amount centered between the two outer avatars.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZapParty(zap.sender, state.profiles, Modifier.weight(1f), Alignment.Start, onReference)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    Icon(painterResource(R.drawable.zap), if (nutzap) stringResource(R.string.ui_nutzap_from_sender_to_recipient) else stringResource(R.string.ui_zap_from_sender_to_recipient), modifier = Modifier.size(24.dp), tint = paymentColor)
                    zap.amount?.let { Text("$it ${zap.unit}", style = MaterialTheme.typography.titleMedium, color = paymentColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            ZapParty(zap.recipient, state.profiles, Modifier.weight(1f), Alignment.End, onReference)
        }
        if (zap.comment.isNotBlank()) Text(zap.comment, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        zapNoteReference(event)?.let { reference ->
            val note = state.embedded[reference.id]
            val filter = LocalContentFilter.current
            if (!filter.hides(reference) && (note == null || !filter.hides(note))) {
                // Keep actions inside the referenced post, never on the payment receipt.
                EmbeddedNote(reference, note, state.profiles, reference.id in state.unavailableEmbeds, "${event.id}:${reference.id}", onReference,
                    actions = if (note != null && note.kind !in listOf(7, 17, 30030, 9735, 9321)) {
                        { ZapNoteActions(note, state) }
                    } else null)
            }
        }
    }
}

@Composable
private fun ZapParty(key: String?, profiles: Map<String, Profile>, modifier: Modifier, alignment: Alignment.Horizontal, onReference: (NostrContent.Reference) -> Unit) {
    val profile = profiles[key]
    val name = profile?.name ?: key?.let(FeatherRepository::shortKey) ?: "Anonymous"
    val recipient = alignment == Alignment.End
    Row(modifier.clickable(enabled = key != null, onClickLabel = "Open $name's profile") {
        key?.let { onReference(NostrContent.Reference(it, profile = true)) }
    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!recipient) Avatar(name, 36, profile?.picture)
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
            textAlign = if (recipient) TextAlign.End else TextAlign.Start,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (recipient) Avatar(name, 36, profile?.picture)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZapNoteActions(note: Event, state: FeedState) {
    val counts = state.engagement[note.id] ?: Engagement()
    val reply = LocalReply.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(CircleShape)
                .clickable(onClickLabel = "Reply to note") { reply(note) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.conversation), "Reply; ${counts.replies} replies",
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (counts.replies > 0) Text(counts.replies.toString(), maxLines = 1,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RepostButton(note, note.id in state.sentReposts, counts.reposts,
                ownCounted = state.publicKey != null && counts.repostedByAccount == state.publicKey)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrangeHeart(note, counts.reactions, state.sentReactions[note.id].orEmpty())
            NoteZap(note, counts, state.publicKey)
        }
    }
}
