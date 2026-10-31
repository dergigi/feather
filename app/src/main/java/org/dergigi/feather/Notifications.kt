package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal val notificationKinds = listOf(1, 1111, 1244, 6, 16, 7, 9735, 9321)
internal fun notificationTarget(event: Event): NostrContent.Reference? = when (event.kind) {
    6, 16 -> repostTarget(event)
    7 -> reactionTarget(event)
    9735, 9321 -> zapNoteReference(event)
    else -> NostrContent.Reference(event.id, author = event.pubKey, kind = event.kind)
}
private enum class NotificationType(val label: String) {
    ALL("Clear notification filters"), REPLIES("Replies"), REACTIONS("Reactions"),
    ZAPS("Zaps and nutzaps"), REPOSTS("Reposts"), MENTIONS("Mentions");

    fun includes(event: Event): Boolean = when (this) {
        ALL -> true
        REPLIES -> event.kind in listOf(1, 1111, 1244) && Protocol.isReply(event)
        REACTIONS -> event.kind == 7
        ZAPS -> event.kind in listOf(9735, 9321)
        REPOSTS -> event.kind in listOf(6, 16)
        MENTIONS -> event.kind in listOf(1, 1111, 1244) && !Protocol.isReply(event)
    }
}

internal data class NotificationPreview(val event: Event, val actor: String?, val text: String,
    val action: String, val reaction: ReactionChoice? = null, val amount: String? = null)

/** Resolve before truncating: a long encoded reference can otherwise consume the whole row. */
private fun notificationText(text: String, event: Event, profiles: Map<String, Profile>,
    embedded: Map<String, Event>, filter: ContentFilter): String {
    fun compact(body: String, source: Event, includeExcerpt: Boolean): String = buildString {
        var offset = 0
        for (token in NostrContent.tokens(body, source)) {
            append(body.substring(offset, token.start))
            val ref = token.reference
            when {
                ref == null || ref.hashtag != null -> append(token.text)
                ref.profile -> append("@${profiles[ref.id]?.name ?: FeatherRepository.shortKey(ref.id)}")
                else -> {
                    val target = embedded[ref.id]?.takeUnless { filter.hides(ref) || filter.hides(it) }
                    val author = target?.pubKey ?: ref.author
                    val name = author?.let { profiles[it]?.name ?: FeatherRepository.shortKey(it) }
                    append("↗ ")
                    append(name?.let { "@$it" } ?: FeatherRepository.shortKey(ref.id))
                    if (includeExcerpt && target != null) {
                        val excerpt = target.tagValue("title")
                            ?: MediaContent.text(target, MediaContent.attachments(target, 32))
                        val resolved = compact(excerpt, target, false).replace(Regex("\\s+"), " ").trim().take(120)
                        if (resolved.isNotEmpty()) { append(": "); append(resolved) }
                    }
                }
            }
            offset = token.end
        }
        append(body.substring(offset))
    }
    return compact(text.withoutObjectMarkers(), event, true).take(400)
}

internal data class NotificationSnapshot(
    val owner: String,
    val types: Int,
    val previews: List<NotificationPreview>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsPage(vm: FeatherViewModel, navigation: ReaderNavigation, entry: ReaderEntry, home: ReaderEntry, active: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingMutes by vm.pendingAccountMutes.collectAsStateWithLifecycle()
    var muteRow by remember(state.publicKey) { mutableStateOf<String?>(null) }
    BackHandler(active && muteRow != null) { muteRow = null }
    LaunchedEffect(active) { if (!active) muteRow = null }
    val owner = state.publicKey
    if (owner == null) {
        LaunchedEffect(active) { if (active) home.toolbar = true }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Notifications, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ui_notifications_sign_in), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    // Zero means all; each category otherwise toggles independently.
    val savedFilters by vm.notificationFilters.collectAsStateWithLifecycle()
    val selection = savedFilters
    if (selection == null || selection.first != owner) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        return
    }
    val focusManager = LocalFocusManager.current
    val search = entry.notificationSearch.takeIf { entry.notificationSearchOwner == owner }.orEmpty()
    val query = search.trim()
    val selectedTypes = selection.second
    val includedTypes = remember(selectedTypes) {
        NotificationType.entries.filter { it != NotificationType.ALL && selectedTypes and (1 shl it.ordinal) != 0 }
    }
    val events = state.notifications.takeIf { state.notificationsFor == owner }.orEmpty()
    val filter = remember(state.mutedAuthors, state.embedded, state.showSensitive, state.deletedReactions, state.deletedNotes, state.mutedWords) {
        ContentFilter(state.mutedAuthors, state.embedded, state.deletedReactions, state.showSensitive, state.deletedNotes, state.mutedWords)
    }
    // Keep rows alongside the retained LazyListState. Mounting a temporary empty list
    // on Back would clamp its saved index before the background previews are ready.
    val snapshot by produceState<NotificationSnapshot?>(
        entry.notifications?.takeIf { it.owner == owner && it.types == selectedTypes },
        owner, events, state.embedded, state.profiles, filter, selectedTypes,
    ) {
        val previousRows = entry.notifications?.takeIf { it.owner == owner }?.previews.orEmpty().associateBy { it.event.id }
        val rows = withContext(Dispatchers.Default) {
            events.filter { event -> (includedTypes.isEmpty() || includedTypes.any { it.includes(event) }) && !filter.hides(event) }.mapNotNull { event ->
                val target = notificationTarget(event)
                if (target != null && filter.hides(target)) return@mapNotNull null
                val payment = if (event.kind in listOf(9735, 9321)) zapActivity(event) ?: return@mapNotNull null else null
                val actor = if (payment != null) payment.sender else event.pubKey
                if (actor in state.mutedAuthors) return@mapNotNull null
                val source = if (event.kind in listOf(1, 1111, 1244)) event else state.embedded[target?.id]
                val text = payment?.comment?.takeIf { it.isNotBlank() }
                    ?: source?.let { it.tagValue("title") ?: MediaContent.text(it, MediaContent.attachments(it, 32)) }
                    ?: previousRows[event.id]?.text.orEmpty()
                NotificationPreview(event, actor, notificationText(text, source ?: event, state.profiles, state.embedded, filter), when(event.kind) {
                    6, 16 -> "Repost"; 7 -> "Reaction"; 9735 -> "Zap"; 9321 -> "Nutzap"
                    else -> if (Protocol.isReply(event)) "Reply" else "Mention"
                }, if (event.kind == 7) reactionChoice(event) else null,
                    payment?.amount?.let { "$it ${payment.unit}" })
            }
        }
        val updated = NotificationSnapshot(owner, selectedTypes, rows)
        entry.notifications = updated
        value = updated
    }
    val readySnapshot = snapshot?.takeIf { it.owner == owner && it.types == selectedTypes }
    val previews = readySnapshot?.previews.orEmpty()
    var previousTypes by remember { mutableIntStateOf(selectedTypes) }
    LaunchedEffect(selectedTypes) {
        if (previousTypes != selectedTypes) entry.list.scrollToItem(0)
        previousTypes = selectedTypes
    }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(active, message) { if (active) message?.let { AppMessages.show(it); vm.message.compareAndSet(it, null) } }
    LaunchedEffect(active, owner) {
        if (active) {
            vm.accountSettings.retry()
            vm.repo.openReference(null); vm.repo.viewProfile(null); vm.repo.viewThread(null); vm.repo.viewHashtag(null)
        }
    }
    val toolbarScroll = remember(entry, home) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                home.scrollToolbar(consumed.y)
                if (!entry.list.canScrollBackward && available.y > 0) home.toolbar = true
                return Offset.Zero
            }
        }
    }
    val context = LocalContext.current
    val media = (context.applicationContext as FeatherApplication).media
    val resources by media.resources.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val visiblePreviews = remember(previews, filter, query, state.profiles, pendingMutes) {
        previews.filter { item ->
            val profile = state.profiles[item.actor]
            !filter.hides(item.event) && item.actor !in filter.authors && item.actor !in pendingMutes &&
                (query.isEmpty() || item.text.contains(query, ignoreCase = true) ||
                    profile?.name?.contains(query, ignoreCase = true) == true ||
                    profile?.username?.contains(query, ignoreCase = true) == true ||
                    profile?.nip05?.contains(query, ignoreCase = true) == true)
        }
    }
    LaunchedEffect(visiblePreviews) {
        if (visiblePreviews.none { it.event.id == muteRow }) muteRow = null
    }
    val emptySettled by produceState(false, active, events, visiblePreviews.isEmpty(), state.notificationsLoading) {
        value = false
        if (active && !state.notificationsLoading && visiblePreviews.isEmpty()) { delay(1_500); value = true }
    }
    fun open(reference: NostrContent.Reference) {
        if (filter.hides(reference)) return
        val note = if (reference.profile) null else events.find { it.id == reference.id } ?: state.embedded[reference.id]
        if (reference.external != null && note == null) { context.openWebLink("https://njump.to/${reference.external}"); return }
        val destination = ReaderEntry(if (reference.profile) "profile" else "thread", note?.id ?: reference.id)
        destination.reference = reference
        if (note != null) destination.notes[ProfileTab.POSTS] = listOf(note)
        navigation.push(destination)
        vm.repo.openReference(reference)
        vm.repo.viewProfile(reference.id.takeIf { reference.profile })
        vm.repo.viewThread(note?.id ?: reference.id.takeUnless { reference.profile }, note)
    }
    LaunchedEffect(active, owner, previews) {
        if (active) snapshotFlow { entry.list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged().collect { vm.repo.readingNotes(it) }
    }
    LaunchedEffect(active, entry.list) {
        if (active) snapshotFlow { entry.list.firstVisibleItemIndex to entry.list.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) -> if (index == 0 && offset == 0) home.toolbar = true }
    }
    // Also protect the saved offset when recreating a screen without a retained snapshot.
    if (readySnapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalMedia provides MediaEnvironment(resources, active && lifecycle.isAtLeast(Lifecycle.State.STARTED), null),
    ) {
        PullToRefreshBox(isRefreshing = active && state.notificationsLoading,
            onRefresh = { vm.accountSettings.retry(); vm.repo.refreshNotifications() }, modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            LazyColumn(state = entry.list, modifier = Modifier.fillMaxSize().nestedScroll(toolbarScroll),
                contentPadding = PaddingValues(bottom = 144.dp)) {
                stickyHeader("notification-filters") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 72.dp, top = 8.dp, bottom = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            NotificationType.entries.forEach { type ->
                                if (type == NotificationType.ALL) {
                                    OutlinedIconButton(onClick = { vm.setNotificationFilters(0) }, enabled = selectedTypes != 0,
                                        modifier = Modifier.size(48.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        colors = IconButtonDefaults.outlinedIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            disabledContentColor = MaterialTheme.colorScheme.primary)) {
                                        Icon(Icons.Default.Notifications,
                                            if (selectedTypes == 0) "All notifications" else type.label, Modifier.size(24.dp))
                                    }
                                    return@forEach
                                }
                                IconToggleButton(checked = type in includedTypes,
                                    onCheckedChange = {
                                        vm.setNotificationFilters(selectedTypes xor (1 shl type.ordinal))
                                    },
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = type.label },
                                    colors = IconButtonDefaults.iconToggleButtonColors(
                                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    when (type) {
                                        NotificationType.ALL -> Unit
                                        NotificationType.REACTIONS -> Icon(Icons.Default.FavoriteBorder, null)
                                        NotificationType.MENTIONS -> Text("@", style = MaterialTheme.typography.titleLarge)
                                        else -> Icon(painterResource(when(type) {
                                            NotificationType.REPLIES -> R.drawable.conversation
                                            NotificationType.ZAPS -> R.drawable.zap
                                            else -> R.drawable.repost
                                        }), null, Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item("notification-search") {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.heightIn(min = 40.dp).padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            val hint = stringResource(R.string.ui_search_notifications)
                            BasicTextField(value = search, onValueChange = {
                                entry.notificationSearchOwner = owner
                                entry.notificationSearch = it.take(256)
                            }, modifier = Modifier.weight(1f).padding(vertical = 10.dp).semantics { contentDescription = hint },
                                singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                decorationBox = { field -> field() })
                            if (search.isNotEmpty()) IconButton(onClick = { entry.notificationSearch = "" }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.ui_clear_search), Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (visiblePreviews.isEmpty()) item("notification-status") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        if (query.isNotEmpty()) Icon(Icons.Default.Search, stringResource(R.string.ui_no_notification_matches), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (state.notificationsLoading || !emptySettled) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else if (state.error != null) IconButton(onClick = { AppMessages.show(state.error!!) }) {
                            Icon(Icons.Default.Warning, stringResource(R.string.ui_notification_connection_error), tint = MaterialTheme.colorScheme.error)
                        } else Icon(Icons.Default.Notifications, stringResource(R.string.ui_no_notifications), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(visiblePreviews, key = { it.event.id }) { item ->
                    val profile = state.profiles[item.actor]
                    val name = profile?.name ?: item.actor?.let(FeatherRepository::shortKey) ?: "Anonymous"
                    val tint = when(item.event.kind) { 9735 -> Color(0xFFFFB300); 9321 -> Color(0xFFC084FC); else -> MaterialTheme.colorScheme.primary }
                    val canMute = item.actor != null && item.actor != owner
                    val muteLabel = stringResource(R.string.ui_mute_account, name)
                    val moderationLabel = stringResource(R.string.ui_moderation_actions)
                    val revealMute: (() -> Unit)? = if (canMute) ({
                        muteRow = item.event.id.takeUnless { muteRow == it }
                    }) else null
                    Row(Modifier.fillMaxWidth().animateContentSize().combinedClickable(
                        onClickLabel = "Open ${item.action.lowercase()}",
                        onLongClickLabel = if (canMute) moderationLabel else null,
                        onLongClick = revealMute,
                        onClick = {
                            if (muteRow != null) muteRow = null
                            else (notificationTarget(item.event) ?: item.actor?.let { NostrContent.Reference(it, profile = true) })?.let(::open)
                        },
                    ).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.widthIn(min = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val reaction = item.reaction
                            if (reaction?.image != null) RemoteImage(reaction.image, Modifier.size(22.dp), "Reaction ${reaction.content}", crop = false)
                            else if (reaction?.content in listOf("", "+")) Icon(Icons.Default.Favorite, item.action, Modifier.size(22.dp), tint = Color(0xFFFF8A00))
                            else if (reaction != null) Text(reaction.display.take(16))
                            else if (item.event.kind == 7) Icon(Icons.Default.FavoriteBorder, item.action, Modifier.size(22.dp), tint = tint)
                            else if (item.action == "Mention") Text("@", color = tint, style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { contentDescription = "Mention" })
                            else Icon(painterResource(when(item.event.kind) {
                                6, 16 -> R.drawable.repost; 9735, 9321 -> R.drawable.zap; else -> R.drawable.conversation
                            }), item.action, Modifier.size(22.dp), tint = tint)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).then(if (item.actor != null) Modifier.combinedClickable(onClickLabel = "Open $name's profile",
                                onLongClickLabel = if (canMute) moderationLabel else null, onLongClick = revealMute,
                                onClick = { open(NostrContent.Reference(item.actor, profile = true)) }) else Modifier), contentAlignment = Alignment.Center) { Avatar(name, 36, profile?.picture) }
                            AnimatedVisibility(visible = canMute && muteRow == item.event.id,
                                enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                                // Keep the control within the avatar's height, without Material's layout padding.
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 2.dp)) {
                                        item.actor?.let { ProfileFollowButton(vm, it, size = 36) }
                                        IconButton(onClick = {
                                            item.actor?.let(vm.accountReports::open)
                                            muteRow = null
                                        }, modifier = Modifier.size(36.dp)) {
                                            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                                contentAlignment = Alignment.Center) {
                                                Icon(painterResource(R.drawable.report_flag), stringResource(R.string.ui_report_account, name),
                                                    Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                        IconButton(onClick = {
                                            item.actor?.let(vm::muteAccount)
                                            muteRow = null
                                        }, modifier = Modifier.size(36.dp)) {
                                            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                                contentAlignment = Alignment.Center) {
                                                Icon(painterResource(R.drawable.account_hidden), muteLabel, Modifier.size(22.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.amount?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tint) }
                            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        NotificationTimestamp(item.event, onLongClick = revealMute, longClickLabel = if (canMute) moderationLabel else null) {
                            (notificationTarget(item.event) ?: item.actor?.let { NostrContent.Reference(it, profile = true) })?.let(::open)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
