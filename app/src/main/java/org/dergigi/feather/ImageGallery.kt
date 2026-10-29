package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.maxBitmapSize
import coil3.size.Size
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt
import com.vitorpamplona.quartz.nip01Core.core.Event

internal val LocalGalleryVisible = staticCompositionLocalOf { false }
internal class GalleryOpener(private val open: (List<String>, Int, Event?, Boolean) -> Unit) {
    operator fun invoke(images: List<String>, start: Int, note: Event? = null, expandAlbum: Boolean = true) = open(images, start, note, expandAlbum)
}
internal val LocalOpenGallery = staticCompositionLocalOf { GalleryOpener { _, _, _, _ -> } }

@Composable
internal fun GalleryHost(state: FeedState, walletConnected: Boolean, covered: Boolean = false, album: () -> ProfilePhotos = { ProfilePhotos() }, onOpenNote: (String, Event?) -> Unit, onOpenProfile: (String) -> Unit, onLogin: () -> Unit = {}, onWallet: () -> Unit = {}, content: @Composable () -> Unit) {
    var urls by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var noteIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var sourceNotes by remember { mutableStateOf<List<Event?>>(emptyList()) }
    val filter = LocalContentFilter.current
    val hiddenSource = sourceNotes.any { it != null && filter.hides(it) } || noteIds.any { id -> filter.embedded[id]?.let(filter::hides) == true }
    LaunchedEffect(hiddenSource) {
        if (hiddenSource) { urls = arrayListOf(); noteIds = arrayListOf(); sourceNotes = emptyList() }
    }
    val currentAlbum by rememberUpdatedState(album)
    CompositionLocalProvider(LocalGalleryVisible provides urls.isNotEmpty(), LocalOpenGallery provides GalleryOpener { images, start, note, expandAlbum ->
        if (images.isNotEmpty() && (note == null || !filter.hides(note))) {
            val selected = start.coerceIn(images.indices)
            val photos = currentAlbum()
            val collection = photos.urls
            // Preserve exact indices (including duplicate URLs) for full-grid calls.
            // Single-note/inline callers in Photos must still open the whole album.
            val albumIndex = if (!expandAlbum) -1 else if (images == collection) selected else collection.indexOf(images[selected])
            if (albumIndex >= 0) {
                urls = ArrayList(collection); index = albumIndex
                sourceNotes = photos.sources
                noteIds = ArrayList(photos.sources.map { it.id })
            } else {
                urls = ArrayList(images); index = selected
                sourceNotes = List(images.size) { note }
                noteIds = ArrayList(List(images.size) { note?.id.orEmpty() })
            }
        }
    }) {
        content()
        if (urls.isNotEmpty() && !hiddenSource && !covered) ImageGallery(urls, index, noteIds, state, walletConnected,
            sourceNote = { page ->
                val id = noteIds.getOrNull(page)
                sourceNotes.getOrNull(page)?.takeIf { it.id == id }
                    ?: currentAlbum().sources.find { it.id == id }
                    ?: state.embedded[id]
                    ?: state.notes.find { it.id == id }
                    ?: state.profileEvents.find { it.id == id }
                    ?: state.threadNotes.find { it.id == id }
            },
            onOpenProfile = { pubkey ->
                urls = arrayListOf(); noteIds = arrayListOf(); sourceNotes = emptyList()
                onOpenProfile(pubkey)
            },
            onLogin = onLogin,
            onWallet = onWallet,
            onPage = { index = it },
            onDismiss = { urls = arrayListOf(); noteIds = arrayListOf(); sourceNotes = emptyList() },
            onOpenNote = { page ->
                val id = noteIds.getOrNull(page)?.takeIf { it.isNotBlank() }
                val note = sourceNotes.getOrNull(page)?.takeIf { it.id == id }
                if (id != null) {
                    urls = arrayListOf(); noteIds = arrayListOf(); sourceNotes = emptyList()
                    onOpenNote(id, note)
                }
            })
    }
}

// Swipe, double-tap/pinch zoom, background cycling and actions follow Boris.
@Composable
private fun ImageGallery(urls: List<String>, initialIndex: Int, noteIds: List<String>, state: FeedState, walletConnected: Boolean, sourceNote: (Int) -> Event?, onPage: (Int) -> Unit, onDismiss: () -> Unit, onOpenNote: (Int) -> Unit, onOpenProfile: (String) -> Unit, onLogin: () -> Unit, onWallet: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(urls.indices), pageCount = { urls.size })
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val galleryFocus = remember { FocusRequester() }
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    fun navigate(direction: Int) {
        if (navigationJob?.isActive == true || pager.isScrollInProgress) return
        val next = (pager.currentPage + direction).coerceIn(urls.indices)
        if (next != pager.currentPage) navigationJob = scope.launch { pager.animateScrollToPage(next) }
    }
    var zoomed by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val accessibility = LocalAccessibilityManager.current
    var busy by remember { mutableStateOf(false) }
    var reactionPicker by remember { mutableStateOf(false) }
    var zapPicker by remember { mutableStateOf(false) }
    val currentNote = sourceNote(pager.currentPage)
    val app = context.applicationContext as FeatherApplication
    val resources by app.media.resources.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    DisposableEffect(currentNote?.id) {
        app.repository.readingGallery(currentNote)
        onDispose { app.repository.readingGallery(null) }
    }
    val reactionActions = LocalReactionActions.current
    val zapActions = LocalZapActions.current
    val actionPending = currentNote?.id?.let { it in reactionActions.pending || it in zapActions.pending } == true
    var background by rememberSaveable { mutableIntStateOf(0) }
    var pendingSave by remember { mutableStateOf<List<String>?>(null) }
    val colors = listOf(Color.Black, Color.White, Color(0xFF666666))
    val foreground = if (background == 1) Color.Black else Color.White
    fun toast(message: String) { AppMessages.show(message) }
    fun save(images: List<String>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    var saved = 0
                    for (url in images) { ensureActive(); try { ImageStore.save(context, url); saved++ } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
                    saved
                }
                if (count == 0) toast("Couldn't save images. Check your connection and try again.")
                else AppMessages.notify(context, "Saved $count of ${images.size} images to Pictures${if (Build.VERSION.SDK_INT >= 29) "/Feather" else ""}.")
            } finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val images = pendingSave; pendingSave = null
        if (granted && images != null) save(images) else AppMessages.notify(context, "Storage permission is needed to save images on this Android version.")
    }
    fun download(images: List<String>) {
        if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) save(images)
        else { pendingSave = images; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    }
    fun share(url: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { ImageStore.share(context, url) }
                context.startActivity(Intent.createChooser(intent, "Share image"))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { toast("Couldn't share this image. Please try again.") }
            finally { busy = false }
        }
    }
    LaunchedEffect(pager.currentPage) { zoomed = false; onPage(pager.currentPage) }
    LaunchedEffect(pager.isScrollInProgress) {
        if (pager.isScrollInProgress) controlsVisible = false
    }
    LaunchedEffect(controlsVisible, interaction, menu, busy, reactionPicker, zapPicker, actionPending) {
        if (controlsVisible && !menu && !busy && !reactionPicker && !zapPicker && !actionPending) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(3_000L,
                containsIcons = true, containsText = true, containsControls = true) ?: 3_000L)
            controlsVisible = false
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LaunchedEffect(menu, reactionPicker, zapPicker) {
            if (!menu && !reactionPicker && !zapPicker) galleryFocus.requestFocus()
        }
        CopyableMessageHost()
        val window = (LocalView.current.parent as DialogWindowProvider).window
        HidePhotoStatusBar(window, hideNavigationBar = true)
        val systemBrightness by produceState(.5f, context) {
            value = withContext(Dispatchers.IO) {
                runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(.5f)
            }
        }
        var brightnessFeedback by remember { mutableStateOf<VideoFeedback?>(null) }
        var brightnessTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(brightnessTick) { delay(900); brightnessFeedback = null }
        DisposableEffect(window) {
            val original = window.attributes.screenBrightness
            onDispose { window.attributes = window.attributes.apply { screenBrightness = original } }
        }
        CompositionLocalProvider(LocalMedia provides MediaEnvironment(resources, lifecycle.isAtLeast(Lifecycle.State.STARTED), null)) {
        Box(Modifier.fillMaxSize().background(colors[background])
            .focusRequester(galleryFocus)
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (direction == 0 || menu || reactionPicker || zapPicker ||
                    event.isCtrlPressed || event.isMetaPressed || event.isAltPressed || event.isShiftPressed) false
                else {
                    if (event.type == KeyEventType.KeyDown) { interaction++; navigate(direction) }
                    true
                }
            }
            .focusable()
            .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed && !it.previousPressed }) interaction++
                }
            }
        }) {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableGalleryImage(urls[page], page == pager.currentPage,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    brightness = { window.attributes.screenBrightness.takeIf { it >= 0f } ?: systemBrightness },
                    onBrightness = { level ->
                        window.attributes = window.attributes.apply { screenBrightness = level }
                        brightnessFeedback = VideoFeedback("brightness", "${(level * 100).roundToInt()}%")
                        brightnessTick++
                    },
                    onZoomed = { if (page == pager.currentPage) { zoomed = it; if (it) controlsVisible = false } })
            }
            VideoGestureFeedback(brightnessFeedback, Modifier.align(Alignment.Center))
            AnimatedVisibility(controlsVisible, modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it }) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 4.dp)
                .background(colors[background].copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp)), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.ui_close_gallery), tint = foreground) }
                currentNote?.let { note ->
                    val author = state.profiles[note.pubKey]
                    val name = author?.name ?: FeatherRepository.shortKey(note.pubKey)
                    IconButton(onClick = { onOpenProfile(note.pubKey) },
                        modifier = Modifier.semantics { contentDescription = "Open $name’s profile" }) {
                        Avatar(name, size = 36, picture = author?.picture)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = foreground, strokeWidth = 2.dp)
                IconButton(onClick = { background = (background + 1) % colors.size }) { Icon(painterResource(R.drawable.contrast), stringResource(R.string.ui_change_gallery_background), tint = foreground) }
                IconButton(onClick = { download(listOf(urls[pager.currentPage])) }, enabled = !busy) { Icon(painterResource(R.drawable.download), stringResource(R.string.ui_save_image_to_pictures), tint = foreground) }
                IconButton(onClick = { share(urls[pager.currentPage]) }, enabled = !busy) { Icon(Icons.Outlined.Share, stringResource(R.string.ui_share_image), tint = foreground) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.ui_more_image_actions), tint = foreground) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_save_all_images)) },
                            leadingIcon = { Icon(painterResource(R.drawable.download_all), null) },
                            enabled = !busy,
                            onClick = { menu = false; download(urls.toList()) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_copy_image_url)) },
                            leadingIcon = { Icon(painterResource(R.drawable.copy), null) },
                            onClick = { menu = false; clipboard.setText(AnnotatedString(urls[pager.currentPage])) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_open_image_in_browser)) },
                            leadingIcon = { Icon(painterResource(R.drawable.open_external), null) },
                            onClick = { menu = false; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls[pager.currentPage]))) } },
                        )
                    }
                }
            }
            }
            AnimatedVisibility(controlsVisible && !noteIds.getOrNull(pager.currentPage).isNullOrBlank(),
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it }) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp, bottom = if (urls.size > 1) 72.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    currentNote?.let { note -> key(note.id) {
                        val counts = state.engagement[note.id] ?: Engagement()
                        val canReact = state.publicKey != null
                        val canZap = canReact && walletConnected
                        Surface(modifier = Modifier.graphicsLayer { alpha = if (canZap) 1f else 0.38f },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
                            if (!canZap) IconButton(onClick = if (canReact) onWallet else onLogin, modifier = Modifier.size(56.dp)) {
                                Icon(painterResource(R.drawable.zap), stringResource(if (canReact) R.string.ui_connect_wallet else R.string.ui_log_in_to_zap),
                                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else NoteZap(note, counts, state.publicKey,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, iconOnly = true, enabled = canZap,
                                onPickerVisibilityChanged = { zapPicker = it })
                        }
                        Surface(modifier = Modifier.graphicsLayer { alpha = if (canReact) 1f else 0.38f },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
                            if (!canReact) IconButton(onClick = onLogin, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.ui_log_in_to_react),
                                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else OrangeHeart(note, counts.reactions, state.sentReactions[note.id].orEmpty(),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, iconOnly = true, enabled = canReact,
                                onPickerVisibilityChanged = { reactionPicker = it })
                        }
                    } }
                    FloatingActionButton(onClick = { onOpenNote(pager.currentPage) },
                        shape = androidx.compose.foundation.shape.CircleShape) {
                        Icon(painterResource(R.drawable.conversation), stringResource(R.string.ui_open_image_s_note))
                    }
                }
            }
            AnimatedVisibility(controlsVisible && urls.size > 1, modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it }) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 8.dp)
                .background(colors[background].copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (urls.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = pager.currentPage > 0, onClick = { navigate(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.ui_previous_image), tint = foreground.copy(alpha = if (pager.currentPage > 0) 1f else 0.3f)) }
                Text("${pager.currentPage + 1} / ${urls.size}", color = foreground, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(enabled = pager.currentPage < urls.lastIndex, onClick = { navigate(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.ui_next_image), tint = foreground.copy(alpha = if (pager.currentPage < urls.lastIndex) 1f else 0.3f)) }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun ZoomableGalleryImage(url: String, isCurrent: Boolean, onToggleControls: () -> Unit, brightness: () -> Float, onBrightness: (Float) -> Unit, onZoomed: (Boolean) -> Unit) {
    val context = LocalContext.current
    val media = (context.applicationContext as FeatherApplication).media
    val resources by media.resources.collectAsState()
    val request = remember(context, url) {
        ImageRequest.Builder(context).data(url).size(2048, 2048).maxBitmapSize(Size(2048, 2048))
            .memoryCachePolicy(CachePolicy.DISABLED).build()
    }
    var decoded by remember(url) { mutableStateOf<coil3.Image?>(null) }
    ImageAnimation(decoded, isCurrent)
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var lastTap by remember(url) { mutableLongStateOf(0L) }
    val gestureScope = rememberCoroutineScope()
    var singleTap by remember(url) { mutableStateOf<Job?>(null) }
    val currentToggleControls by rememberUpdatedState(onToggleControls)
    val currentBrightness by rememberUpdatedState(brightness)
    val currentOnBrightness by rememberUpdatedState(onBrightness)
    DisposableEffect(url, isCurrent) { onDispose { singleTap?.cancel() } }
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    val currentOnZoomed by rememberUpdatedState(onZoomed)
    fun applyScale(next: Float, pan: Offset = offset) {
        scale = next.coerceIn(1f, 5f)
        offset = if (scale < 1.02f) { scale = 1f; Offset.Zero } else pan
        currentOnZoomed(scale > 1f)
    }
    LaunchedEffect(isCurrent) { if (!isCurrent) { scale = 1f; offset = Offset.Zero } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (failed) {
            IconButton(onClick = { failed = false; loaded = false; retry++ }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.ui_image_failed_to_load_retry)) }
        } else if (resources != null) key(url, retry) {
            AsyncImage(model = request, imageLoader = resources!!.images, contentDescription = "Gallery image; tap for controls, pinch or double-tap to zoom, swipe vertically to adjust brightness", contentScale = ContentScale.Fit,
                onSuccess = { loaded = true; decoded = it.result.image }, onError = { failed = true },
                modifier = Modifier.fillMaxSize().semantics {
                    customActions = listOf(CustomAccessibilityAction("Toggle gallery controls") { currentToggleControls(); true }, CustomAccessibilityAction("Zoom in") { applyScale(2.5f); true }, CustomAccessibilityAction("Reset zoom") { applyScale(1f); true })
                }.pointerInput(url) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.uptimeMillis
                        // Cancel the pending single tap as soon as the second touch arrives.
                        if (lastTap > 0 && start - lastTap < 300) singleTap?.cancel()
                        var endedAt = start
                        var pinching = false
                        var dragged = false
                        var direction = 0 // Lock to brightness or paging after touch slop.
                        var travel = Offset.Zero
                        val startBrightness = currentBrightness()
                        do {
                            val event = awaitPointerEvent()
                            endedAt = event.changes.maxOf { it.uptimeMillis }
                            val pan = event.calculatePan()
                            if (event.changes.count { it.pressed } >= 2) pinching = true
                            if (abs(pan.x) > 1f || abs(pan.y) > 1f) dragged = true
                            if (!pinching && scale == 1f) {
                                travel += pan
                                if (direction == 0 && travel.getDistance() > viewConfiguration.touchSlop) {
                                    direction = if (abs(travel.y) > abs(travel.x)) 1 else 2
                                    singleTap?.cancel()
                                }
                                if (direction == 1 && event.changes.none { it.isConsumed }) {
                                    dragged = true
                                    currentOnBrightness((startBrightness - travel.y / size.height.coerceAtLeast(1)).coerceIn(.01f, 1f))
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                            if (pinching || scale > 1f) {
                                val next = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                val maxX = size.width * (next - 1) / 2
                                val maxY = size.height * (next - 1) / 2
                                applyScale(next, Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY)))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (!pinching && !dragged && endedAt - start < 300) {
                            if (lastTap > 0 && start - lastTap < 300) {
                                singleTap?.cancel()
                                if (scale > 1f) applyScale(1f)
                                else {
                                    val next = 2.5f
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    // Counter the center-based layer transform so the tapped
                                    // image point stays beneath the finger as it magnifies.
                                    applyScale(next, (down.position - center) * (1f - next))
                                }
                                lastTap = 0L
                            }
                            else {
                                lastTap = start
                                singleTap?.cancel()
                                singleTap = gestureScope.launch { delay(300); currentToggleControls() }
                            }
                        }
                    }
                }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
            if (!loaded) CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}
