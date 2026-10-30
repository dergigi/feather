package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.text.NumberFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Shared one-tap zap and amount picker for notes and their gallery images. */
@Composable
internal fun NoteZap(note: Event, counts: Engagement, publicKey: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, iconOnly: Boolean = false, enabled: Boolean = true, onPickerVisibilityChanged: (Boolean) -> Unit = {}) {
    val zapAmount = remember(counts.zapSats) { counts.zapSats.stripTrailingZeros().toPlainString() }
    val zapActions = LocalZapActions.current
    val openSplitProfile = LocalZapSplitProfile.current
    val celebration = zapActions.celebrations[note.id] ?: 0L
    var picker by remember(note.id) { mutableStateOf(false) }
    var custom by remember(note.id) { mutableStateOf(false) }
    var input by remember(note.id) { mutableStateOf("") }
    var message by remember(note.id) { mutableStateOf("") }
    var choices by remember(note.id) { mutableStateOf(zapActions.amounts) }
    val callback by rememberUpdatedState(onPickerVisibilityChanged)
    LaunchedEffect(enabled) { if (!enabled) { picker = false; custom = false } }
    LaunchedEffect(picker, custom) { callback(picker || custom) }
    DisposableEffect(Unit) { onDispose { callback(false) } }
    val format = remember { NumberFormat.getIntegerInstance() }
    val pending = note.id in zapActions.pending
    val zapped = note.id in zapActions.confirmed ||
        (publicKey != null && counts.zappedByAccount == publicKey)
    Box {
        Row((if (iconOnly) Modifier.size(56.dp) else Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp)).clip(CircleShape)
            .combinedClickable(enabled = enabled,
                onClickLabel = "Zap 21 sats", onLongClickLabel = "Choose zap amount",
                onClick = { if (!pending) zapActions.zap(note, DEFAULT_ZAP_SATS, "") },
                onLongClick = { choices = zapActions.amounts; picker = true }).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            ZapBolt(note.id, publicKey, pending, zapped, celebration, tint,
                if (pending) "Zap in progress" else if (zapped) "You zapped this post; $zapAmount sats received"
                else "Zap 21 sats; $zapAmount sats received")
            if (!iconOnly && counts.zapSats.signum() > 0) Text(zapAmount, style = MaterialTheme.typography.labelMedium, color = tint)
        }
        DropdownMenu(expanded = picker && enabled, onDismissRequest = { picker = false }) {
            val amounts = choices.distinct().take(7).sorted()
            // Reserve the final two cells together, even with a short history.
            val rows = (amounts.size + 4) / 3
            val actionStart = rows * 3 - 2
            Column(Modifier.width(288.dp).padding(horizontal = 8.dp)) {
                ZapSplitRow(note) { key -> picker = false; openSplitProfile(key) }
                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { column ->
                            val slot = row * 3 + column
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                when {
                                    slot < amounts.size -> {
                                        val amount = amounts[slot]
                                        TextButton(onClick = {
                                            picker = false
                                            zapActions.zap(note, amount, "")
                                        }, modifier = Modifier.semantics { contentDescription = "Zap $amount sats" }) {
                                            Text(format.format(amount), maxLines = 1)
                                        }
                                    }
                                    slot == actionStart -> IconButton(onClick = {
                                        picker = false; input = ""; message = ""; custom = true
                                    }) { Icon(Icons.Default.Add, stringResource(R.string.ui_custom_zap_amount)) }
                                    slot == actionStart + 1 -> IconButton(onClick = {
                                        picker = false; input = DEFAULT_ZAP_SATS.toString(); message = ""; custom = true
                                    }) { Icon(painterResource(R.drawable.zap_comment), stringResource(R.string.ui_add_zap_message)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (custom && enabled) {
        val amount = input.toLongOrNull()?.takeIf { it in 1..MAX_ZAP_SATS }
        AlertDialog(onDismissRequest = { custom = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ZapSplitRow(note) { key -> custom = false; openSplitProfile(key) }
                OutlinedTextField(value = input, onValueChange = { value ->
                    if (value.length <= 16 && value.all { it in '0'..'9' }) input = value
                }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("sats") },
                    modifier = Modifier.semantics { contentDescription = "Zap amount in sats" })
                OutlinedTextField(value = message, onValueChange = { value ->
                    if (value.codePointCount(0, value.length) <= 280) message = value
                }, placeholder = { Text(stringResource(R.string.ui_public_message)) }, maxLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                IconButton(enabled = amount != null, onClick = {
                    amount?.let { custom = false; zapActions.zap(note, it, message) }
                }) { Icon(painterResource(R.drawable.zap), amount?.let { "Zap $it sats" } ?: "Enter zap amount") }
            })
    }
}

/** Animate only the glyph; keep the button, count and neighboring actions stationary. */
@Composable
private fun ZapBolt(noteId: String, account: String?, pending: Boolean, zapped: Boolean, celebration: Long,
    tint: Color, description: String) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    var seenCelebration by remember(noteId, account) { mutableLongStateOf(celebration) }
    val pulse = remember(noteId, account) { Animatable(1f) }
    val shimmer = remember(noteId, account) { Animatable(-1f) }
    LaunchedEffect(noteId, account, celebration, active) {
        val fresh = celebration > seenCelebration
        seenCelebration = celebration
        if (fresh && active) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(600, easing = LinearEasing))
        } else pulse.snapTo(1f)
    }
    LaunchedEffect(noteId, account, pending, active) {
        shimmer.snapTo(-1f)
        if (pending && active) {
            // Do not flash an animation for immediate validation failures.
            delay(150)
            shimmer.snapTo(0f)
            shimmer.animateTo(1f, infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart))
        }
    }
    val gold = Color(0xFFFFB300)
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.zap), description,
            Modifier.size(20.dp).graphicsLayer {
                // An isolated layer masks the sheen to the existing bolt silhouette.
                compositingStrategy = if ((pending && active) || pulse.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                val scale = if (!pending) 1f + .18f * sin(Math.PI * pulse.value).toFloat() else 1f
                scaleX = scale; scaleY = scale
            }.drawWithContent {
                drawContent()
                if (pending && active && shimmer.value >= 0f) {
                    val x = size.width * (-1f + 3f * shimmer.value)
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .9f), Color.Transparent),
                        start = Offset(x, size.height), end = Offset(x + size.width * .65f, 0f)),
                        blendMode = BlendMode.SrcAtop)
                } else if (!pending && pulse.value < 1f) {
                    drawRect(lerp(gold, Color(0xFFFFF4BF), sin(Math.PI * pulse.value).toFloat()),
                        blendMode = BlendMode.SrcAtop)
                }
            }, tint = if (zapped) gold else tint)
    }
}
