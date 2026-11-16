package org.dergigi.feather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import java.math.BigDecimal
import java.math.RoundingMode

internal data class ZapSplit(val pubkey: String, val relays: List<String>, val weight: BigDecimal, val total: BigDecimal) {
    val percentage: String get() = weight.multiply(BigDecimal(100)).divide(total, 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"
}

/** NIP-57 Appendix G: absent weights are equal only when every weight is absent. */
internal fun zapSplits(event: Event): List<ZapSplit> {
    val tags = event.tags.filter { it.firstOrNull() == "zap" }
    if (tags.isEmpty() || tags.size > 64) return emptyList()
    val weighted = tags.any { it.size > 3 }
    val recipients = linkedMapOf<String, Pair<BigDecimal, List<String>>>()
    for (tag in tags) {
        val key = tag.getOrNull(1)?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } } ?: return emptyList()
        if (tag.size > 3 && (tag[3].length > 32 || !tag[3].matches(Regex("[0-9]+(\\.[0-9]+)?")))) return emptyList()
        val weight = if (!weighted) BigDecimal.ONE else tag.getOrNull(3)?.takeIf { it.length <= 32 && it.matches(Regex("[0-9]+(\\.[0-9]+)?")) }?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (weight.signum() <= 0) continue
        val relay = tag.getOrNull(2)?.let(Protocol::relay)
        val previous = recipients[key]
        recipients[key] = (previous?.first ?: BigDecimal.ZERO).add(weight) to (previous?.second.orEmpty() + listOfNotNull(relay)).distinct()
    }
    val total = recipients.values.fold(BigDecimal.ZERO) { sum, item -> sum.add(item.first) }
    return recipients.map { (key, item) -> ZapSplit(key, item.second, item.first, total) }
}

internal val LocalZapSplitProfile = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun ZapSplitRow(note: Event, onProfile: (String) -> Unit = LocalZapSplitProfile.current) {
    val splits = remember(note.id) { zapSplits(note) }
    if (splits.isEmpty()) return
    val repository = (LocalContext.current.applicationContext as FeatherApplication).repository
    val profiles by remember(note.id, repository) { repository.zapSplitProfiles(note) }.collectAsState(emptyMap())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.zap_split), stringResource(R.string.ui_zap_split), Modifier.size(16.dp), tint = muted)
        }
        LazyRow(Modifier.weight(1f)) {
            items(splits, key = { it.pubkey }) { split ->
                val profile = profiles[split.pubkey]
                val name = profile?.name ?: FeatherRepository.shortNpub(split.pubkey)
                val label = "$name · ${split.percentage}"
                Column(Modifier.widthIn(min = 38.dp).clickable { onProfile(split.pubkey) }
                    .semantics { contentDescription = label }.padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, size = 28, picture = profile?.picture)
                    Text(split.percentage, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
        }
    }
}

/** Largest remainders preserve the exact user-selected total, with stable tag-order ties. */
internal fun allocateZap(splits: List<ZapSplit>, sats: Long): List<Long> {
    require(splits.isNotEmpty() && sats in 1..MAX_ZAP_SATS)
    val products = splits.map { it.weight.multiply(BigDecimal.valueOf(sats)) }
    val total = splits.first().total
    val amounts = products.map { it.divide(total, 0, RoundingMode.DOWN).longValueExact() }.toMutableList()
    val remaining = (sats - amounts.sum()).toInt()
    val order = products.indices.sortedWith(compareByDescending<Int> {
        products[it].subtract(total.multiply(BigDecimal.valueOf(amounts[it])))
    }.thenBy { it })
    order.take(remaining).forEach { amounts[it]++ }
    require(amounts.all { it > 0 }) { "Increase the zap amount so every split recipient receives at least one sat" }
    return amounts
}
