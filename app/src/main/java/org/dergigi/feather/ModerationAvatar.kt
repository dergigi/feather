package org.dergigi.feather

import androidx.activity.compose.BackHandler
import com.vitorpamplona.quartz.nip01Core.core.Event
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal class AvatarModeration(val owner: String?, val mute: (String) -> Unit, val report: (String) -> Unit,
    val isMuted: (String) -> Boolean, val unmute: (String) -> Unit, val mutePending: (String) -> Boolean, val deleteNote: (Event) -> Unit,
    val followButton: @Composable (String, Int) -> Unit) {
    var expanded by mutableStateOf<Any?>(null)
    var controlBounds: Pair<Any, Rect>? = null
}
internal val LocalAvatarModeration = staticCompositionLocalOf<AvatarModeration?> { null }

/** The avatar keeps its position and height while the actions roll out beside it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ModerationAvatar(pubkey: String, name: String, picture: String?, size: Int = 36,
    slotSize: Int = 48, expansionKey: Any? = null, profileToggle: Boolean = false, note: Event? = null, onClick: () -> Unit) {
    val actions = LocalAvatarModeration.current
    val token = remember(pubkey, expansionKey) { expansionKey ?: Any() }
    val ownNote = actions?.owner == pubkey && note?.kind in listOf(1, 20, 1111)
    val enabled = actions?.owner != null && (actions.owner != pubkey || ownNote)
    val expanded = enabled && actions?.expanded === token
    DisposableEffect(token, actions) {
        onDispose { if (actions?.expanded === token) actions.expanded = null }
    }
    BackHandler(expanded) { actions?.expanded = null }
    // Collapse only the trailing hit-area padding; the avatar and row height stay fixed.
    val avatarWidth by animateDpAsState(if (expanded) (size + 2).dp else slotSize.dp, label = "Avatar action spacing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(avatarWidth).height(slotSize.dp).combinedClickable(
            onClickLabel = "Open $name's profile",
            onLongClickLabel = if (enabled) stringResource(R.string.ui_moderation_actions) else null,
            onLongClick = if (enabled) ({ actions?.expanded = if (expanded) null else token }) else null,
            onClick = { actions?.expanded = null; onClick() },
        ), contentAlignment = Alignment.CenterStart) { Avatar(name, size, picture) }
        AnimatedVisibility(expanded,
            enter = expandHorizontally(expandFrom = Alignment.Start) + slideInHorizontally { -it } + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + slideOutHorizontally { -it } + fadeOut()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(Modifier.onGloballyPositioned { actions?.controlBounds = token to it.boundsInRoot() }.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (ownNote && note != null) {
                        HoldToConfirmButton(note.id, stringResource(R.string.ui_hold_to_delete_note), R.drawable.delete_note, size) {
                            actions?.expanded = null
                            actions?.deleteNote?.invoke(note)
                        }
                    } else {
                        actions?.followButton?.invoke(pubkey, size)
                        IconButton(onClick = { actions?.expanded = null; actions?.report?.invoke(pubkey) },
                            modifier = Modifier.size(size.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                            Icon(painterResource(R.drawable.report_flag), stringResource(R.string.ui_report_account, name),
                                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (profileToggle) {
                            val muted = actions?.isMuted?.invoke(pubkey) == true
                            val background = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer
                            val foreground = if (muted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
                            IconToggleButton(checked = muted, enabled = actions?.mutePending?.invoke(pubkey) != true,
                                onCheckedChange = { checked ->
                                    if (checked) actions?.mute?.invoke(pubkey) else actions?.unmute?.invoke(pubkey)
                                }, modifier = Modifier.size(size.dp),
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    containerColor = background, contentColor = foreground,
                                    checkedContainerColor = background, checkedContentColor = foreground,
                                    disabledContainerColor = background, disabledContentColor = foreground.copy(alpha = .65f))) {
                                Icon(painterResource(R.drawable.account_hidden),
                                    stringResource(if (muted) R.string.ui_unmute_account else R.string.ui_mute_account, name),
                                    Modifier.size(22.dp))
                            }
                        }

                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.avatarModerationName(pubkey: String, expansionKey: Any, ownNote: Boolean = false, onClick: () -> Unit): Modifier {
    val actions = LocalAvatarModeration.current
    if (actions?.owner == null || (actions.owner == pubkey && !ownNote)) return this
    return combinedClickable(
        onLongClickLabel = stringResource(R.string.ui_moderation_actions),
        onLongClick = { actions.expanded = if (actions.expanded === expansionKey) null else expansionKey },
        onClick = onClick,
    )
}

/** Consume only an outside tap's release, leaving scrolling and control gestures alone. */
internal fun Modifier.dismissAvatarModeration(actions: AvatarModeration): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(actions) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val token = actions.expanded
            val bounds = actions.controlBounds?.takeIf { it.first === token }?.second
            val outside = token != null && bounds?.contains(down.position + origin) != true
            var moved = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!change.pressed && outside && !moved && actions.expanded === token &&
                        change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                        change.consume()
                        actions.expanded = null
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
