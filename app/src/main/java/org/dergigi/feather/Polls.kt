package org.dergigi.feather

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal data class Poll(val options: List<Pair<String, String>>, val multiple: Boolean, val endsAt: Long?, val relays: List<String>)
data class PollVoter(val author: String, val choices: Set<String>)
data class PollResults(val counts: Map<String, Int> = emptyMap(), val voters: Int = 0, val selected: Set<String> = emptySet(), val limited: Boolean = false, val ballots: List<PollVoter> = emptyList())
internal fun parsePoll(event: Event): Poll? {
    if (event.kind != 1068) return null
    val options = event.tags.filter { it.firstOrNull() == "option" }.mapNotNull {
        val id = it.getOrNull(1) ?: return@mapNotNull null
        val label = it.getOrNull(2)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        // Option IDs are opaque references: Primal uses UUIDs with hyphens.
        // Preserve the exact ID in response tags rather than normalizing it.
        if (id.isBlank() || id.length > 256 || id.any(Char::isISOControl)) return@mapNotNull null
        id to label
    }.distinctBy { it.first }
    if (options.size !in 2..64) return null
    val type = event.tagValue("polltype") ?: "singlechoice"
    if (type !in listOf("singlechoice", "multiplechoice")) return null
    val end = event.tagValue("endsAt")
    if (end != null && end.toLongOrNull() == null) return null
    return Poll(options, type == "multiplechoice", end?.toLongOrNull(), event.tags.filter { it.firstOrNull() == "relay" }
        .mapNotNull { it.getOrNull(1)?.let(Protocol::relay) }.distinct().take(8))
}

/** Image-only labels keep their opaque option ID; URLs are never used as vote IDs. */
private fun pollOptionImages(event: Event, poll: Poll?): Map<String, MediaContent.Attachment> {
    val metadata = event.tags.filter { it.firstOrNull() == "imeta" }.take(32).map { tag ->
        tag.drop(1).mapNotNull { field ->
            val split = field.indexOf(' ')
            if (split <= 0) null else field.take(split) to field.substring(split + 1)
        }.toMap()
    }.associateBy { it["url"] }
    return poll?.options.orEmpty().mapNotNull { (id, label) ->
        val url = MediaContent.secureUrl(label.trim()) ?: return@mapNotNull null
        val fields = metadata[url].orEmpty()
        val mime = fields["m"]?.substringBefore(';')?.trim()?.lowercase() ?: DetectedMedia.mime(url)
        val extension = runCatching { java.net.URI(url).path.substringAfterLast('.').lowercase() }.getOrDefault("")
        if (mime?.startsWith("image/") != true && extension !in setOf("jpg", "jpeg", "png", "webp", "gif", "avif"))
            return@mapNotNull null
        id to MediaContent.Attachment(url, MediaContent.Kind.IMAGE, alt = fields["alt"]?.take(500))
    }.toMap()
}

@Composable
private fun PollOptionImage(image: MediaContent.Attachment, description: String, compact: Boolean = false) {
    var ratio by remember(image.url) { mutableFloatStateOf(ImageRatios.get(image.url) ?: 16f / 9f) }
    val modifier = if (compact) Modifier.size(64.dp) else Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.5f, 3f))
    RemoteImage(image.url, modifier.clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        description = image.alt ?: description, crop = false, onImageSize = { width, height ->
            ImageRatios.put(image.url, width, height)?.let { ratio = it }
        })
}

internal fun tallyPoll(event: Event, responses: List<Event>, account: String?): PollResults {
    val poll = parsePoll(event) ?: return PollResults()
    val ids = poll.options.map { it.first }.toSet()
    val latest = responses.filter { it.kind == 1018 && it.tagValue("e") == event.id && it.createdAt >= event.createdAt &&
        (poll.endsAt == null || it.createdAt <= poll.endsAt) }
        .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).distinctBy { it.pubKey }
    val votes = latest.associate { response ->
        val selections = response.tags.filter { it.firstOrNull() == "response" }
        val relevant = if (poll.multiple) selections else selections.take(1)
        response.pubKey to relevant.mapNotNull { it.getOrNull(1)?.takeIf(ids::contains) }.toSet()
    }
    return PollResults(ids.associateWith { id -> votes.values.count { id in it } }, votes.values.count { it.isNotEmpty() },
        votes[account].orEmpty(), responses.size >= 2000,
        votes.filterValues { it.isNotEmpty() }.map { (author, choices) -> PollVoter(author, choices) })
}

internal data class PollActions(val votersVisible: (List<String>) -> Unit = {}, val vote: (Event, Set<String>) -> Unit = { _, _ -> })
internal val LocalPollActions = staticCompositionLocalOf { PollActions() }

@Composable
internal fun PollContent(event: Event, state: FeedState, onReference: (NostrContent.Reference) -> Unit, mediaKey: String = event.id) {
    val detectedTypes by DetectedMedia.types.collectAsStateWithLifecycle()
    val poll = remember(event.id) { parsePoll(event) }
    val optionImages by produceState<Map<String, MediaContent.Attachment>>(emptyMap(), event.id, detectedTypes) {
        value = withContext(Dispatchers.Default) { pollOptionImages(event, poll) }
    }
    val attachments by produceState<List<MediaContent.Attachment>>(emptyList(), event.id, detectedTypes) {
        value = withContext(Dispatchers.Default) {
            MediaContent.attachments(event, 32).filterNot { image ->
                poll?.options?.any { it.second.trim() == image.url } == true && !event.content.contains(image.url)
            }
        }
    }
    val body = remember(event.id, attachments) { MediaContent.text(event, attachments) }
    val quotes = remember(event.id) { NostrContent.quotes(event) }
    val results = state.pollResults[event.id] ?: PollResults()
    val actions = LocalPollActions.current
    var showVoters by remember(event.id) { mutableStateOf(false) }
    if (showVoters && poll != null) PollVotersSheet(poll, optionImages, results, state, actions, onReference) { showVoters = false }
    var selected by remember(event.id, state.publicKey, results.selected) { mutableStateOf(results.selected) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(event.id) {
        val end = poll?.endsAt ?: return@LaunchedEffect
        while (now < end) { delay(1000); now = System.currentTimeMillis() / 1000 }
    }
    val closed = poll?.endsAt?.let { now >= it } == true
    val busy = event.id in state.pendingVotes
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkPreviewContent(body, event = event) { part ->
            val richContent = richNoteText(part, event, state.profiles, onReference, quotes.map { it.id }.toSet())
            val content = if (quotes.isEmpty()) richContent else richContent.subSequence(0, richContent.text.trimEnd().length)
            if (content.isNotBlank()) NoteSelection(event) { LinkText(content, style = MaterialTheme.typography.bodyLarge) }
        }
        quotes.forEach { reference ->
            EmbeddedNote(reference, state.embedded[reference.id], state.profiles, reference.id in state.unavailableEmbeds, "$mediaKey:${reference.id}", onReference)
        }
        if (attachments.isNotEmpty()) NoteMedia(event, attachments, mediaKey)
        if (poll == null) {
            Text(stringResource(R.string.ui_poll_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            poll.options.forEachIndexed { index, (id, label) ->
                val image = optionImages[id]
                val count = results.counts[id] ?: 0
                val fraction = if (results.voters == 0) 0f else count.toFloat() / results.voters
                Surface(onClick = {
                    if (state.publicKey == null) actions.vote(event, setOf(id))
                    else selected = if (!poll.multiple) setOf(id) else if (id in selected) selected - id else selected + id
                }, enabled = !closed && !busy, shape = RoundedCornerShape(12.dp),
                    border = if (id in selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        Box(Modifier.matchParentSize().fillMaxWidth()) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary.copy(alpha = .16f)))
                        }
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (image != null) PollOptionImage(image, stringResource(R.string.ui_poll_image_option, index + 1))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (image == null) Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                else Spacer(Modifier.weight(1f))
                                if (id in selected) Icon(Icons.Default.Check, stringResource(R.string.ui_poll_selected), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (results.limited) R.string.ui_poll_votes_limited else R.string.ui_poll_votes, results.voters),
                    Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ui_poll_view_votes)) { showVoters = true }
                        .padding(vertical = 14.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (closed) Text(stringResource(R.string.ui_poll_closed), style = MaterialTheme.typography.labelSmall)
                else if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else FilledIconButton(onClick = { actions.vote(event, selected) }, enabled = selected.isNotEmpty() && selected != results.selected) {
                    Icon(Icons.Default.Check, stringResource(R.string.ui_poll_vote))
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollVotersSheet(poll: Poll, optionImages: Map<String, MediaContent.Attachment>, results: PollResults, state: FeedState, actions: PollActions,
    onReference: (NostrContent.Reference) -> Unit, onDismiss: () -> Unit) {
    val list = rememberLazyListState()
    val ballots = results.ballots
    val labels = remember(poll) { poll.options.toMap() }
    LaunchedEffect(list, ballots) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { item -> ballots.getOrNull(item.index)?.author }.distinct().take(24) }
            .distinctUntilChanged().collect { actions.votersVisible(it) }
    }
    DisposableEffect(Unit) { onDispose { actions.votersVisible(emptyList()) } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(if (results.limited) R.string.ui_poll_votes_limited else R.string.ui_poll_votes, results.voters),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ballots, key = { it.author }) { ballot ->
                val profile = state.profiles[ballot.author]
                val name = profile?.name ?: FeatherRepository.shortNpub(ballot.author)
                Row(Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    onReference(NostrContent.Reference(ballot.author, profile = true))
                }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(name, size = 36, picture = profile?.picture)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        ballot.choices.forEach { id ->
                            val image = optionImages[id]
                            if (image != null) PollOptionImage(image,
                                stringResource(R.string.ui_poll_image_option, poll.options.indexOfFirst { it.first == id } + 1), compact = true)
                            else labels[id]?.let { label ->
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
