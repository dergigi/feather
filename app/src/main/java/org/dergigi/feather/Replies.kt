package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import kotlinx.coroutines.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.json.JSONObject
import org.json.JSONArray

internal val LocalReply = staticCompositionLocalOf<(Event) -> Unit> { {} }
internal data class ReplyDraft(val owner: String, val target: Event?, val text: String = "", val initialCursor: Int? = null, val gifs: List<GifItem> = emptyList(), val uploads: List<UploadedAttachment> = emptyList(), val picturePost: Boolean = false)

/** A selected passage is a blockquote in a real reply, not a standalone quote post. */
internal fun ReplyDraft.withQuotedSelection(selection: String): ReplyDraft {
    val passage = selection.trim()
    if (passage.isEmpty()) return this
    val quote = passage.lineSequence().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
    // Two line breaks leave one empty line between the quote and the reply/cursor.
    val prefix = "$quote\n\n"
    if (text.startsWith(prefix)) return copy(initialCursor = text.length)
    val combined = prefix + text
    require(combined.length <= 8000) { "The quote and saved draft are too long. Select a shorter passage or shorten your draft." }
    return copy(text = combined, initialCursor = combined.length)
}

/** Private local drafts and exact signed retries, bounded and accessed only on IO. */
internal class ReplyStore(context: Context) {
    private val drafts = context.getSharedPreferences("reply-drafts", Context.MODE_PRIVATE)
    private val media = context.getSharedPreferences("reply-gifs", Context.MODE_PRIVATE)
    private val uploads = context.getSharedPreferences("reply-uploads", Context.MODE_PRIVATE)
    private val pending = context.getSharedPreferences("reply-pending", Context.MODE_PRIVATE)
    private fun key(owner: String, target: Event?, picturePost: Boolean = false) = "$owner:${target?.id ?: if (picturePost) "picture" else "post"}"
    @Synchronized fun load(owner: String, target: Event?, picturePost: Boolean = false): ReplyDraft = ReplyDraft(owner, target,
        drafts.getString(key(owner, target, picturePost), "").orEmpty().take(8000),
        gifs = GifItem.list(media.getString(key(owner, target, picturePost), "[]").orEmpty()),
        uploads = runCatching {
            val array = JSONArray(uploads.getString(key(owner, target, picturePost), "[]"))
            (0 until minOf(array.length(), 32)).mapNotNull { index ->
                runCatching {
                    val item = array.getJSONObject(index)
                    UploadedAttachment(item.getString("url"), item.getLong("originalSize"), item.getLong("size"), item.optBoolean("compressed", false), item.optString("mime"), item.optString("hash"))
                        .takeIf { it.url.length <= 2048 && it.originalSize in 1..250L * 1024 * 1024 && it.size in 1..250L * 1024 * 1024 }
                }.getOrNull()
            }
        }.getOrDefault(emptyList()), picturePost = picturePost)
    @Synchronized fun save(draft: ReplyDraft) {
        val key = key(draft.owner, draft.target, draft.picturePost)
        val edit = drafts.edit()
        if (draft.text.isBlank()) edit.remove(key) else edit.putString(key, draft.text)
        drafts.all.keys.filter { it != key }.drop(15).forEach(edit::remove)
        check(edit.commit()) { "Could not save reply draft" }
        val attachments = draft.gifs.filter { draft.picturePost || it.url in draft.text }.distinctBy { it.url }.take(32)
        val mediaEdit = media.edit()
        if (attachments.isEmpty()) mediaEdit.remove(key)
        else mediaEdit.putString(key, JSONArray(attachments.map { it.json() }).toString())
        media.all.keys.filter { it != key }.drop(15).forEach(mediaEdit::remove)
        check(mediaEdit.commit()) { "Could not save GIF attachments" }
        val sizes = draft.uploads.filter { draft.picturePost || it.url in draft.text }.distinctBy { it.url }.take(32)
        val uploadEdit = uploads.edit()
        if (sizes.isEmpty()) uploadEdit.remove(key)
        else uploadEdit.putString(key, JSONArray(sizes.map {
            JSONObject().put("url", it.url).put("originalSize", it.originalSize).put("size", it.size).put("compressed", it.compressed).put("mime", it.mime).put("hash", it.hash)
        }).toString())
        uploads.all.keys.filter { it != key }.drop(15).forEach(uploadEdit::remove)
        check(uploadEdit.commit()) { "Could not save attachment sizes" }
    }
    @Synchronized fun signed(draft: ReplyDraft): Event? = pending.getString(key(draft.owner, draft.target, draft.picturePost), null)?.let {
        runCatching { Event.fromJson(JSONObject(it).getString("event")) }.getOrNull()
    }?.takeIf { it.pubKey == draft.owner && it.content == draft.text && (!draft.picturePost || (it.kind == 20 && it.tags.filter { tag -> tag.firstOrNull() == "imeta" }.mapNotNull { tag -> tag.firstOrNull { field -> field.startsWith("url ") }?.removePrefix("url ") } == draft.pictureUrls())) }
    @Synchronized fun saveSigned(draft: ReplyDraft, event: Event) {
        val key = key(draft.owner, draft.target, draft.picturePost)
        val edit = pending.edit().putString(key, JSONObject().put("event", event.toJson()).toString())
        pending.all.keys.filter { it != key }.drop(15).forEach(edit::remove)
        check(edit.commit()) { "Could not save signed reply for retry" }
    }
    @Synchronized fun complete(draft: ReplyDraft) {
        check(uploads.edit().remove(key(draft.owner, draft.target, draft.picturePost)).commit()) { "Could not clear attachment sizes" }
        check(media.edit().remove(key(draft.owner, draft.target, draft.picturePost)).commit()) { "Could not clear GIF attachments" }
        check(drafts.edit().remove(key(draft.owner, draft.target, draft.picturePost)).commit()) { "Could not clear reply draft" }
        check(pending.edit().remove(key(draft.owner, draft.target, draft.picturePost)).commit()) { "Could not clear reply retry" }
    }
}

/** Preserve thread scope; ordinary notes remain interoperable NIP-10 replies. */
internal fun replyTags(target: Event, relay: String, cachedRoot: Event? = null, rootRelay: (String) -> String? = { null }): Pair<Int, Array<Array<String>>> {
    val hex = Regex("[0-9a-f]{64}")
    val tags = mutableListOf<Array<String>>()
    if (target.kind == 1) {
        val es = target.tags.filter { it.size > 1 && it[0] == "e" && hex.matches(it[1]) }
        val root = es.firstOrNull { it.getOrNull(3) == "root" }
            ?: es.firstOrNull { it.size < 4 || it[3].isEmpty() }
        if (root == null) tags += arrayOf("e", target.id, relay, "root", target.pubKey)
        else {
            tags += arrayOf("e", root[1], (root.getOrNull(2)?.let(Protocol::relay) ?: rootRelay(root[1])).orEmpty(), "root", root.getOrNull(4)?.takeIf(hex::matches).orEmpty())
            tags += arrayOf("e", target.id, relay, "reply", target.pubKey)
        }
        (listOf(target.pubKey) + target.tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1)?.takeIf(hex::matches) })
            .distinct().take(64).forEach { tags += arrayOf("p", it) }
        return 1 to tags.toTypedArray()
    }
    val root = target.takeIf { it.kind in listOf(1111, 1244) }?.tags?.firstOrNull {
        it.size >= 2 && it[0] in listOf("E", "A", "I") && it[1].isNotBlank()
    }
    val addressParts = root?.takeIf { it[0] == "A" }?.get(1)?.split(':', limit = 3)
        ?.takeIf { it.size == 3 && it[0].toIntOrNull()?.let { kind -> kind in 30000..39999 } == true && hex.matches(it[1]) }
    val knownRoot = cachedRoot?.takeIf { root?.get(0) == "E" && it.id == root[1] }
    val rootKind = when (root?.get(0)) {
        "A" -> addressParts?.get(0)
        "E" -> if (hex.matches(root[1])) knownRoot?.kind?.toString()
            ?: target.tagValue("K")?.toIntOrNull()?.takeIf { it >= 0 }?.toString() else null
        "I" -> target.tagValue("K")?.takeIf { it.isNotBlank() }
            ?: externalReplyKind(root[1])
        else -> null
    }
    if (root != null && rootKind != null) {
        val inherited = root.toMutableList()
        if (root[0] in listOf("E", "A")) {
            while (inherited.size < 3) inherited.add("")
            inherited[2] = (inherited[2].let(Protocol::relay) ?: rootRelay(root[1])).orEmpty()
        }
        tags += inherited.toTypedArray()
        tags += arrayOf("K", rootKind)
        val rootAuthor = knownRoot?.pubKey ?: addressParts?.get(1)
            ?: root.takeIf { it[0] == "E" }?.getOrNull(3)?.takeIf(hex::matches)
            ?: target.tagValue("P")?.takeIf(hex::matches)
        rootAuthor?.let { tags += arrayOf("P", it, inherited.getOrNull(2).orEmpty()) }
    } else {
        // An incomplete legacy comment must not prevent a reply, including offline.
        // If its original scope cannot be recovered, scope to the actual parent
        // instead of inventing a kind for an unknown root event.
        val address = target.tagValue("d")?.takeIf { target.kind in 30000..39999 }?.let { "${target.kind}:${target.pubKey}:$it" }
        tags += if (address != null) arrayOf("A", address, relay) else arrayOf("E", target.id, relay, target.pubKey)
        tags += arrayOf("K", target.kind.toString())
        tags += arrayOf("P", target.pubKey, relay)
    }
    if (target.kind in 30000..39999) target.tagValue("d")?.let { tags += arrayOf("a", "${target.kind}:${target.pubKey}:$it", relay) }
    tags += arrayOf("e", target.id, relay, target.pubKey)
    tags += arrayOf("k", target.kind.toString())
    tags += arrayOf("p", target.pubKey, relay)
    return 1111 to tags.toTypedArray()
}

/** NIP-73 external scopes can identify their kind even when a comment omitted K. */
private fun externalReplyKind(scope: String): String? = when {
    scope.startsWith("https://") || scope.startsWith("http://") -> "web"
    scope.startsWith("#") && scope.length > 1 -> "#"
    else -> listOf("podcast:item:guid", "podcast:publisher:guid", "podcast:guid",
        "isbn", "geo", "iso3166", "isan", "doi").firstOrNull { scope.startsWith("$it:") }
}

internal val profileMentionPattern = Regex("(?:nostr:)?(?:npub|nprofile)1[023456789acdefghjklmnpqrstuvwxyz]+", RegexOption.IGNORE_CASE)
internal fun replyMentions(text: String): List<String> = profileMentionPattern.findAll(text).take(32)
    .mapNotNull { NostrContent.decode(it.value)?.takeIf { ref -> ref.profile }?.id }.distinct().toList()

private data class MentionInput(val start: Int, val end: Int, val query: String)
private fun mentionInput(value: TextFieldValue): MentionInput? {
    if (!value.selection.collapsed) return null
    val end = value.selection.end.coerceIn(0, value.text.length)
    val start = value.text.lastIndexOf('@', (end - 1).coerceAtLeast(0))
    if (start < 0 || start >= end || (start > 0 && !value.text[start - 1].isWhitespace())) return null
    val query = value.text.substring(start + 1, end)
    return if (query.length in 2..100 && query.none { it.isWhitespace() }) MentionInput(start, end, query) else null
}

/** The draft retains real nostr: references; the editor displays compact human-readable mentions. */
private fun mentionTransformation(text: String, labels: Map<String, String>): VisualTransformation {
    val replacements = profileMentionPattern.findAll(text).mapNotNull { match ->
        labels[match.value]?.let { match.range to "@$it" }
    }.toList()
    if (replacements.isEmpty()) return VisualTransformation.None
    return VisualTransformation { input ->
        if (input.text != text) TransformedText(input, OffsetMapping.Identity)
        else {
            val output = StringBuilder()
            val forward = IntArray(text.length + 1)
            val reverse = mutableListOf(0)
            var cursor = 0
            fun append(end: Int, shown: String) {
                val originalLength = end - cursor
                val start = output.length
                for (i in 0..originalLength) forward[cursor + i] = start + (i * shown.length / originalLength.coerceAtLeast(1))
                for (i in 1..shown.length) reverse.add(cursor + i * originalLength / shown.length)
                output.append(shown)
                cursor = end
            }
            for ((range, label) in replacements) {
                if (cursor < range.first) append(range.first, text.substring(cursor, range.first))
                append(range.last + 1, label)
            }
            if (cursor < text.length) append(text.length, text.substring(cursor))
            TransformedText(AnnotatedString(output.toString()), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = forward[offset.coerceIn(0, forward.lastIndex)]
                override fun transformedToOriginal(offset: Int) = reverse[offset.coerceIn(0, reverse.lastIndex)]
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyComposer(vm: FeatherViewModel) {
    val draft by vm.replyDraft.collectAsStateWithLifecycle()
    val busy by vm.replyBusy.collectAsStateWithLifecycle()
    val mining by vm.replyMining.collectAsStateWithLifecycle()
    val uploading by vm.imageUploading.collectAsStateWithLifecycle()
    val uploadProgress by vm.imageUploadProgress.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val current = draft ?: return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(uploading, uploadProgress, foreground) {
        val completed = uploadProgress
        if (foreground && !uploading && completed?.percent == 100) {
            delay(4000)
            vm.clearImageUploadProgress(completed)
        }
    }
    val media = (LocalContext.current.applicationContext as FeatherApplication).media
    val resources by media.resources.collectAsStateWithLifecycle()
    val focus = remember(current.owner, current.target?.id, current.picturePost) { FocusRequester() }
    var editor by remember(current.owner, current.target?.id, current.picturePost) { mutableStateOf(TextFieldValue(current.text, TextRange((current.initialCursor ?: if (current.text.startsWith("\nnostr:")) 0 else current.text.length).coerceIn(0, current.text.length)))) }
    var chosenNames by remember(current.owner, current.target?.id, current.picturePost) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(current.text) {
        if (editor.text != current.text) editor = TextFieldValue(current.text, TextRange(current.text.length))
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> vm.uploadImages(uris) }
    val images by produceState<List<String>>(emptyList(), current.text, current.uploads, current.gifs) {
        value = if (current.picturePost) current.pictureUrls() else withContext(Dispatchers.Default) {
            Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(current.text).map { it.value }
                .filter { url -> runCatching { java.net.URI(url).path?.substringAfterLast('.')?.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "mp4", "m4v", "mov", "webm") }.getOrDefault(false) }
                .distinct().toList()
        }
    }
    var previewVideo by remember { mutableStateOf<String?>(null) }
    val mention = remember(editor) { mentionInput(editor) }
    var suggestions by remember { mutableStateOf<List<SearchProfile>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mention?.query, current.owner, busy, foreground) {
        suggestions = emptyList(); searching = false; searchError = null
        val query = mention?.query ?: return@LaunchedEffect
        if (busy || !foreground) return@LaunchedEffect
        searching = true
        try { delay(350); suggestions = VertexProfileSearch.search(query).take(5) }
        catch (_: TimeoutCancellationException) { searchError = "Vertex mention search timed out" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { searchError = "Vertex mention search: ${e.message}" }
        finally { searching = false }
    }
    val labels by produceState<Map<String, String>>(emptyMap(), current.text, chosenNames, state.profiles) {
        value = withContext(Dispatchers.Default) {
            profileMentionPattern.findAll(current.text).take(32).mapNotNull { match ->
                NostrContent.decode(match.value)?.takeIf { it.profile }?.let { ref ->
                    match.value to (chosenNames[ref.id] ?: state.profiles[ref.id]?.name ?: FeatherRepository.shortKey(ref.id))
                }
            }.toMap()
        }
    }
    val transform = remember(current.text, labels) { mentionTransformation(current.text, labels) }
    var gifPicker by remember(current.owner, current.target?.id, current.picturePost) { mutableStateOf(false) }
    val gifFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val canSend = !busy && !uploading && (if (current.picturePost) current.pictureUrls().isNotEmpty() else current.text.isNotBlank())
    val sendButton: @Composable (Modifier) -> Unit = { modifier ->
        FilledIconButton(onClick = vm::sendReply, enabled = canSend,
            modifier = modifier, shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)) {
            if (busy) ComposerProgress(mining)
            else Icon(Icons.AutoMirrored.Filled.Send, if (current.target == null) stringResource(R.string.ui_publish_post) else stringResource(R.string.ui_send_reply))
        }
    }
    val gifButton: @Composable () -> Unit = {
        FilledIconButton(enabled = !busy, modifier = Modifier.size(if (current.target == null) 56.dp else 48.dp)
            .focusRequester(gifFocus).semantics { contentDescription = "Add a GIF" }, shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            onClick = { keyboard?.hide(); gifPicker = true }) { Text("GIF", style = MaterialTheme.typography.labelLarge) }
    }
    val imageButton: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
                uploadProgress?.let { progress ->
                    Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.End) {
                        progress.size?.let { size ->
                            Text(attachmentSizeLabel(LocalContext.current, size, progress.originalSize),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (!uploading && progress.percent == 100) "100%" else progress.stage,
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (uploading) {
                    Box(Modifier.size(if (current.target == null) 56.dp else 48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).semantics {
                        contentDescription = uploadProgress?.percent?.let { "Image upload $it percent" } ?: uploadProgress?.stage ?: "Preparing image"
                    }, contentAlignment = Alignment.Center) {
                        val percent = uploadProgress?.percent
                        if (percent == null) CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.dp)
                        else {
                            CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(34.dp), strokeWidth = 2.dp)
                            Text("$percent%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else FilledIconButton(enabled = !busy,
                    modifier = Modifier.size(if (current.target == null) 56.dp else 48.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer), onClick = {
                    imagePicker.launch(PickVisualMediaRequest(if (current.picturePost) ActivityResultContracts.PickVisualMedia.ImageOnly else ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) {
                    Icon(painterResource(R.drawable.photos), stringResource(if (current.picturePost) R.string.ui_add_photos else R.string.ui_attach_images_or_videos))
                }
        }
    }
    val addPhotoTile: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = !busy && !uploading, onClickLabel = stringResource(R.string.ui_add_photos)) {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, contentAlignment = Alignment.Center) {
            if (uploading) Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    val percent = uploadProgress?.percent
                    if (percent == null) CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.dp)
                    else {
                        CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(34.dp), strokeWidth = 2.dp)
                        Text("$percent%", fontSize = 11.sp)
                    }
                }
                uploadProgress?.let { progress ->
                    Text(progress.stage, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    progress.size?.let { size ->
                        Text(attachmentSizeLabel(LocalContext.current, size, progress.originalSize), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else Icon(Icons.Default.Add, stringResource(R.string.ui_add_photos), Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
    val attachments: @Composable () -> Unit = {
            val openGallery = LocalOpenGallery.current
            val tileSize = if (current.picturePost) 200 else 88
            val tileStep = tileSize + 8
            if (images.isNotEmpty()) Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                // Images have an 8dp gap; render controls last so adjacent thumbnails never steal their taps.
                Box(Modifier.width((images.size * tileStep + if (current.picturePost) 112 else 16).dp).height((tileSize + 24).dp)) {
                    images.forEachIndexed { index, url ->
                        val video = java.net.URI(url).path.substringAfterLast('.').lowercase() in listOf("mp4", "m4v", "mov", "webm")
                        val tile = Modifier.offset(x = (index * tileStep).dp, y = 24.dp).size(tileSize.dp).clip(RoundedCornerShape(16.dp))
                        if (video) Box(tile.background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable(onClickLabel = "Preview attached video") { previewVideo = url }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.ui_attached_video), Modifier.size(32.dp))
                        } else RemoteImage(current.gifs.find { it.url == url }?.still ?: url, tile
                            .clickable(onClickLabel = "Preview attached image") {
                                val photos = images.filterNot { java.net.URI(it).path.substringAfterLast('.').lowercase() in listOf("mp4", "m4v", "mov", "webm") }
                                openGallery(photos, photos.indexOf(url), expandAlbum = false)
                            }, "Attached image")

                    }
                    if (current.picturePost) addPhotoTile(Modifier.offset(x = (images.size * tileStep + 8).dp, y = 24.dp)
                        .width(96.dp).height(tileSize.dp))
                    images.forEachIndexed { index, url ->
                        IconButton(enabled = !busy,
                            modifier = Modifier.offset(x = (index * tileStep + tileSize - 24).dp).size(48.dp),
                            onClick = { if (current.picturePost) vm.removePicture(url) else vm.editReply(current.text.replace(url, "").trim()) }) {
                            Box(Modifier.size(28.dp).background(Color(0xFF8C1D18).copy(alpha = if (busy) .5f else 1f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, stringResource(R.string.ui_remove_attachment_from_post), Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
    }
    val composer: @Composable () -> Unit = {
        CopyableMessageHost()
        CompositionLocalProvider(LocalMedia provides MediaEnvironment(resources, foreground, null)) {
        GalleryHost(state, walletConnected = false, onOpenNote = { _, _ -> }, onOpenProfile = {}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = if (current.target == null) 152.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::closeReply) { Icon(Icons.Default.Close, stringResource(R.string.ui_close_and_keep_draft)) }
                Spacer(Modifier.weight(1f))
                val account = state.profiles[current.owner]
                val name = account?.name ?: FeatherRepository.shortKey(current.owner)
                val postingAs = stringResource(R.string.ui_posting_as, name)
                Box(Modifier.size(48.dp).clearAndSetSemantics { contentDescription = postingAs },
                    contentAlignment = Alignment.Center) {
                    Avatar(name, 40, account?.picture)
                }
            }
            current.target?.let { target ->
                val profile = state.profiles[target.pubKey]
                ReplyContextRow(target, profile?.name ?: FeatherRepository.shortKey(target.pubKey))
            }
            if (current.picturePost) {
                attachments()
                if (images.isEmpty()) addPhotoTile(Modifier.fillMaxWidth().height(180.dp))
            }
            TextField(value = editor, onValueChange = { next ->
                if (next.text.length <= 8000) { editor = next; vm.editReply(next.text) }
            }, visualTransformation = transform, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    // Some Bluetooth keyboards also advertise a D-pad. Deny vertical focus
                    // traversal so Compose passes arrows to its normal text/caret handler.
                    .focusProperties { up = FocusRequester.Cancel; down = FocusRequester.Cancel }
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.N) {
                            true // Already composing: preserve this draft and do not insert an N.
                        } else if ((event.isCtrlPressed || event.isMetaPressed) && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            // Consume before TextField inserts a newline; held keys must not resend.
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && canSend)
                                vm.sendReply()
                            true
                        } else false
                    }.semantics { contentDescription = if (current.target == null) "Post text" else "Reply text" },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent))
            if (!current.picturePost) attachments()
            if (searching) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            searchError?.let { error -> IconButton(onClick = { AppMessages.show(error) }) {
                Icon(androidx.compose.material.icons.Icons.Default.Info, stringResource(R.string.ui_mention_search_error))
            } }
            if (mention != null && !busy) suggestions.filter { it.event.pubKey !in state.mutedAuthors && (state.showSensitive || !it.sensitive) }.forEach { profile -> key(profile.event.pubKey) {
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Mention ${profile.name}") {
                    val input = mentionInput(editor) ?: return@clickable
                    val reference = "nostr:${Protocol.npub(profile.event.pubKey)} "
                    val text = editor.text.replaceRange(input.start, input.end, reference)
                    if (text.length <= 8000) {
                        chosenNames = chosenNames + (profile.event.pubKey to profile.name)
                        editor = TextFieldValue(text, TextRange(input.start + reference.length))
                        vm.editReply(text)
                        focus.requestFocus()
                    }
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(profile.name, 32, profile.picture)
                    Text(profile.name, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    ProfileStatusIcons(profile, profile.event.pubKey in state.followedAccounts)
                }
            } }
            if (current.target != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                gifButton()
                Spacer(Modifier.weight(1f))
                imageButton()
                sendButton(Modifier.size(48.dp))
            }
        }
        }
        }
        LaunchedEffect(current.owner, current.target?.id, current.picturePost) { if (!current.picturePost) focus.requestFocus() }
    }
    if (current.target == null) {
        Dialog(onDismissRequest = vm::closeReply,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    composer()
                    previewVideo?.let { ComposerVideoPreview(it) { previewVideo = null } }
                    if (gifPicker) GifPicker(onDismiss = { gifPicker = false; gifFocus.requestFocus() }, onPick = { vm.attachGif(it); gifPicker = false; gifFocus.requestFocus() })
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 80.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!current.picturePost) gifButton()
                        Spacer(Modifier.weight(1f))
                        if (!current.picturePost) imageButton()
                        sendButton(Modifier.size(56.dp))
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = vm::closeReply,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface) {
                composer()
                previewVideo?.let { ComposerVideoPreview(it) { previewVideo = null } }
                if (gifPicker) GifPicker(onDismiss = { gifPicker = false; gifFocus.requestFocus() }, onPick = { vm.attachGif(it); gifPicker = false; gifFocus.requestFocus() })
            }
    }
}

private fun attachmentSizeLabel(context: Context, size: Long, originalSize: Long?): String {
    val result = android.text.format.Formatter.formatShortFileSize(context, size)
    val original = originalSize?.takeIf { it > size }?.let { android.text.format.Formatter.formatShortFileSize(context, it) }
    return if (original == null || original == result) result else "$original → $result"
}

/** Shared, single-line parent summary for displayed replies and the composer. */
@Composable
internal fun ReplyContextRow(parent: Event?, name: String, kind: Int? = parent?.kind, loading: Boolean = false, onClick: (() -> Unit)? = null) {
    val detectedTypes by DetectedMedia.types.collectAsStateWithLifecycle()
    val summary by produceState("" to kind, parent?.id, kind, detectedTypes) {
        value = withContext(Dispatchers.Default) {
            val media = parent?.let { MediaContent.attachments(it, 32) }.orEmpty()
            val type = when {
                kind in voiceKinds -> kind
                media.any { it.kind == MediaContent.Kind.AUDIO } -> 1222
                kind in videoKinds || media.any { it.kind == MediaContent.Kind.VIDEO } -> 21
                kind == 30023 || kind == 9802 -> kind
                kind == 20 || media.any { it.kind == MediaContent.Kind.IMAGE } -> 20
                else -> kind
            }
            val text = parent?.tagValue("title")?.takeIf { it.isNotBlank() }
                ?: parent?.let { if (it.kind in voiceKinds) "" else MediaContent.text(it, media) }.orEmpty()
            text.withoutObjectMarkers().replace(Regex("\\s+"), " ").trim().take(240) to type
        }
    }
    val description = when (summary.second) {
        in voiceKinds -> "Reply to voice note by $name"
        in videoKinds -> "Reply to video by $name"
        20 -> "Reply to image by $name"
        30023 -> "Reply to article by $name"
        9802 -> "Reply to highlight by $name"
        else -> "Reply to $name"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(
        if (onClick == null) Modifier else Modifier.clickable(onClickLabel = "Open parent note", onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = "Loading earlier conversation" },
            strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        else if (summary.second in videoKinds) Icon(Icons.Default.PlayArrow, description, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        else Icon(painterResource(when (summary.second) {
            in voiceKinds -> R.drawable.voice
            20 -> R.drawable.photos
            30023 -> R.drawable.article
            9802 -> R.drawable.highlighter
            else -> R.drawable.reply
        }), description, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (summary.first.isEmpty()) name else "$name · ${summary.first}", maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Extra rotation only during CPU mining; signing and relay delivery keep the regular spinner. */
@Composable
private fun ComposerProgress(mining: Boolean) {
    if (mining) {
        val transition = rememberInfiniteTransition(label = "Proof of work")
        val rotation by transition.animateFloat(0f, 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "Mining rotation")
        CircularProgressIndicator(Modifier.size(22.dp).graphicsLayer { rotationZ = rotation }
            .semantics { contentDescription = "Mining proof of work" },
            strokeWidth = 2.dp, color = Color(0xFFFF8A00))
    } else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
}
