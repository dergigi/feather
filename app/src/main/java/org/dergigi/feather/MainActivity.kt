package org.dergigi.feather

import androidx.compose.ui.res.stringResource

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private val Sky = Color(0xFF79C4F2)

class MainActivity : ComponentActivity() {
    private val composerModel: FeatherViewModel by viewModels()
    private val repo get() = (application as FeatherApplication).repository
    internal var onReaderShortcut: ((ReaderShortcut) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        composerModel.onboarding.allowed.value = intent.action == null || intent.action == Intent.ACTION_MAIN
        composerModel.onboarding.icon.value = null
        if (android.os.Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { splash ->
            val icon = splash.iconView
            if (icon == null || icon.width <= 0 || icon.height <= 0) {
                splash.remove()
                return@setOnExitAnimationListener
            }
            val location = IntArray(2)
            icon.getLocationInWindow(location)
            val geometry = LaunchIcon(location[0], location[1], icon.width, icon.height)
            val onboarding = composerModel.onboarding
            onboarding.icon.value = geometry
            lifecycleScope.launch {
                try {
                    // Keep the system artwork over the fallback frame until Compose draws
                    // the measured size/position. Never wait for feeds, relays or media.
                    withTimeoutOrNull(1_500) {
                        combine(onboarding.state, onboarding.allowed, geometry.drawn) { firstUse, allowed, drawn ->
                            !allowed || firstUse?.welcome == true || drawn
                        }.first { it }
                    }
                } finally {
                    // A draw acknowledgement can resume us during traversal; remove afterward.
                    window.decorView.post { splash.remove() }
                }
            }
        }
        receiveMedia(intent)
        receiveNostrLink(intent)
        setContent {
            FeatherTheme { CopyableMessageHost(); FeatherApp(window, composerModel); PreviousCrashDialog(composerModel); IssueComposer(composerModel); AccountReportSheet(composerModel) }
        }
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Handle these before TextFields turn Tab into focus traversal. Dialogs own a
        // separate window, so never navigate the reader while a modal has focus.
        val shortcut = when {
            event.isAltPressed -> null
            event.isCtrlPressed && !event.isMetaPressed && event.keyCode == android.view.KeyEvent.KEYCODE_TAB ->
                if (event.isShiftPressed) ReaderShortcut.PREVIOUS_TAB else ReaderShortcut.NEXT_TAB
            (event.isCtrlPressed || event.isMetaPressed) && !event.isShiftPressed && event.keyCode == android.view.KeyEvent.KEYCODE_F -> ReaderShortcut.SEARCH
            else -> null
        }
        val readerShortcut = onReaderShortcut
        if (shortcut != null && readerShortcut != null && window.decorView.hasWindowFocus()) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) readerShortcut(shortcut)
            return true
        }
        if ((event.isCtrlPressed || event.isMetaPressed) && event.keyCode == android.view.KeyEvent.KEYCODE_N) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (composerModel.signer.value == null) composerModel.message.value = "Connect an Android signer first."
                else if (composerModel.replyDraft.value == null) composerModel.openReply()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE))
            composerModel.onboarding.allowed.value = false
        receiveMedia(intent)
        receiveNostrLink(intent)
    }

    private fun receiveNostrLink(link: Intent) {
        if (link.action != Intent.ACTION_VIEW || link.data?.scheme != "nostr") return
        (application as FeatherApplication).audio.consumeHeart(link.getStringExtra("audio-heart"))?.let {
            composerModel.audioHeart.value = it
        }
        composerModel.receiveNostrLink(link.dataString.orEmpty())
        // Consume once: recreation must not reopen a link after navigating back.
        setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
    }

    private fun receiveMedia(shared: Intent) {
        // File managers may label videos application/octet-stream or mixed selections */*.
        // Accept URI attachments here; the background uploader validates the actual media.
        if (shared.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        try {
            val streams = if (shared.action == Intent.ACTION_SEND_MULTIPLE)
                IntentCompat.getParcelableArrayListExtra(shared, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else listOfNotNull(IntentCompat.getParcelableExtra(shared, Intent.EXTRA_STREAM, Uri::class.java))
            val clips = shared.clipData
            val uris = (streams + (0 until (clips?.itemCount ?: 0)).mapNotNull { clips?.getItemAt(it)?.uri })
                .distinct().filter { it.scheme == "content" }
            if (uris.isEmpty()) composerModel.message.value = "No readable image or video was shared."
            else composerModel.receiveSharedMedia(uris)
        } catch (_: Exception) {
            composerModel.message.value = "Could not read shared media."
        }
        // Do not replay uploads when this activity is recreated. Android retains the URI grants for the task.
        setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
    }

    override fun onStart() { super.onStart(); repo.foreground() }
    override fun onStop() { repo.background(); super.onStop() }
}

@Composable
fun FeatherApp(window: android.view.Window, vm: FeatherViewModel = viewModel()) {
    val connectedSigner by vm.signer.collectAsStateWithLifecycle()
    LaunchedEffect(connectedSigner) { vm.processSharedMedia() }
    val appState by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(appState.pendingMuteList?.id, connectedSigner) { vm.loadMutes(appState.pendingMuteList) }
    val signerScope = rememberCoroutineScope()
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { AppMessages.show(it); vm.message.compareAndSet(it, null) } }
    val reactionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null || data.getBooleanExtra("rejected", false))
            vm.cancelSignerRequest("Signer request cancelled.")
        else vm.signerResponse(data)
    }
    DisposableEffect(connectedSigner, reactionLauncher) {
        val signer = connectedSigner
        val launch: (Intent) -> Unit = { intent ->
            signerScope.launch {
                if (vm.signer.value === signer && vm.signerRequests.active) {
                    try { reactionLauncher.launch(intent) }
                    catch (_: Exception) { vm.cancelSignerRequest("Could not open your signer. Please try again.") }
                }
            }
        }
        signer?.registerForegroundLauncher(launch)
        onDispose { signer?.unregisterForegroundLauncher(launch) }
    }
    val audioHeart by vm.audioHeart.collectAsStateWithLifecycle()
    val activityLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(appState.settingsEvent?.id, connectedSigner, activityLifecycle) {
        vm.accountSettings.refresh(activityLifecycle.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(audioHeart, connectedSigner, activityLifecycle) {
        val request = audioHeart ?: return@LaunchedEffect
        if (!activityLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (vm.audioHeart.compareAndSet(request, null) && connectedSigner?.pubKey == request.second) {
            vm.react(request.first, ReactionChoice("🧡"))
        }
    }
    val navigation = rememberSaveable(saver = ReaderNavigation.saver) { ReaderNavigation() }
    val incomingLink by vm.incomingLink.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val shortcutHandler by rememberUpdatedState<(ReaderShortcut) -> Unit>(handler@{ action ->
        if (!activityLifecycle.isAtLeast(Lifecycle.State.RESUMED) || vm.replyDraft.value != null ||
            (vm.onboarding.allowed.value && vm.onboarding.state.value?.welcome != true)) return@handler
        val current = navigation.current
        if (action == ReaderShortcut.SEARCH) {
            focus.clearFocus()
            val searchHome = if (current.screen == "feed") current else ReaderEntry("feed", tab = ProfileTab.SEARCH).also {
                // Keep the previous reader on the back stack, but release its live subscriptions.
                vm.repo.openReference(null)
                vm.repo.viewHashtag(null)
                vm.repo.viewProfile(null)
                vm.repo.viewThread(null)
                navigation.push(it)
            }
            searchHome.toolbar = true
            searchHome.requestSearchFocus = true
            searchHome.requestedFeedTab = FeedTabRequest(ProfileTab.SEARCH)
        } else {
            val state = vm.state.value
            val tabs = when (current.screen) {
                "feed" -> current.tabs
                "hashtag" -> current.tabs
                "profile" -> if (state.profileTabsFor == current.target) state.profileTabs.toList() else current.tabs
                else -> return@handler
            }
            if (tabs.isEmpty()) return@handler
            val direction = if (action == ReaderShortcut.NEXT_TAB) 1 else -1
            val index = tabs.indexOf(current.requestedFeedTab?.tab ?: current.tab)
            val next = if (index < 0) { if (direction > 0) tabs.first() else tabs.last() }
                else tabs[(index + direction + tabs.size) % tabs.size]
            focus.clearFocus()
            current.toolbar = true
            current.requestSearchFocus = false
            if (current.screen == "feed") current.requestedFeedTab = FeedTabRequest(next) else current.tab = next
        }
    })
    DisposableEffect(context, navigation) {
        val activity = context as? MainActivity
        val handler: (ReaderShortcut) -> Unit = { shortcutHandler(it) }
        activity?.onReaderShortcut = handler
        onDispose { if (activity != null && activity.onReaderShortcut === handler) activity.onReaderShortcut = null }
    }
    LaunchedEffect(incomingLink) {
        val request = incomingLink ?: return@LaunchedEffect
        val ref = request.second
        val state = vm.state.value
        if (!ContentFilter(state.mutedAuthors, state.embedded, state.deletedReactions, state.showSensitive, state.deletedNotes, state.mutedWords).hides(ref)) {
            if (ref.external != null) {
                // Unsupported event/address types use HTTPS, never launch our own scheme again.
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://njump.to/${ref.external}"))) }
                catch (_: Exception) { vm.message.value = "No browser available to open this Nostr link." }
            } else {
                vm.closeReply()
                val destination = ReaderEntry(if (ref.profile) "profile" else "thread", ref.id)
                destination.reference = ref
                navigation.push(destination)
            }
        }
        vm.incomingLink.compareAndSet(request, null)
    }
    val entry = navigation.current
    BackHandler(entry.screen == "feed" && navigation.entries.size > 1) { navigation.back() }
    if (entry.screen in listOf("feed", "profile", "hashtag") && entry.tab == ProfileTab.PHOTOS) HidePhotoStatusBar(window)
    TranslationHost {
    CompositionLocalProvider(LocalOpenRepositoryIssues provides { address, relays ->
        if (issueRepositoryFilter(address) != null) navigation.push(ReaderEntry("repository", address).also {
            it.reference = NostrContent.Reference(address, kind = 30617, relays = relays)
        })
    }) {
    if (entry.screen == "repository") key(entry.key) { RepositoryIssuesScreen(vm, navigation, entry) }
    else if (entry.screen == "feed") HomeFeedPages(vm, navigation, entry)
    else key(entry.key) { FeatherReader(vm, navigation, entry) }
    ReplyComposer(vm)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FeatherReader(vm: FeatherViewModel, navigation: ReaderNavigation, entry: ReaderEntry, activePage: Boolean = true, homeToolbar: ReaderEntry? = null,
    accountRequested: Boolean = false, onAccountHandled: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val connectedSigner by vm.signer.collectAsStateWithLifecycle()
    val composingReply by vm.replyDraft.collectAsStateWithLifecycle()
    val reposting by vm.reposting.collectAsStateWithLifecycle()
    val reacting by vm.reacting.collectAsStateWithLifecycle()
    val optimisticReactions by vm.optimisticReactions.collectAsStateWithLifecycle()
    val zapping by vm.zapping.collectAsStateWithLifecycle()
    val zapCelebrations by vm.zapCelebrations.collectAsStateWithLifecycle()
    val walletConnected by vm.wallet.store.connected.collectAsStateWithLifecycle()
    val confirmedPayments by vm.wallet.store.confirmedPayments.collectAsStateWithLifecycle()
    val recentZapAmounts by vm.wallet.store.recentAmounts.collectAsStateWithLifecycle()
    val confirmedZaps = remember(confirmedPayments, s.publicKey) {
        confirmedPayments.values.filter { it.first == s.publicKey }.map { it.second }.toSet()
    }
    val decryptingMutes by vm.decryptingMutes.collectAsStateWithLifecycle()
    val contentFilter = remember(s.mutedAuthors, s.embedded, s.deletedReactions, s.showSensitive, s.deletedNotes, s.mutedWords) { ContentFilter(s.mutedAuthors, s.embedded, s.deletedReactions, s.showSensitive, s.deletedNotes, s.mutedWords) }
    val context = LocalContext.current
    val scrollScope = rememberCoroutineScope()
    val mediaStore = (context.applicationContext as FeatherApplication).media
    val screen = entry.screen
    val profileKey = entry.target.takeIf { screen == "profile" }
    val hashtag = entry.target.takeIf { screen == "hashtag" }
    val threadId = entry.target.takeIf { screen == "thread" }
    var openingInApp by remember(entry.key) { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showWallet by remember { mutableStateOf(false) }
    LaunchedEffect(connectedSigner) { showWallet = false }
    LaunchedEffect(accountRequested) {
        if (accountRequested) { showAccount = true; onAccountHandled() }
    }
    var statusReport by rememberSaveable { mutableStateOf<String?>(null) }
    var statusTitle by rememberSaveable { mutableStateOf("") }
    var profileTab by entry::tab
    val visibleProfileTabs = if (screen == "hashtag") {
        (entry.tabs + s.hashtagCategories.takeIf { s.hashtagFor == hashtag && s.hashtagAuthor == entry.hashtagAuthor }.orEmpty())
            .distinct().sortedBy { it.orderPriority }.also { entry.tabs = it }
    } else if (s.profileTabsFor == profileKey && profileKey != null) {
        // Discovery samples are bounded, not evidence that a previously found category is empty.
        // Keep this visit's tabs when returning after another profile replaced repository state.
        (listOf(ProfileTab.POSTS) + s.profileTabs + entry.tabs).distinct().sortedBy { it.profileOrderPriority }.also { entry.tabs = it }
    } else (listOf(ProfileTab.POSTS) + entry.tabs).distinct().sortedBy { it.profileOrderPriority }
    LaunchedEffect(entry.key, visibleProfileTabs) {
        if (screen in listOf("profile", "hashtag") && visibleProfileTabs.isNotEmpty() && profileTab !in visibleProfileTabs) profileTab = visibleProfileTabs.first()
    }
    var topBarVisible by (homeToolbar ?: entry)::toolbar
    val toolbarOwner = homeToolbar ?: entry
    var barFraction by toolbarOwner::toolbarFraction
    var toolbarHeight by remember(entry.key) { mutableIntStateOf(0) }
    var tabsHeight by remember(entry.key) { mutableIntStateOf(0) }
    val activeListState = if (screen == "hashtag") entry.hashtagList(profileTab) else entry.list
    val savedScreens = rememberSaveableStateHolder()
    var profileNameBottom by remember(entry.key) { mutableStateOf<Float?>(null) }
    var profileViewportTop by remember(entry.key) { mutableStateOf<Float?>(null) }
    val showProfileToolbarName by remember(entry.key, activeListState) {
        derivedStateOf {
            activeListState.firstVisibleItemIndex > 0 ||
                (profileNameBottom?.let { bottom -> profileViewportTop?.let { bottom <= it } } == true)
        }
    }
    val transparentToolbar = screen == "feed"
    val toolbarButtonSurface = Modifier.size(RoundControlSize).then(if (transparentToolbar) Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), CircleShape) else Modifier)
    fun selectProfileTab(tab: ProfileTab) {
        if (profileTab == tab) return
        profileTab = tab
    }
    fun activate(destination: ReaderEntry, clicked: Event? = null) {
        vm.repo.openReference(destination.reference)
        vm.repo.viewHashtag(destination.target.takeIf { destination.screen == "hashtag" }, destination.hashtagAuthor, destination.tab)
        vm.repo.viewProfile(destination.target.takeIf { destination.screen == "profile" })
        if (destination.screen == "profile") vm.repo.profileTab(destination.tab)
        if (destination.screen == "feed") vm.repo.feedTab(destination.tab)
        vm.repo.viewThread(destination.target.takeIf { destination.screen == "thread" },
            clicked ?: destination.notes.values.firstNotNullOfOrNull { notes -> notes.find { it.matchesThread(destination.target) } },
            destination.notes.values.flatten().takeIf { destination.screen == "thread" }.orEmpty(), destination.expandedThreadParents,
            destination.threadAncestorLimit)
    }
    fun goHome() {
        navigation.entries.forEach { savedScreens.removeState(it.key) }
        navigation.home(); activate(navigation.current)
    }
    fun goBack() {
        val removed = navigation.current
        navigation.back()
        savedScreens.removeState(removed.key)
        activate(navigation.current)
    }
    fun openReference(ref: NostrContent.Reference) {
        if (contentFilter.hides(ref)) return
        val resolved = s.embedded[ref.id]
        if (resolved != null && resolved.kind in readableKinds) {
            val destination = ReaderEntry("thread", resolved.id)
            destination.reference = NostrContent.Reference(resolved.id, author = resolved.pubKey, kind = resolved.kind, relays = ref.relays)
            destination.notes[ProfileTab.POSTS] = listOf(resolved)
            navigation.push(destination); activate(navigation.current, resolved)
            return
        }
        if (ref.external != null) {
            val url = MediaContent.secureUrl(ref.external) ?: "https://njump.to/${ref.external}"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); return
        }
        val destination = ReaderEntry(if (ref.hashtag != null) "hashtag" else if (ref.profile) "profile" else "thread", ref.hashtag ?: ref.id)
        destination.reference = ref.takeIf { ref.hashtag == null }
        destination.hashtagAuthor = ref.author.takeIf { ref.hashtag != null }
        navigation.push(destination); activate(navigation.current)
    }
    fun openProfile(key: String) = openReference(NostrContent.Reference(key, profile = true))
    fun openNote(note: Event) {
        if (contentFilter.hides(note)) return
        val destination = ReaderEntry("thread", note.id)
        destination.reference = NostrContent.Reference(note.id, profile = false)
        destination.notes[ProfileTab.POSTS] = listOf(note)
        navigation.push(destination); activate(navigation.current, note)
    }
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data
        if (it.resultCode == RESULT_OK && data != null) {
            val result = ExternalSignerLogin.parseResult(data)
            if (result is SignerResult.RequestAddressed.Successful) {
                if (vm.onboarding.allowed.value && vm.onboarding.state.value?.welcome == false) vm.onboarding.step.value = 1
                vm.login(result.result.pubkey, result.result.packageName); showAccount = false; goHome()
            } else vm.message.value = "Could not connect the signer. Please try again."
        }
    }
    LaunchedEffect(showAccount, s.publicKey) {
        if (showAccount && s.publicKey == null) {
            showAccount = false
            try { loginLauncher.launch(ExternalSignerLogin.createIntent(emptyList())) }
            catch (_: Exception) { vm.message.value = "Install an Android signer such as Amber first." }
        }
        if (s.publicKey == null) showSettings = false
    }
    LaunchedEffect(showSettings) {
        if (showSettings && s.publicKey != null) {
            vm.repo.refreshAccountData(force = true)
            vm.accountSettings.retry()
            vm.retryMutes()
        }
    }
    LaunchedEffect(s.pendingHashtagList?.id, connectedSigner, activePage) {
        if (activePage) vm.loadHashtags(s.pendingHashtagList)
    }
    LaunchedEffect(s.pendingMuteList?.id, connectedSigner, reacting, activePage) {
        if (activePage) vm.loadMutes(s.pendingMuteList)
    }
    LaunchedEffect(entry.key, activePage) { if (activePage) activate(entry) }
    LaunchedEffect(entry.key, profileTab, activePage) {
        if (activePage && screen == "profile") vm.repo.profileTab(profileTab)
        if (activePage && screen == "hashtag") vm.repo.viewHashtag(hashtag, entry.hashtagAuthor, profileTab)
    }
    statusReport?.let { StatusDetails(statusTitle, it) { statusReport = null } }
    BackHandler(activePage && navigation.entries.size > 1 && !showAccount && !showSettings) { goBack() }
    val pendingAvatarMutes by vm.pendingAccountMutes.collectAsStateWithLifecycle()
    val pendingAvatarUnmutes by vm.pendingAccountUnmutes.collectAsStateWithLifecycle()
    val avatarMutePending by rememberUpdatedState(pendingAvatarMutes + pendingAvatarUnmutes)
    val avatarMutedAccounts by rememberUpdatedState((s.mutedAuthors + pendingAvatarMutes) - pendingAvatarUnmutes)
    val avatarModeration = remember(vm, s.publicKey) {
        AvatarModeration(s.publicKey, vm::muteAccount, vm.accountReports::open,
            isMuted = { it in avatarMutedAccounts }, unmute = vm::unmuteAccount, mutePending = { it in avatarMutePending }, deleteNote = vm::deleteNote,
            followButton = { target, size -> ProfileFollowButton(vm, target, size) })
    }
    if (activePage) ProfileFollowConfirmation(vm)
    LaunchedEffect(activePage) { if (!activePage) avatarModeration.expanded = null }
    CompositionLocalProvider(LocalAvatarModeration provides avatarModeration, LocalPollActions provides PollActions(votersVisible = vm.repo::pollVotersVisible) { note, choices ->
        if (s.publicKey == null) showAccount = true else vm.vote(note, choices)
    }, LocalReply provides { note ->
        if (s.publicKey == null) showAccount = true else vm.openReply(note)
    }, LocalSelectionReply provides { note, text ->
        if (s.publicKey == null) showAccount = true else vm.openReply(note, selectedText = text)
    }, LocalRepostActions provides RepostActions(reposting,
        repost = { note -> if (s.publicKey == null) showAccount = true else vm.repost(note) },
        quote = { note -> if (s.publicKey == null) showAccount = true else vm.openReply(quote = note) }), LocalContentFilter provides contentFilter, LocalZapSplitProfile provides ::openProfile, LocalZapActions provides ZapActions(zapping, confirmedZaps, recentZapAmounts[s.publicKey].orEmpty().ifEmpty { defaultZapAmounts }, zapCelebrations) { note, amount, message ->
        if (s.publicKey == null) showAccount = true
        else if (!walletConnected) showWallet = true
        else vm.zap(note, amount, message)
    }, LocalReactionActions provides ReactionActions(pending = reacting, optimistic = optimisticReactions, choices = s.reactionChoices, recentEmojis = s.recentEmojis, customEmojis = s.customEmojis, remove = { note, choice -> vm.react(note, choice, remove = true) }) { note, choice ->
        if (s.publicKey == null) showAccount = true else vm.react(note, choice)
    }) {
    GalleryHost(state = s, walletConnected = walletConnected, covered = showAccount || showWallet || showSettings || composingReply != null, album = { if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PHOTOS) entry.photos else ProfilePhotos() },
        onOpenNote = { id, note -> if (note != null) openNote(note) else openReference(NostrContent.Reference(id)) },
        onOpenProfile = ::openProfile, onLogin = { showAccount = true }, onWallet = { showWallet = true }) {
    MediaHost(mediaStore, reading = activePage && !showSettings && !showAccount && composingReply == null) {
    savedScreens.SaveableStateProvider(entry.key) {
    val categoryTabs: @Composable () -> Unit = {
        if (visibleProfileTabs.isEmpty()) Spacer(Modifier.fillMaxWidth().height(48.dp))
        else {
            key(visibleProfileTabs) {
                val selectedIndex = visibleProfileTabs.indexOf(profileTab).coerceAtLeast(0)
                ScrollableTabRow(edgePadding = 0.dp, selectedTabIndex = selectedIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { positions ->
                        positions.getOrNull(selectedIndex)?.let { position ->
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(position))
                        }
                    }) {
                    visibleProfileTabs.forEach { tab ->
                        Tab(selected = profileTab == tab, onClick = {
                            if (profileTab == tab) scrollScope.launch {
                                if (activeListState.firstVisibleItemIndex > 20) activeListState.scrollToItem(0)
                                else activeListState.animateScrollToItem(0)
                                topBarVisible = true
                                barFraction = 1f
                            } else selectProfileTab(tab)
                        }, icon = {
                            FeedCategoryIcon(tab)
                        })
                    }
                }
            }
        }
    }
    val detailNote = if (screen == "thread") {
        (s.threadNotes.firstOrNull { it.matchesThread(threadId) }
            ?: entry.notes[profileTab]?.firstOrNull { it.matchesThread(threadId) })
            ?.takeUnless { contentFilter.hides(it) || it.id in s.conversation?.deleted.orEmpty() }
    } else null
    Scaffold(
        modifier = Modifier.dismissAvatarModeration(avatarModeration),
        topBar = {
            if (homeToolbar == null) {
            Box(Modifier.scrollAway(barFraction) { toolbarHeight = it }) {
            Column {
            TopAppBar(title = {
                if (detailNote?.kind == 30023) {
                    val articleTitle = remember(detailNote.id) {
                        detailNote.tagValue("title")?.withoutObjectMarkers()?.trim().orEmpty()
                    }
                    if (articleTitle.isNotEmpty()) Text(articleTitle,
                        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.ui_scroll_feed_to_top)) {
                            scrollScope.launch {
                                if (activeListState.firstVisibleItemIndex > 20) activeListState.scrollToItem(0)
                                else activeListState.animateScrollToItem(0)
                                topBarVisible = true
                                barFraction = 1f
                            }
                        }.heightIn(min = 48.dp).wrapContentHeight(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                }
                if (screen == "profile" && profileKey != null && showProfileToolbarName) {
                    Text(s.profiles[profileKey]?.name ?: FeatherRepository.shortKey(profileKey),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                }
                if (screen == "hashtag") {
                    val author = entry.hashtagAuthor
                    if (author == null) {
                        Text("#${hashtag.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    } else {
                        val profile = s.profiles[author]
                        val name = profile?.name ?: FeatherRepository.shortKey(author)
                        Text(stringResource(R.string.ui_hashtag_by_author, hashtag.orEmpty(), name),
                            modifier = Modifier.clickable { openProfile(author) },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (screen == "feed") {
                    IconButton(onClick = {
                        scrollScope.launch {
                            // Avoid traversing a long feed and loading intermediate media.
                            if (activeListState.firstVisibleItemIndex > 20) activeListState.scrollToItem(0)
                            else activeListState.animateScrollToItem(0)
                            entry.toolbar = true
                        }
                    }, modifier = Modifier.size(48.dp).then(toolbarButtonSurface)) {
                        Icon(painterResource(R.drawable.feather), stringResource(R.string.ui_scroll_feed_to_top), Modifier.size(32.dp), tint = Color.Unspecified)
                    }
                }
            }, navigationIcon = {
                if (screen != "feed") IconButton(onClick = { goBack() }, modifier = toolbarButtonSurface) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back)) }
            }, actions = {
                if (screen == "profile" && profileKey != null) {
                    val pointer = Protocol.npub(profileKey)
                    IconButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://njump.to/$pointer")
                        }, context.getString(R.string.ui_share_profile)))
                    }, modifier = toolbarButtonSurface) { Icon(Icons.Default.Share, stringResource(R.string.ui_share_profile)) }
                    ReferenceOverflow(
                        identity = pointer, modifier = toolbarButtonSurface,
                        copyDescription = stringResource(R.string.ui_copy_profile_public_key),
                        openDescription = stringResource(R.string.ui_open_profile_in_another_nostr_app),
                        opening = openingInApp,
                        onCopy = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nostr public key", pointer))
                        },
                        onOpen = {
                            openingInApp = true
                            scrollScope.launch {
                                try { context.openInNostrApp(pointer) }
                                finally { openingInApp = false }
                            }
                        })
                }
                if (screen == "hashtag") HashtagFollowButton(vm, hashtag.orEmpty()) { showAccount = true }
                if (detailNote != null) IconButton(onClick = {
                    scrollScope.launch {
                        val pointer = context.nostrPointer(detailNote)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, if (detailNote.kind == 1621) "https://gitworkshop.dev/$pointer" else "https://njump.to/$pointer")
                        }, "Share note"))
                    }
                }, modifier = toolbarButtonSurface) { Icon(Icons.Default.Share, stringResource(R.string.ui_share_note)) }
                val translations = LocalTranslations.current
                if (detailNote != null) ReferenceOverflow(
                    identity = detailNote.id, modifier = toolbarButtonSurface,
                    onTranslate = if (detailNote.kind in listOf(1, 1111, 1244, 1222, 30023, 1621) && detailNote.content.isNotBlank())
                        ({ translations?.toggle(detailNote) }) else null,
                    translationEnabled = translations != null && translations.pending == null,
                    showingTranslation = translations?.text(detailNote) != null,
                    copyDescription = stringResource(R.string.ui_copy_nostr_reference),
                    openDescription = stringResource(R.string.ui_open_note_in_another_nostr_app),
                    opening = openingInApp,
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        scrollScope.launch { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nostr reference", "nostr:${context.nostrPointer(detailNote)}")) }
                    },
                    onOpen = {
                        openingInApp = true
                        scrollScope.launch {
                            try { context.openInNostrApp(detailNote) }
                            finally { openingInApp = false }
                        }
                    })
                if (screen == "profile" && s.publicKey != null && profileKey == s.publicKey) IconButton(onClick = { showSettings = true }, modifier = toolbarButtonSurface) { Icon(Icons.Default.Settings, stringResource(R.string.ui_settings)) }
                if (screen == "feed") {
                    IconButton(onClick = { s.publicKey?.let { openProfile(it) } ?: run { showAccount = true } }, modifier = (if (s.publicKey != null) Modifier.size(RoundControlSize) else toolbarButtonSurface).semantics { contentDescription = "Account" }) {
                        val account = s.profiles[s.publicKey]
                        if (s.publicKey != null) Avatar(account?.name ?: FeatherRepository.shortKey(s.publicKey!!), ROUND_CONTROL_DP, account?.picture)
                        else Icon(Icons.Default.Person, null, Modifier.size(22.dp))
                    }
                }
            }, expandedHeight = 64.dp, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (transparentToolbar) Color.Transparent else MaterialTheme.colorScheme.surface,
                actionIconContentColor = MaterialTheme.colorScheme.primary, navigationIconContentColor = MaterialTheme.colorScheme.primary))
            if (screen == "hashtag") categoryTabs()
            }
            }
            }
        },
        floatingActionButton = {
            if (screen == "profile" && profileKey != null) {
                val opacity by androidx.compose.animation.core.animateFloatAsState(
                    if (activeListState.isScrollInProgress) 0.4f else 1f, tween(180), label = "Profile feather scroll opacity")
                FeatherComposeButton(onClick = {
                    if (s.publicKey == null) showAccount = true
                    else vm.openReply(mentionProfile = profileKey.takeUnless { it == s.publicKey }, picturePost = profileTab == ProfileTab.PHOTOS)
                }, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp).graphicsLayer {
                    alpha = opacity
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                }, showShadow = !activeListState.isScrollInProgress && opacity == 1f, picturePost = profileTab == ProfileTab.PHOTOS,
                    description = if (profileKey == s.publicKey) "Create post" else "Create post mentioning ${s.profiles[profileKey]?.name ?: FeatherRepository.shortKey(profileKey)}")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (screen) {
            else -> {
                // A new input token is pending immediately, before the background effect starts.
                val preparation = remember(entry.key, s.notes, s.feedTab, s.feedPreviews, s.profileEvents, s.hashtagNotes, s.hashtagFor, s.hashtagAuthor, s.hashtagTab, hashtag, screen, profileKey, threadId, profileTab, s.publicKey, s.following, s.followedHashtags, contentFilter) { Any() }
                val preparedNotes by key(profileTab) { produceState<Pair<Any?, List<Event>>>(null to entry.notes[profileTab].orEmpty(), preparation) {
                    val retained = entry.notes[profileTab].orEmpty().filterNot(contentFilter::hides)
                    value = preparation to withContext(Dispatchers.Default) {
                    val filtered = when (screen) {
                    "hashtag" -> latestProfileEvents(
                        (s.hashtagNotes.takeIf { s.hashtagFor == hashtag && s.hashtagAuthor == entry.hashtagAuthor && s.hashtagTab == profileTab }.orEmpty() + retained)
                            .filter { it.matchesHashtagFeed(hashtag.orEmpty(), entry.hashtagAuthor, profileTab) }).take(600)
                    "profile" -> latestProfileEvents(s.profileEvents.filter { it.involves(profileKey) } + retained).filter(profileTab::matches).take(600)
                    "thread" -> emptyList()
                    else -> latestProfileEvents((if (s.feedTab == profileTab) s.notes else s.feedPreviews[profileTab].orEmpty()) + retained)
                        .filter { profileTab.matchesFeed(it) && (if (profileTab in listOf(ProfileTab.HASHTAGS, ProfileTab.RECIPES)) true else (s.publicKey == null || (profileTab == ProfileTab.POSTS && it.matchesHashtags(s.followedHashtags)) || it.involves(s.publicKey) || it.pubKey in s.following || (it.kind == 9321 && it.tagValue("p") in s.following) || (it.kind == 9735 && (it.tagValue("p") in s.following || it.tagValue("P") in s.following)))) }.take(600)
                    }
                    filtered
                    }
                } }
                // The selected note is handed off immediately; background filtering never blanks it.
                val notes = if (screen == "thread") {
                    // StateFlow already holds the handoff even if lifecycle collection resumes next frame.
                    val current = vm.repo.state.value
                    (current.threadNotes.takeIf { current.threadId == threadId } ?: entry.notes[profileTab]).orEmpty()
                } else preparedNotes.second
                // Re-check retained/handoff state synchronously when a list changes. These are
                // bounded in-memory key checks; no parsing, disk, or network work on main.
                val visibleNotes = remember(notes, contentFilter) { notes.filterNot(contentFilter::hides) }
                SideEffect { entry.notes[profileTab] = visibleNotes }
                val topicPage = screen in listOf("feed", "profile", "hashtag") && profileTab == ProfileTab.HASHTAGS
                val topics by produceState<List<String>>(entry.topics, topicPage, visibleNotes, screen) {
                    if (!topicPage) return@produceState
                    value = withContext(Dispatchers.Default) {
                        rankedHashtags(visibleNotes, if (screen == "feed") System.currentTimeMillis() / 1000 - 24 * 60 * 60 else null)
                            .filterNot { screen == "hashtag" && it.equals(hashtag, ignoreCase = true) }
                    }
                }
                SideEffect { if (topicPage) entry.topics = topics }
                val threadView = if (screen == "thread") (s.conversation?.takeIf { it.target == threadId } ?: entry.conversation) else null
                SideEffect { if (threadView != null) entry.conversation = threadView }
                val groupFeed = screen in listOf("feed", "profile") && profileTab == ProfileTab.POSTS
                val groupedRows by key(profileTab) { produceState(if (groupFeed) entry.homeRows else visibleNotes.map { ConversationRow(it.id, it) }, visibleNotes, s.notes, s.profileEvents, s.embedded, contentFilter, groupFeed, entry.homeRefresh, s.loading) {
                    value = withContext(Dispatchers.Default) {
                        if (groupFeed)
                            homeConversations(visibleNotes, s.notes + s.profileEvents + s.embedded.values, contentFilter,
                                if (entry.homeRefresh > 0 && s.loading) emptyList() else entry.homeOrder)
                        else if (profileTab == ProfileTab.REPOSTS && screen in listOf("feed", "profile", "hashtag")) {
                            visibleNotes.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
                                .mapNotNull { repost -> repostTarget(repost)?.let { it.id to repost } }
                                .groupBy({ it.first }, { it.second })
                                .map { (target, reposts) ->
                                    ConversationRow(target, reposts.first(), reposts = reposts.distinctBy { it.pubKey })
                                }
                        } else visibleNotes.map { ConversationRow(it.id, it) }
                    }
                    if (groupFeed) {
                        entry.homeRows = value
                        entry.homeOrder = value.map { it.key }.distinct().take(1800)
                        if (!s.loading) entry.homeRefresh = 0
                    }
                } }
                val displayRows = if (screen == "thread") threadView?.rows ?: visibleNotes.firstOrNull { it.matchesThread(threadId) }
                    ?.let { listOf(ConversationRow(it.id, it, focused = true)) }.orEmpty()
                    else groupedRows
                val displayedEvents = displayRows.mapNotNull { it.event }.filterNot(contentFilter::hides)
                val threadViewport = entry.threadViewport.takeIf { screen == "thread" }
                SideEffect { threadViewport?.update(entry, displayRows, threadView != null) }
                LaunchedEffect(threadViewport) { threadViewport?.observe(entry) }
                val preparing = screen != "thread" && preparedNotes.first !== preparation
                val waitingForPage = screen == "feed" && (!activePage || s.feedTab != profileTab)
                val pendingContent = preparing || waitingForPage || s.loading
                // Relay completion can precede the sampled cache snapshot or another relay's
                // response. Brief gaps keep a spinner; existing content is never delayed.
                val emptySettled by produceState(false, entry.key, profileTab, pendingContent, visibleNotes.isEmpty()) {
                    value = false
                    if (!pendingContent && visibleNotes.isEmpty()) {
                        kotlinx.coroutines.delay(1_500)
                        value = true
                    }
                }
                val emptyLoading = visibleNotes.isEmpty() && (pendingContent || !emptySettled)
                val hiddenContent = visibleNotes.isEmpty() && (notes.isNotEmpty() || profileKey in s.mutedAuthors ||
                    entry.reference?.let(contentFilter::hides) == true)
                val toolbarScroll = remember(screen, activeListState, toolbarOwner) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (homeToolbar != null) return Offset.Zero
                            if (available.y < 0 && !activeListState.canScrollBackward) return Offset.Zero
                            return Offset(0f, toolbarOwner.scrollToolbar(available.y, toolbarHeight + tabsHeight))
                        }
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (homeToolbar != null) {
                                // Floating controls follow content without consuming its movement.
                                toolbarOwner.scrollToolbar(consumed.y)
                                if (!activeListState.canScrollBackward && available.y > 0) toolbarOwner.toolbar = true
                            }
                            return Offset.Zero
                        }
                    }
                }
                val playback = LocalMedia.current.playback
                val widthPx = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp - 48).dp.roundToPx() }
                val preparedBookmarks by produceState<List<Pair<Event, NostrContent.Reference>>>(if (profileTab == ProfileTab.PEOPLE) entry.people else entry.bookmarks, visibleNotes, profileTab) {
                    value = withContext(Dispatchers.Default) {
                        if (screen in listOf("profile", "feed", "hashtag") && profileTab in listOf(ProfileTab.BOOKMARKS, ProfileTab.PEOPLE))
                            visibleNotes.asSequence().flatMap { collection -> (if (profileTab == ProfileTab.PEOPLE) peopleReferences(collection) else bookmarkReferences(collection)).asSequence().map { collection to it } }.take(5000).toList()
                        else emptyList()
                    }
                    if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.BOOKMARKS) entry.bookmarks = value
                    if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PEOPLE) entry.people = value
                }
                val bookmarkRows = remember(preparedBookmarks, contentFilter) {
                    preparedBookmarks.filterNot { contentFilter.hides(it.first) || contentFilter.hides(it.second) }
                }
                val preparedPhotos by produceState(entry.photos, visibleNotes, profileTab) {
                    value = withContext(Dispatchers.Default) {
                        if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PHOTOS) {
                            val photos = visibleNotes.asSequence().flatMap { note -> MediaContent.attachments(note, 32).filter { it.kind == MediaContent.Kind.IMAGE }.map { note to it } }.take(600).toList()
                            ProfilePhotos(photos.chunked(3), photos.map { it.second.url }, photos.map { it.first })
                        } else ProfilePhotos()
                    }
                    if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PHOTOS) entry.photos = value
                }
                val photoGrid = remember(preparedPhotos, contentFilter) {
                    val photos = preparedPhotos.rows.flatten().filterNot { contentFilter.hides(it.first) }
                    ProfilePhotos(photos.chunked(3), photos.map { it.second.url }, photos.map { it.first })
                }
                SideEffect { if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PHOTOS) entry.photos = photoGrid }
                val photoRows = photoGrid.rows
                val currentPhotos by rememberUpdatedState(photoRows)
                val repostKeys = remember(displayRows) { displayRows.filter { it.reposts.isNotEmpty() }.associate { it.key to (listOfNotNull(it.event?.id) + it.reposts.map { repost -> repost.id }).distinct() } }
                val currentRepostKeys by rememberUpdatedState(repostKeys)
                val mediaKeys = remember(photoRows, bookmarkRows, s.embedded, repostKeys) {
                    photoRows.associate { row -> "photo:${row.first().first.id}:${row.first().second.url}" to row.map { it.first.id } } +
                        bookmarkRows.associate { "${it.first.id}:${it.second.id}" to listOf(s.embedded[it.second.id]?.id ?: it.second.id) } + repostKeys
                }
                val mediaNotes = if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.BOOKMARKS)
                    bookmarkRows.mapNotNull { s.embedded[it.second.id] }.filterNot(contentFilter::hides).distinctBy { it.id } else if (screen == "thread" || groupFeed) displayedEvents else visibleNotes
                val mediaEmbeds = remember(s.embedded, contentFilter) { s.embedded.filterValues { !contentFilter.hides(it) } }
                if (!topicPage) PrefetchMedia(mediaNotes, s.profiles, activeListState, widthPx, mediaEmbeds, mediaKeys)
                val currentBookmarks by rememberUpdatedState(bookmarkRows)
                val currentNotes by rememberUpdatedState(if (screen == "thread" || groupFeed) displayedEvents else visibleNotes)
                LaunchedEffect(activeListState, screen, activePage, topicPage) {
                    if (!activePage) return@LaunchedEffect
                    if (topicPage) { vm.repo.readingNotes(emptyList()); return@LaunchedEffect }
                    snapshotFlow { activeListState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
                        .distinctUntilChanged().collect { visibleKeys ->
                            val keys = visibleKeys.flatMap { currentRepostKeys[it] ?: listOf(it) }
                            val last = currentNotes.indexOfLast { it.id in keys }
                            val first = currentNotes.indexOfFirst { it.id in keys }
                            val nearby = if (last >= 0) currentNotes.drop(last + 1).take(6) + currentNotes.take(first).takeLast(2) else emptyList()
                            val bookmarks = currentBookmarks
                            if (bookmarks.isNotEmpty()) {
                                val visibleBookmarks = bookmarks.filter { "${it.first.id}:${it.second.id}" in keys }
                                val lastBookmark = bookmarks.indexOfLast { "${it.first.id}:${it.second.id}" in keys }
                                vm.repo.readingNotes((visibleBookmarks + bookmarks.drop(lastBookmark + 1).take(6)).map { it.second.id })
                            } else if (currentPhotos.isNotEmpty()) {
                                val photos = currentPhotos
                                val lastPhoto = photos.indexOfLast { "photo:${it.first().first.id}:${it.first().second.url}" in keys }
                                vm.repo.readingNotes(photos.filter { "photo:${it.first().first.id}:${it.first().second.url}" in keys }
                                    .plus(photos.drop(lastPhoto + 1).take(2)).flatten().map { it.first.id })
                            } else vm.repo.readingNotes(keys + nearby.map { it.id })
                        }
                }
                var requestedBoundary by remember(screen, profileKey, profileTab, hashtag) { mutableStateOf<String?>(null) }
                var emptyPages by remember(screen, profileKey, profileTab, hashtag) { mutableIntStateOf(0) }
                val rawHistory by rememberUpdatedState(when (screen) {
                    "profile" -> s.profileEvents
                    "hashtag" -> s.hashtagNotes.takeIf { s.hashtagFor == hashtag && s.hashtagAuthor == entry.hashtagAuthor && s.hashtagTab == profileTab }.orEmpty()
                    else -> s.notes.takeIf { s.feedTab == profileTab }.orEmpty()
                })
                val pagingBusy by rememberUpdatedState(s.loading || s.loadingOlder)
                val mediaActive by rememberUpdatedState(LocalMedia.current.active)
                LaunchedEffect(activeListState, screen, profileKey, profileTab, hashtag, activePage) {
                    if (!activePage || screen == "thread") return@LaunchedEffect
                    snapshotFlow {
                        val layout = activeListState.layoutInfo
                        val nearEnd = layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 7 } == true
                        Triple(nearEnd, rawHistory.lastOrNull()?.id, !pagingBusy && mediaActive)
                    }.distinctUntilChanged().collect { (nearEnd, boundary, ready) ->
                        if (!nearEnd) { requestedBoundary = null; emptyPages = 0 }
                        if (nearEnd && ready && boundary != null && boundary != requestedBoundary && (currentNotes.isNotEmpty() || emptyPages < 3)) {
                            if (vm.repo.older(profileKey.takeIf { screen == "profile" })) {
                                requestedBoundary = boundary
                                if (currentNotes.isEmpty()) emptyPages++ else emptyPages = 0
                            }
                        }
                    }
                }
                var refreshing by remember(screen) { mutableStateOf(false) }
                LaunchedEffect(s.loading) { if (!s.loading) refreshing = false }
                val refresh = {
                    if (activePage && !s.loading) { entry.homeOrder = emptyList(); entry.homeRefresh++; requestedBoundary = null; emptyPages = 0; refreshing = true; vm.repo.refresh(); vm.retryMutes(); vm.retryHashtags() }
                }
                var handledRefresh by remember(entry.key) { mutableIntStateOf(entry.refreshRequested) }
                LaunchedEffect(entry.refreshRequested) {
                    if (entry.refreshRequested != handledRefresh) {
                        handledRefresh = entry.refreshRequested
                        refresh()
                    }
                }
                val initialViewport = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
                var profileViewport by remember(entry.key) { mutableIntStateOf(initialViewport) }
                val tabSpace = if (screen == "profile") remember(entry.key, profileTab, visibleNotes.isEmpty()) { ProfileTabSpace() } else null
                val tabMinimum = with(LocalDensity.current) {
                    (profileViewport - (tabSpace?.measured ?: 0)).coerceAtLeast(0).toDp()
                }
                PullToRefreshBox(
                    isRefreshing = refreshing && s.loading,
                    onRefresh = refresh,
                    modifier = Modifier.padding(
                        if (screen == "feed") PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                            start = padding.calculateStartPadding(LocalLayoutDirection.current),
                            end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        ) else padding
                    ).fillMaxSize().nestedScroll(toolbarScroll)
                        .profileTabSwipes(screen in listOf("profile", "hashtag"), visibleProfileTabs, profileTab, ::selectProfileTab).semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Refresh") { if (s.loading) false else { refresh(); true } },
                            CustomAccessibilityAction("Show toolbar") { topBarVisible = true; barFraction = 1f; true },
                        )
                    },
                ) {
                    LazyColumn(Modifier.fillMaxSize().then(threadViewport?.viewportModifier() ?: Modifier).onSizeChanged { profileViewport = it.height }.onGloballyPositioned {
                        playback?.viewport = it.boundsInWindow()
                        if (screen == "profile") profileViewportTop = it.positionInWindow().y
                    }, state = activeListState, contentPadding = PaddingValues(top = if (screen == "feed" && homeToolbar == null) 64.dp else 0.dp, bottom = 24.dp + if (homeToolbar != null) 136.dp else 0.dp)) {
                        if (screen == "profile") item("profile-header") {
                            val key = profileKey!!
                            val p = s.profiles[key]
                            val profileModerationKey = remember(key) { Any() }
                            val openGallery = LocalOpenGallery.current
                            val profileImages = remember(p?.picture, p?.banner) { listOfNotNull(p?.picture, p?.banner) }
                            Box(Modifier.fillMaxWidth()) {
                                p?.banner?.let { banner ->
                                    Box(Modifier.fillMaxWidth().height(176.dp)
                                        .clickable(onClickLabel = "Open profile banner") { openGallery(profileImages, profileImages.lastIndex, expandAlbum = false) }) {
                                        RemoteImage(banner, Modifier.fillMaxSize(), "Profile banner")
                                        val surface = MaterialTheme.colorScheme.surface
                                        Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                                            .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))))
                                    }
                                }
                                // The 56dp avatar straddles the banner edge; content keeps its natural height.
                                Column(Modifier.fillMaxWidth().padding(top = if (p?.banner != null) 148.dp else 0.dp)) {
                                Column(Modifier.padding(start = 24.dp, end = 24.dp,
                                    top = if (p?.banner != null) 0.dp else 24.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        ModerationAvatar(key, p?.name ?: FeatherRepository.shortKey(key), p?.picture, size = 56, slotSize = 56, expansionKey = profileModerationKey, profileToggle = true,
                                            onClick = { if (profileImages.isNotEmpty()) openGallery(profileImages, 0, expandAlbum = false) })
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(p?.name ?: FeatherRepository.shortKey(key),
                                                modifier = Modifier.avatarModerationName(key, profileModerationKey) {}.onGloballyPositioned {
                                                    profileNameBottom = it.positionInWindow().y + it.size.height
                                                }, style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (!p?.nip05.isNullOrBlank()) Nip05Identity(p!!.nip05!!, key)
                                            else p?.username?.takeIf { it != p.name }?.let {
                                                Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        ProfileMutedIndicator(vm, key) { avatarModeration.expanded = profileModerationKey }
                                        if (avatarModeration.expanded !== profileModerationKey) {
                                        ProfileFollowButton(vm, key)
                                        p?.website?.let { website ->
                                            IconButton(onClick = { context.openWebLink(website) }) {
                                                Icon(Icons.Default.Home, stringResource(R.string.ui_open_profile_homepage), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        p?.lightning?.trim()?.takeIf { '@' in it }?.let { address ->
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lightning address", address))
                                            }) { Icon(painterResource(R.drawable.zap), stringResource(R.string.ui_copy_lightning_address), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                    }
                                    if (!p?.about.isNullOrBlank()) ProfileBiography(key, p, s, ::openReference)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 24.dp))
                                }
                            }
                        }
                        if (screen == "profile") stickyHeader(key = "profile-tabs") {
                            Box(Modifier.scrollAway(barFraction) { tabsHeight = it }) {
                                categoryTabs()
                            }
                        }
                        if (s.error == null && !hiddenContent && !refreshing && (emptyLoading || (visibleNotes.isEmpty() && activePage && s.loading)) && (screen != "thread" || displayRows.isEmpty())) item("progress") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        if (((activePage && s.error != null) || (visibleNotes.isEmpty() && !emptyLoading && (hiddenContent || screen == "thread"))) && (screen != "thread" || displayRows.isEmpty())) item("status") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                val title = when {
                                    activePage && s.error != null -> "Could not load notes"
                                    hiddenContent -> "Notes hidden"
                                    screen == "thread" -> "Post unavailable"
                                    else -> "No notes yet"
                                }
                                StatusInfo(title, error = activePage && s.error != null, hidden = hiddenContent) {
                                    statusTitle = title
                                    statusReport = readerStatusDetails(if (activePage) s else s.copy(error = null), screen, entry.target, profileTab,
                                        hidden = hiddenContent)
                                }
                            }
                        }
                        if (topicPage) {
                            items(topics, key = { "topic:$it" }) { tag ->
                                ProfileTabRow(tabSpace, "topic:$tag", profileViewport) {
                                    Text("#$tag", Modifier.fillMaxWidth()
                                        .clickable { openReference(NostrContent.Reference(tag, hashtag = tag, author = if (screen == "hashtag") entry.hashtagAuthor else profileKey.takeIf { screen == "profile" })) }
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PHOTOS) {
                            itemsIndexed(photoRows, key = { _, row -> "photo:${row.first().first.id}:${row.first().second.url}" }) { rowIndex, row ->
                                val openGallery = LocalOpenGallery.current
                                ProfileTabRow(tabSpace, "photo:${row.first().first.id}:${row.first().second.url}", profileViewport) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        row.forEachIndexed { columnIndex, (_, photo) ->
                                            RemoteImage(photo.url, Modifier.weight(1f).aspectRatio(1f).clickable(onClickLabel = "Open photo") {
                                                openGallery(photoGrid.urls, rowIndex * 3 + columnIndex)
                                            }, photo.alt ?: "Photo")
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        } else if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.PEOPLE) {
                            itemsIndexed(bookmarkRows, key = { _, row -> "${row.first.id}:${row.second.id}" }) { index, (collection, reference) ->
                                ProfileTabRow(tabSpace, "${collection.id}:${reference.id}", profileViewport) {
                                    if (index == 0 || bookmarkRows[index - 1].first.id != collection.id) {
                                        collection.tagValue("title")?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall) }
                                    }
                                    val person = s.profiles[reference.id]
                                    val name = person?.name ?: FeatherRepository.shortKey(reference.id)
                                    ProfileListRow(reference.id, name, person?.picture, person?.nip05,
                                        reference.id in s.followedAccounts) { openReference(reference) }
                                }
                            }
                        } else if (screen in listOf("profile", "feed", "hashtag") && profileTab == ProfileTab.BOOKMARKS) {
                            items(bookmarkRows, key = { "${it.first.id}:${it.second.id}" }) { (collection, reference) ->
                                ProfileTabRow(tabSpace, "${collection.id}:${reference.id}", profileViewport) {
                                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        collection.tagValue("title")?.takeIf { it.isNotBlank() }?.let { title ->
                                            Text(title, style = MaterialTheme.typography.labelMedium)
                                        }
                                        EmbeddedNote(reference, s.embedded[reference.id], s.profiles, "bookmark:${collection.id}:${reference.id}", ::openReference)
                                    }
                                }
                            }
                        } else itemsIndexed(displayRows, key = { _, row -> row.key }) { rowIndex, row ->
                            val note = row.event
                            // The graph's preceding connected event is the immediate parent. A gap,
                            // hidden event or deletion is not a rendered parent, even after loading ends.
                            val parentRendered = row.above && displayRows.getOrNull(rowIndex - 1)?.event?.let {
                                !contentFilter.hides(it) && it.id !in threadView?.deleted.orEmpty()
                            } == true
                            Column(threadViewport?.rowModifier(row.key) ?: Modifier) {
                                ProfileTabRow(tabSpace, row.key, profileViewport) {
                                    if (note == null) {
                                        ConversationGap(row, profiles = s.profiles, onProfile = ::openProfile, onOpen = { reference -> openReference(reference) },
                                            onInfo = { title, detail -> statusTitle = title; statusReport = detail },
                                            onMore = (row.deletedCopy ?: row.mutedCopy)?.takeIf { screen == "thread" && row.more }?.let { cached -> ({
                                                entry.expandedThreadParents = (entry.expandedThreadParents + cached.id).distinct().takeLast(64)
                                                vm.repo.expandThread(cached.id)
                                            }) },
                                            loading = row.gap in listOf(ThreadGap.MISSING, ThreadGap.LIMIT) && row.reference != null &&
                                                s.threadId == threadId && s.threadContextLoading && row.reference.id == s.threadLoadingParent,
                                            onLoadParent = if (screen == "thread") ({
                                                if (row.gap == ThreadGap.LIMIT && entry.threadAncestorLimit >= MAX_THREAD_ANCESTORS) {
                                                    statusTitle = "Earlier conversation"
                                                    statusReport = "This view has reached its limit of $MAX_THREAD_ANCESTORS earlier notes."
                                                } else vm.repo.loadThreadParent(row)?.let { entry.threadAncestorLimit = it }
                                            }) else null)
                                    } else if (!contentFilter.hides(note) && note.id !in threadView?.deleted.orEmpty()) {
                                    if (note.kind !in listOf(6, 16) && row.reposts.isNotEmpty()) {
                                        val reposters = row.reposts.filterNot(contentFilter::hides)
                                        if (reposters.isNotEmpty()) Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                                            RepostAttribution(reposters, s, ::openProfile)
                                        }
                                    }
                                    if (note.kind == 30617) ProjectCard(note)
                                    else if (note.kind == 32267 && screen != "thread") SoftwareAppCard(note) { openNote(note) }
                                    else if (note.kind in listOf(6, 16)) RepostRow(note, s, ::openReference, ::openProfile, row.reposts) { original ->
                                        openNote(original)
                                    } else NoteRow(note, s, onReference = ::openReference, onProfile = { openProfile(note.pubKey) }, onOpen = { if (note.kind == 7) reactionTarget(note)?.let(::openReference) else { openNote(note) } },
                                        showHashtagMatch = screen == "feed" && profileTab == ProfileTab.POSTS && note.matchesHashtags(s.followedHashtags),
                                        compact = !row.focused, threadRow = row.takeIf { screen == "thread" || row.above || row.below },
                                        showParent = if (screen == "thread") row.focused && !parentRendered else !row.above,
                                        parentLoading = screen == "thread" && row.focused && s.threadId == threadId && s.threadContextLoading,
                                        onMore = if (screen == "thread") ({
                                            entry.expandedThreadParents = (entry.expandedThreadParents + note.id).distinct().takeLast(64)
                                            vm.repo.expandThread(note.id)
                                        }) else null)
                                    }
                                }
                            }
                        }
                        if (threadViewport != null && displayRows.isNotEmpty()) item("thread-end-space") { threadViewport.EndSpace() }
                        if (screen == "profile") item("profile-tab-minimum") {
                            Spacer(Modifier.fillMaxWidth().height(tabMinimum))
                        }
                        if (s.loadingOlder && screen != "thread") item("older-progress") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    } // Reaction actions.
    } // Per-visit saved UI state.
    if (showWallet && connectedSigner != null) WalletSheet(vm) { showWallet = false }
    if (showSettings && s.publicKey != null) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CopyableMessageHost()
            Settings(vm, s, onChange = { sensitive, pow -> vm.changeSettings(sensitive, pow) }, onWallet = { showSettings = false; showWallet = true }, onLogout = { vm.logout(); showSettings = false; showAccount = false; goHome() },
                onIssue = { type -> showSettings = false; vm.issues.open(type) },
                onIssues = {
                    showSettings = false
                    navigation.push(ReaderEntry("repository", IssueReports.REPOSITORY).also {
                        it.reference = NostrContent.Reference(IssueReports.REPOSITORY, kind = 30617, relays = IssueReports.RELAYS)
                    })
                },
                onFeather = {
                    showSettings = false; showAccount = false
                    Protocol.publicKey("npub1r7u5wcxpv0fknn486uarrlset92caxntmwn22nvw2fjan630m03stxw3t3")?.let(::openProfile)
                },
                onProfile = { showSettings = false; showAccount = false; openProfile(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 32.dp))
        }
    }
    if (showAccount && s.publicKey != null) {
        ModalBottomSheet(onDismissRequest = { showAccount = false }, containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CopyableMessageHost()
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val key = s.publicKey
                val profile = s.profiles[key]
                if (key != null) {
                    AccountAvatar(profile?.name ?: FeatherRepository.shortKey(key), 72, profile?.picture)
                    Text(profile?.name ?: FeatherRepository.shortKey(key), style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        FilledTonalIconButton(onClick = { showAccount = false; openProfile(key) }, modifier = Modifier.size(RoundControlSize)) { Icon(Icons.Default.Person, stringResource(R.string.ui_view_your_profile)) }
                        IconButton(onClick = { vm.logout(); showAccount = false; goHome() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.ui_disconnect_account)) }
                    }
                }
            }
        }
    }
    }
    }
}

/** Account sheets share the image cache but do not inherit paused feed playback. */
@Composable
internal fun AccountAvatar(name: String, size: Int, picture: String?) {
    val store = (LocalContext.current.applicationContext as FeatherApplication).media
    val resources by store.resources.collectAsStateWithLifecycle()
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    CompositionLocalProvider(LocalMedia provides MediaEnvironment(resources,
        lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED), null)) {
        Avatar(name, size, picture)
    }
}

@Composable
internal fun Avatar(name: String, size: Int = 36, picture: String? = null, onOpenImage: (() -> Unit)? = null) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        RemoteImage(picture, Modifier.fillMaxSize().clip(CircleShape).then(if (onOpenImage != null && picture != null) Modifier.clickable(onClickLabel = "Open profile picture", onClick = onOpenImage) else Modifier), avatar = true)
    }
}

@Composable
private fun MatchedHashtagBadge(note: Event, followed: Set<String>, onReference: (NostrContent.Reference) -> Unit) {
    val matches = remember(note.id, followed) {
        note.tags.asSequence().filter { it.size >= 2 && it[0] == "t" }
            .mapNotNull { normalizeFollowedTag(it[1]) }.filter { it in followed }.distinct().sorted().toList()
    }
    if (matches.isEmpty()) return
    var expanded by remember(note.id) { mutableStateOf(false) }
    fun open(tag: String) { expanded = false; onReference(NostrContent.Reference(tag, hashtag = tag)) }
    Row(Modifier.widthIn(max = 112.dp).clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f))
        .clickable { if (matches.size == 1) open(matches.first()) else expanded = true }
        .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("#${matches.first()}", Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (matches.size > 1) Text("+${matches.size - 1}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.ui_matching_followed_hashtags)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(matches, key = { it }) { tag ->
                    Text("#$tag", Modifier.fillMaxWidth().clickable { open(tag) }.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }, confirmButton = {})
}

@Composable
private fun RepostAttribution(reposts: List<Event>, state: FeedState, onProfile: (String) -> Unit) {
    var showPeople by remember { mutableStateOf(false) }
    val names = reposts.take(2).map { state.profiles[it.pubKey]?.name ?: FeatherRepository.shortKey(it.pubKey) }
    val label = when (reposts.size) {
        1 -> stringResource(R.string.ui_reposted_by_one, names.first())
        2 -> stringResource(R.string.ui_reposted_by_two, names[0], names[1])
        else -> stringResource(R.string.ui_reposted_by_more, names[0], names[1], reposts.size - 2)
    }
    Row(Modifier.fillMaxWidth().clickable {
        if (reposts.size == 1) onProfile(reposts.first().pubKey) else showPeople = true
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.repost), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showPeople) AlertDialog(onDismissRequest = { showPeople = false },
        title = { Text(stringResource(R.string.ui_repost)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(reposts.size, key = { reposts[it].pubKey }) { index ->
                    val author = reposts[index].pubKey
                    val profile = state.profiles[author]
                    val name = profile?.name ?: FeatherRepository.shortKey(author)
                    Row(Modifier.fillMaxWidth().clickable { showPeople = false; onProfile(author) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(name, picture = profile?.picture)
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }, confirmButton = {})
}

@Composable
private fun RepostRow(repost: Event, state: FeedState, onReference: (NostrContent.Reference) -> Unit,
    onProfile: (String) -> Unit, groupedReposts: List<Event> = emptyList(), onOpen: (Event) -> Unit) {
    val filter = LocalContentFilter.current
    if (filter.hides(repost)) return
    val target = remember(repost.id) { repostTarget(repost) } ?: return
    if (filter.hides(target)) return
    val people = groupedReposts.ifEmpty { listOf(repost) }.filterNot(filter::hides)
    if (people.isEmpty()) return
    val original = state.embedded[target.id]?.takeIf { it.kind in readableKinds }
    Column {
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            RepostAttribution(people, state, onProfile)
        }
        if (original != null) {
            NoteRow(original, state, onReference, { onProfile(original.pubKey) }, { onOpen(original) }, repost = repost)
        } else {
            Box(Modifier.fillMaxWidth().clickable(onClickLabel = "Read conversation") { onReference(target) }.padding(24.dp)) {
                EmbeddedNote(target, null, state.profiles, repost.id, onReference)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteRow(note: Event, state: FeedState, onReference: (NostrContent.Reference) -> Unit, onProfile: () -> Unit, onOpen: () -> Unit, compact: Boolean = true, repost: Event? = null,
    threadRow: ConversationRow? = null, showParent: Boolean = true, parentLoading: Boolean = false, onMore: (() -> Unit)? = null,
    showHashtagMatch: Boolean = false) {
    if (LocalContentFilter.current.hides(note)) return
    if (note.kind in listOf(9735, 9321)) {
        ZapActivityCard(note, state, onReference)
        return
    }
    val profile = state.profiles[note.pubKey]
    if (note.kind == 30023 && compact) {
        ArticleListRow(note, profile?.name ?: FeatherRepository.shortKey(note.pubKey), profile?.picture, onProfile, onOpen)
        return
    }
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable(note.id) { mutableStateOf(false) }
    val name = profile?.name ?: FeatherRepository.shortKey(note.pubKey)
    val moderationKey = remember(note.id) { Any() }
    val articleDetail = note.kind == 30023 && !compact
    val threaded = threadRow != null && note.kind in listOf(1, 1111, 1244, 1222)
    val inset = threaded && !threadRow.focused && !threadRow.root
    val rootReply = threaded && threadRow.root
    val replyConnector = rootReply && threadRow.below && !threadRow.more
    val replyIconSize = 20.dp
    val rail = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().then(if (threaded) Modifier.drawBehind {
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 42.dp.toPx() else 42.dp.toPx()
        if (threadRow.above) drawLine(rail, Offset(x, 0f), Offset(x, 18.dp.toPx()), strokeWidth = 2.dp.toPx())
        // A full-width root connects from its reply action below, keeping the rail out of its content.
        val railStart = if (threadRow.root) size.height - (if (threadRow.more) 60.dp else 12.dp).toPx() else 54.dp.toPx()
        if (!replyConnector && (threadRow.below || threadRow.more)) drawLine(rail, Offset(x, railStart),
            Offset(x, if (threadRow.more) size.height - 48.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
        if (threadRow.below && threadRow.more) drawLine(rail, Offset(x, size.height - 24.dp.toPx()),
            Offset(x, size.height), strokeWidth = 2.dp.toPx())
    } else Modifier).semantics { if (threadRow?.focused == true) stateDescription = "Selected note" }
        .clickable(onClickLabel = "Read conversation", onClick = onOpen).padding(horizontal = if (articleDetail) 0.dp else 24.dp, vertical = if (threaded) 12.dp else 24.dp)) {
        Row(Modifier.padding(horizontal = if (articleDetail) 24.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ModerationAvatar(note.pubKey, name, profile?.picture, expansionKey = moderationKey, note = note, onClick = onProfile)
                Text(name, modifier = Modifier.weight(1f).padding(end = 8.dp)
                    .avatarModerationName(note.pubKey, moderationKey, ownNote = note.kind in listOf(1, 20, 1111), onClick = onOpen), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            }
            if (showHashtagMatch) {
                MatchedHashtagBadge(note, state.followedHashtags, onReference)
                Spacer(Modifier.width(8.dp))
            }
            TranslationControl(note)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (articleDetail && !isRecipe(note)) ArticleReadingTime(note)
                NoteTimestamp(note)
            }
        }
        Column(Modifier.padding(start = if (inset) 48.dp else 0.dp)) {
        AnimatedVisibility(showParent,
            enter = fadeIn(tween(180)) + expandVertically(tween(180), expandFrom = Alignment.Top),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top)) {
            ReplyContext(note, state.embedded, state.profiles, onReference, parentLoading)
        }
        Spacer(Modifier.height(12.dp))
        when {
            note.kind == 1068 -> PollContent(note, state, onReference, repost?.id ?: note.id)
            note.kind == 32267 -> SoftwareAppContent(note, onReference)
            note.kind == 1621 -> IssueContent(note, compact, onReference, state.issueStatuses[note.id], state)
            note.kind in voiceKinds -> AudioContent(note, compact, name)
            note.kind == 30030 -> EmojiPackContent(note)
            note.kind == 30023 -> ArticleContent(note, compact, onReference, state)
            note.kind == 9802 -> HighlightContent(note, state.profiles, compact, onReference)
            note.kind == 17 -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (note.content.isBlank() || note.content == "+") stringResource(R.string.ui_liked) else note.content.take(80),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinkPreviews("", event = note)
            }
            note.kind == 7 -> {
                ReactionContent(note)
                reactionTarget(note)?.let { reference ->
                    Spacer(Modifier.height(12.dp))
                    EmbeddedNote(reference, state.embedded[reference.id], state.profiles, "${repost?.id ?: note.id}:${reference.id}", onReference, compact)
                }
            }
            else -> {
        val detectedTypes by DetectedMedia.types.collectAsStateWithLifecycle()
        val attachments by produceState<List<MediaContent.Attachment>>(emptyList(), note.id, detectedTypes) {
            value = withContext(Dispatchers.Default) { MediaContent.attachments(note, 32) }
        }
        val translated = LocalTranslations.current?.text(note)
        val originalBody = remember(note.id, attachments) { MediaContent.text(note, attachments) }
        val body by produceState(originalBody, note.id, attachments, translated) {
            value = withContext(Dispatchers.Default) {
                if (translated == null) originalBody
                else attachments.fold(translated) { text, attachment -> text.replace(attachment.url, "") }
            }
        }
        val quotes = remember(note.id) { NostrContent.quotes(note) }
        LinkPreviewContent(body, event = note) { part ->
            val richContent = richNoteText(part, note, state.profiles, onReference, quotes.map { it.id }.toSet())
            val content = if (quotes.isEmpty()) richContent else richContent.subSequence(0, richContent.text.trimEnd().length)
            if (content.isNotBlank()) NoteSelection(note) {
                val style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (threadRow?.focused == true) 20.sp else 16.sp,
                    lineHeight = if (threadRow?.focused == true) 28.sp else 24.sp)
                if (compact) InlineExpandableText(content, 12, expanded, { expanded = !expanded }, style,
                    stringResource(R.string.ui_expand_note), stringResource(R.string.ui_collapse_note))
                else LinkText(content, style = style)
            }
        }
        val orderedContent = remember(note.id, attachments, quotes) {
            val tokens = NostrContent.tokens(note.content, note)
            // Keep tag-only references/media at the end, in their existing fallback order.
            val quoted = quotes.map { reference ->
                Triple(tokens.firstOrNull { it.reference?.id == reference.id }?.start ?: Int.MAX_VALUE,
                    reference, null as MediaContent.Attachment?)
            }
            val media = attachments.map { attachment ->
                Triple(tokens.firstOrNull { it.url == attachment.url }?.start ?: Int.MAX_VALUE,
                    null as NostrContent.Reference?, attachment)
            }
            (quoted + media).sortedBy { it.first }
        }
        orderedContent.forEachIndexed { index, (_, reference, attachment) ->
            if (attachment != null) {
                Spacer(Modifier.height(12.dp))
                NoteMedia(note, listOf(attachment), repost?.id ?: note.id)
            } else if (reference != null) {
                if (body.isNotBlank() || index > 0) Spacer(Modifier.height(6.dp))
                EmbeddedNote(reference, state.embedded[reference.id], state.profiles, "${repost?.id ?: note.id}:${reference.id}", onReference, compact)
            }
        }
            }
        }
        } // Surrounding replies stay beside the rail; roots and the selected note use the full width.
        if (note.kind !in listOf(7, 17, 30030)) {
            val counts = state.engagement[note.id] ?: Engagement()
            val reply = LocalReply.current
            val more = threadRow?.more == true
            val leftActions: @Composable () -> Unit = {
            // Keep a little breathing room inside the text edge, independent of reply counts.
            // A continuation control owns the gutter beside this row; keep their tap areas separate.
            // conversation.xml's tail is (2, 22) in a 24-unit viewport. Pin it to the avatar axis
            // (18dp inside the content edge); counts occupy space only after the fixed icon slot.
            val replyOffset = if (rootReply) 18.dp - 12.dp - (24.dp - replyIconSize) / 2 - replyIconSize * (2f / 24f)
                else if (more) 0.dp else (-12).dp
            Row((if (rootReply) Modifier.padding(start = replyOffset) else Modifier.offset(x = replyOffset))
                .widthIn(min = 56.dp).heightIn(min = 48.dp).clip(CircleShape).clickable(onClickLabel = "Reply to note", onClick = { reply(note) }).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start)) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.conversation), "Reply; ${counts.replies} replies", Modifier.size(replyIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (counts.replies > 0) Text(counts.replies.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RepostButton(note, note.id in state.sentReposts, counts.reposts,
                ownCounted = state.publicKey != null && counts.repostedByAccount == state.publicKey)
            }
            // Keep the details above the action row so thread continuations remain anchored to it.
            AnimatedVisibility(detailsExpanded, enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))) {
                Box(Modifier.padding(start = if (inset || more) 48.dp else if (articleDetail) 24.dp else 0.dp,
                    end = if (articleDetail) 24.dp else 0.dp)) {
                    NoteDetailsPanel(note, state, onReference)
                }
            }
            val expandAction: @Composable () -> Unit = {
                IconButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (detailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        stringResource(if (detailsExpanded) R.string.ui_hide_note_details else R.string.ui_show_note_details),
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
                }
            }
            val rightActions: @Composable () -> Unit = {
                OrangeHeart(note, counts.reactions, state.sentReactions[note.id].orEmpty())
                NoteZap(note, counts, state.publicKey)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = if (articleDetail) 24.dp else 0.dp)
                .then(if (replyConnector) Modifier.drawBehind {
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 18.dp.toPx() else 18.dp.toPx()
                    // All actions are vertically centered, even if large text makes this row taller.
                    val tipY = size.height / 2f + replyIconSize.toPx() * (22f / 24f - .5f)
                    drawLine(rail, Offset(x, tipY), Offset(x, size.height + 12.dp.toPx()), strokeWidth = 2.dp.toPx())
                } else Modifier)) {
                if (inset || more) {
                    // Preserve full touch targets when the avatar rail narrows a reply on small screens.
                    FlowRow(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) { leftActions() }
                        expandAction()
                        Row(verticalAlignment = Alignment.CenterVertically) { rightActions() }
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { leftActions() }
                    expandAction()
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) { rightActions() }
                }
                // Share the actions' height. Bottom alignment also keeps the rail gap aligned if counts wrap.
                if (more) IconButton(onClick = onMore ?: onOpen,
                    modifier = Modifier.align(Alignment.BottomStart).size(48.dp)) {
                    Icon(painterResource(R.drawable.thread_more), stringResource(R.string.ui_load_more_replies_in_this_conversation),
                        Modifier.offset(x = (-6).dp).size(24.dp), tint = rail)
                }
            }
        }
    }
}

@Composable
private fun ConversationGap(row: ConversationRow, profiles: Map<String, Profile>, onProfile: (String) -> Unit, onOpen: (NostrContent.Reference) -> Unit, onInfo: (String, String) -> Unit,
    loading: Boolean = false, onLoadParent: (() -> Unit)? = null, onMore: (() -> Unit)? = null) {
    if (row.gap in listOf(ThreadGap.DELETED, ThreadGap.MUTED, ThreadGap.SPAM)) {
        ConcealedConversationNote(row, profiles, onProfile, onInfo, onMore)
        return
    }
    val rail = MaterialTheme.colorScheme.outlineVariant
    val title = when (row.gap) {
        ThreadGap.HIDDEN -> "Parent note hidden"
        ThreadGap.DELETED -> "Note deleted by its author"
        ThreadGap.LIMIT -> "Earlier conversation"
        ThreadGap.CYCLE -> "Invalid reply chain"
        else -> "Earlier note unavailable"
    }
    Row(Modifier.fillMaxWidth().drawBehind {
        if (row.gap in listOf(ThreadGap.MISSING, ThreadGap.LIMIT)) {
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 42.dp.toPx() else 42.dp.toPx()
            drawLine(rail, Offset(x, 42.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }.padding(start = 18.dp, top = 4.dp, bottom = 4.dp)) {
        IconButton(enabled = !loading, onClick = {
            if (row.gap in listOf(ThreadGap.MISSING, ThreadGap.LIMIT) && row.reference != null) {
                if (onLoadParent != null && (row.reference.kind == null || row.reference.kind in readableKinds)) onLoadParent()
                else onOpen(NostrContent.navigable(row.reference))
            }
            else onInfo(title, when (row.gap) {
                ThreadGap.HIDDEN -> "This part of the conversation is hidden by your content settings or mute list."
                ThreadGap.DELETED -> "A signed deletion request from the author removes this note from the conversation."
                else -> "The reply references form a loop. Feather stopped following them."
            })
        }) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = "Loading earlier conversation" },
                strokeWidth = 1.5.dp, color = rail)
            else when (row.gap) {
                ThreadGap.HIDDEN -> Icon(painterResource(R.drawable.account_hidden), title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                ThreadGap.DELETED -> Icon(Icons.Default.Delete, title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                ThreadGap.CYCLE -> Icon(Icons.Default.Warning, title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Icon(painterResource(R.drawable.thread_more), stringResource(R.string.ui_load_earlier_conversation), tint = rail)
            }
        }
    }
}

@Composable
private fun ConcealedConversationNote(row: ConversationRow, profiles: Map<String, Profile>, onProfile: (String) -> Unit, onInfo: (String, String) -> Unit, onMore: (() -> Unit)?) {
    val spam = row.gap == ThreadGap.SPAM
    val muted = row.gap == ThreadGap.MUTED || spam
    val source = if (muted) row.mutedCopy else row.deletedCopy
    val currentFilter = LocalContentFilter.current
    val filter = if (spam) currentFilter else if (muted) currentFilter.copy(authors = source?.pubKey?.let { currentFilter.authors - it } ?: currentFilter.authors, words = emptySet())
        else currentFilter.copy(deletedNotes = emptySet())
    val copy = source?.takeUnless(filter::hides)?.takeUnless {
        it.tagValue("expiration")?.toLongOrNull()?.let { expiry -> expiry <= System.currentTimeMillis() / 1000 } == true
    }
    var revealed by rememberSaveable(row.key, row.gap, copy?.id) { mutableStateOf(false) }
    val rail = MaterialTheme.colorScheme.outlineVariant
    val label = if (spam) stringResource(R.string.ui_suspected_spam) else if (muted) "Reply from a muted account" else "Note deleted by its author"
    Column {
        if (row.mutedTailStart) Text(stringResource(R.string.ui_muted_replies), Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().drawBehind {
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 42.dp.toPx() else 42.dp.toPx()
        if (row.above) drawLine(rail, Offset(x, 0f), Offset(x, 4.dp.toPx()), strokeWidth = 2.dp.toPx())
        if (row.below) drawLine(rail, Offset(x, 52.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }) {
        Row(Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (muted) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (spam) R.drawable.spam else if (source != null && source.pubKey !in currentFilter.authors) R.drawable.muted_words else R.drawable.account_hidden), label, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
            } else IconButton(onClick = { onInfo(label,
                "The author requested deletion. Any available cached text stays hidden until you choose to reveal it.") }) {
                Icon(Icons.Default.Delete, label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (copy != null) IconToggleButton(checked = revealed, onCheckedChange = { revealed = it }) {
                Icon(painterResource(R.drawable.reveal), if (muted) { if (revealed) stringResource(R.string.ui_hide_this_reply) else stringResource(R.string.ui_show_this_reply) } else { if (revealed) stringResource(R.string.ui_hide_cached_version) else stringResource(R.string.ui_show_cached_version) },
                    tint = if (revealed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.more && onMore != null) IconButton(onClick = onMore) {
                Icon(painterResource(R.drawable.thread_more), stringResource(R.string.ui_load_more_replies_in_this_conversation), tint = rail)
            }
        }
        AnimatedVisibility(revealed && copy != null, enter = fadeIn(tween(140)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180))) {
            Column(Modifier.padding(start = 72.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (muted) profiles[copy?.pubKey]?.name ?: FeatherRepository.shortKey(copy?.pubKey.orEmpty())
                    else stringResource(R.string.ui_cached_version_deleted_by_author),
                    modifier = if (muted && copy != null) Modifier.clickable { onProfile(copy.pubKey) } else Modifier,
                    style = if (muted) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (muted) .65f else 1f))
                // Plain selectable text deliberately has no media loads, links or note actions.
                SelectionContainer { Text(copy?.content.orEmpty(), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
}

/** Parent navigation is separate from the author's profile and never blocks the reply body. */
@Composable
private fun ReplyContext(note: Event, embedded: Map<String, Event>, profiles: Map<String, Profile>,
    onReference: (NostrContent.Reference) -> Unit, loading: Boolean = false) {
    val reference = remember(note.id) { NostrContent.replyReference(note) } ?: return
    val parent = embedded[reference.id]
    val filter = LocalContentFilter.current
    if (filter.hides(reference) || (parent != null && filter.hides(parent))) return
    val author = parent?.pubKey ?: reference.author
    val name = profiles[author]?.name ?: FeatherRepository.shortKey(author ?: reference.id)
    ReplyContextRow(parent, name, parent?.kind ?: reference.kind, loading) { onReference(reference) }
}

/** Text and links stay selectable; mention labels update when metadata arrives. */
@Composable
internal fun richNoteText(body: String, note: Event, profiles: Map<String, Profile>, onReference: (NostrContent.Reference) -> Unit,
    embeddedIds: Set<String> = emptySet()): androidx.compose.ui.text.AnnotatedString {
    val tokens = remember(body, note.id) { NostrContent.tokens(body, note) }
    return richText(body, tokens, profiles, onReference, embeddedIds)
}

@Composable
private fun richText(body: String, tokens: List<NostrContent.Token>, profiles: Map<String, Profile>,
    onReference: (NostrContent.Reference) -> Unit, embeddedIds: Set<String> = emptySet()): androidx.compose.ui.text.AnnotatedString {
    val open by rememberUpdatedState(onReference)
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(body, tokens, profiles, embeddedIds, linkColor, context) {
        buildAnnotatedString {
            var offset = 0
            tokens.forEach { token ->
                append(body.substring(offset, token.start))
                val ref = token.reference
                if (ref != null) {
                    if (ref.id !in embeddedIds || ref.profile) {
                        withLink(LinkAnnotation.Clickable(ref.copyableLink(token.text), TextLinkStyles(style = SpanStyle(color = linkColor))) { open(ref) }) {
                            append(if (ref.hashtag != null) token.text else if (ref.profile) "@${profiles[ref.id]?.name ?: FeatherRepository.shortKey(ref.id)}" else "↗ ${FeatherRepository.shortKey(ref.id)}")
                        }
                    }
                } else if (token.url != null) {
                    withLink(LinkAnnotation.Url(token.url, TextLinkStyles(style = SpanStyle(color = linkColor))) { context.openWebLink(token.url) }) { append(token.text) }
                }
                offset = token.end
            }
            append(body.substring(offset))
        }
    }
}

@Composable
internal fun EmbeddedNote(reference: NostrContent.Reference, note: Event?, profiles: Map<String, Profile>, mediaKey: String, onReference: (NostrContent.Reference) -> Unit, compact: Boolean = true, actions: (@Composable () -> Unit)? = null) {
    val filter = LocalContentFilter.current
    if (filter.hides(reference) || (note != null && filter.hides(note))) return
    if (note?.kind == 32267) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                SoftwareAppCard(note) { onReference(reference) }
                actions?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { it() } }
            }
        }
        return
    }
    if (note?.kind == 30023) {
        val profile = profiles[note.pubKey]
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column {
                ArticleListRow(note, profile?.name ?: FeatherRepository.shortKey(note.pubKey), profile?.picture,
                    onProfile = { onReference(NostrContent.Reference(note.pubKey, profile = true)) },
                    onOpen = { onReference(reference) })
                actions?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { it() } }
            }
        }
        return
    }
    Surface(onClick = { onReference(reference) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth(), tonalElevation = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (note == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (reference.kind == 30023) {
                        var waiting by remember(reference.id) { mutableStateOf(true) }
                        LaunchedEffect(reference.id) { kotlinx.coroutines.delay(12_000); waiting = false }
                        if (waiting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.article), stringResource(R.string.ui_article_unavailable_locally_open_externally), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(profiles[reference.author]?.name ?: FeatherRepository.shortKey(reference.author ?: reference.id),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.conversation), stringResource(R.string.ui_open_referenced_note), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(FeatherRepository.shortKey(reference.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                val profile = profiles[note.pubKey]
                val name = profile?.name ?: FeatherRepository.shortKey(note.pubKey)
                val moderationKey = remember(note.id) { Any() }
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    ModerationAvatar(note.pubKey, name, profile?.picture, expansionKey = moderationKey, note = note,
                        onClick = { onReference(NostrContent.Reference(note.pubKey, profile = true)) })
                    Text(name, modifier = Modifier.weight(1f)
                        .avatarModerationName(note.pubKey, moderationKey, ownNote = note.kind in listOf(1, 20, 1111)) { onReference(reference) },
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NoteTimestamp(note)
                }
                if (note.kind in voiceKinds) AudioContent(note, compact, name)
                else if (note.kind == 30030) EmojiPackContent(note)
                else if (note.kind == 30023) ArticleContent(note, compact, onReference)
                else if (note.kind == 9802) HighlightContent(note, profiles, compact, onReference)
                else {
                val detectedTypes by DetectedMedia.types.collectAsStateWithLifecycle()
                val attachments by produceState<List<MediaContent.Attachment>>(emptyList(), note.id, compact, detectedTypes) {
                    value = withContext(Dispatchers.Default) { MediaContent.attachments(note, limit = if (compact) 1 else 32) }
                }
                val body = remember(note.id, attachments) { MediaContent.text(note, attachments) }
                LinkPreviewContent(body, limit = 1, event = note) { part ->
                    val text = richNoteText(part, note, profiles, onReference)
                    if (text.isNotBlank()) NoteSelection(note) {
                        LinkText(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp), maxLines = if (compact) 8 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    }
                }
                // One level only. Deeper references remain links, avoiding recursive network/render work.
                if (attachments.isNotEmpty()) NoteMedia(note, attachments, mediaKey)
                }
                actions?.invoke()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings(vm: FeatherViewModel, state: FeedState, onChange: (Boolean?, Boolean?) -> Unit, onLogout: () -> Unit, onWallet: () -> Unit, onIssue: (IssueType) -> Unit, onIssues: () -> Unit, onFeather: () -> Unit, onProfile: (String) -> Unit, modifier: Modifier) {
    var showMutedAccounts by rememberSaveable(state.publicKey) { mutableStateOf(false) }
    if (showMutedAccounts) MutedAccountsSheet(vm, onDismiss = { showMutedAccounts = false }, onProfile = {
        showMutedAccounts = false; onProfile(it)
    })
    var showMutedWords by rememberSaveable { mutableStateOf(false) }
    var mutedWordInput by rememberSaveable(state.publicKey) { mutableStateOf("") }
    val muteWordEdits by vm.muteWordEdits.collectAsStateWithLifecycle()
    val refreshingAccount by vm.repo.accountDataLoading.collectAsStateWithLifecycle()
    val accountRefreshError by vm.repo.accountDataError.collectAsStateWithLifecycle()
    val decryptingMutes by vm.decryptingMutes.collectAsStateWithLifecycle()
    LaunchedEffect(showMutedWords) {
        if (showMutedWords) { vm.repo.refreshAccountData(force = true); vm.retryMutes() }
    }
    if (showMutedWords) ModalBottomSheet(onDismissRequest = { showMutedWords = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val words = remember(state.mutedWords, muteWordEdits) {
            state.mutedWords.toMutableSet().apply {
                muteWordEdits.forEach { if (it.add) add(it.word) else remove(it.word) }
            }.sorted()
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Icon(painterResource(R.drawable.muted_words), stringResource(R.string.ui_muted_words),
                Modifier.padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = mutedWordInput, onValueChange = { if (it.length <= 256) mutedWordInput = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text(stringResource(R.string.ui_mute_word_input)) }, shape = RoundedCornerShape(24.dp))
                IconButton(enabled = mutedWordInput.isNotBlank(), onClick = {
                    if (vm.changeMutedWord(mutedWordInput, true)) mutedWordInput = ""
                }) {
                    Icon(Icons.Default.Add, stringResource(R.string.ui_add_muted_word))
                }
            }
            if (words.isEmpty() && state.pendingMuteList == null && refreshingAccount != state.publicKey && accountRefreshError?.first != state.publicKey)
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.muted_words), stringResource(R.string.ui_no_muted_words),
                        Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            if (words.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(words, key = { it }) { word ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(Modifier.weight(1f)) { Text(word, Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge) }
                        IconButton(onClick = { vm.changeMutedWord(word, false) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.ui_remove_muted_word, word),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (refreshingAccount == state.publicKey || decryptingMutes)
                CircularProgressIndicator(Modifier.padding(bottom = 16.dp).size(20.dp), strokeWidth = 2.dp)
            else if (state.pendingMuteList != null || accountRefreshError?.first == state.publicKey)
                IconButton(onClick = { vm.repo.refreshAccountData(force = true); vm.retryMutes() }) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.ui_retry), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
        }
    }
    val context = LocalContext.current
    val mediaStore = (context.applicationContext as FeatherApplication).media
    val budget by mediaStore.budgetMb.collectAsStateWithLifecycle()
    val mediaError by mediaStore.error.collectAsStateWithLifecycle()
    val resources by mediaStore.resources.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val usedBytes by produceState<Long?>(null, resources, lifecycle) {
        value = null
        if (resources != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = mediaStore.usedBytes()
                kotlinx.coroutines.delay(2_000)
            }
        }
    }
    var selectedBudget by remember(budget) { mutableIntStateOf(MediaContent.cacheOptionsMb.indexOf(budget)) }
    val scope = rememberCoroutineScope()
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        state.publicKey?.let { key ->
            val profile = state.profiles[key]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountAvatar(profile?.name ?: FeatherRepository.shortKey(key), 40, profile?.picture)
                Text(profile?.name ?: FeatherRepository.shortKey(key), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                FilledTonalIconButton(onClick = onWallet, modifier = Modifier.size(RoundControlSize)) { Icon(painterResource(R.drawable.wallet), stringResource(R.string.ui_wallet_21_sats_per_zap)) }
                FilledTonalIconButton(onClick = onLogout, modifier = Modifier.size(RoundControlSize)) { Icon(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.ui_log_out)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.sensitive_content), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.ui_show_nsfw), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = state.showSensitive, onCheckedChange = { show ->
                onChange(show, null)
            }, modifier = Modifier.semantics { contentDescription = "Show NSFW and content-warning tagged content" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.proof_of_work), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text("PoW", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = state.proofOfWork, onCheckedChange = { enabled ->
                onChange(null, enabled)
            }, modifier = Modifier.semantics { contentDescription = "Proof of work for posts and emoji reactions" })
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.storage), stringResource(R.string.ui_media_cache_storage), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                val mb = MediaContent.cacheOptionsMb[selectedBudget]
                val limit = if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"
                val used = usedBytes?.let(::cacheSizeLabel) ?: "—"
                Text(used, style = MaterialTheme.typography.bodyMedium, color = cacheUsageColor(usedBytes, mb),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "$used media cache used" })
                if (resources == null) { Spacer(Modifier.width(16.dp)); CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                Spacer(Modifier.width(16.dp))
                Text(limit, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = "$limit media cache limit" })
            }
            CacheBudgetSlider(selectedBudget, usedBytes,
                onSelect = { selectedBudget = it },
                onFinished = { index -> scope.launch { mediaStore.setBudget(MediaContent.cacheOptionsMb[index]) } })
            mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { showMutedWords = true }, modifier = Modifier.size(RoundControlSize)) {
                Icon(painterResource(R.drawable.muted_words), stringResource(R.string.ui_muted_words), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = { showMutedAccounts = true }, modifier = Modifier.size(RoundControlSize)) {
                Icon(painterResource(R.drawable.account_hidden), stringResource(R.string.ui_muted_accounts), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onIssue(IssueType.BUG) }, modifier = Modifier.size(RoundControlSize)) {
                Icon(painterResource(R.drawable.bug_report), stringResource(R.string.ui_report_a_bug))
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = { onIssue(IssueType.FEATURE) }, modifier = Modifier.size(RoundControlSize)) {
                Icon(painterResource(R.drawable.feature_request), stringResource(R.string.ui_suggest_a_feature))
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://feather.dergigi.com")))
            }, modifier = Modifier.size(RoundControlSize)) {
                Icon(Icons.Default.Home, stringResource(R.string.ui_open_feather_homepage),
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = onFeather, modifier = Modifier.size(RoundControlSize)) {
                Icon(painterResource(R.drawable.feather), stringResource(R.string.ui_open_feather_profile), Modifier.size(28.dp), tint = Color.Unspecified)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onIssues) {
                Icon(painterResource(R.drawable.project), stringResource(R.string.ui_repository_issues), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dergigi/feather/releases/tag/v${BuildConfig.VERSION_NAME}")))
                }.padding(horizontal = 12.dp, vertical = 16.dp).semantics { contentDescription = "Version ${BuildConfig.VERSION_NAME}, open GitHub release" })
            Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(BuildConfig.GIT_COMMIT.take(7), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dergigi/feather/commit/${BuildConfig.GIT_COMMIT}")))
                }.padding(horizontal = 12.dp, vertical = 16.dp).semantics { contentDescription = "Build commit ${BuildConfig.GIT_COMMIT.take(7)}, open GitHub commit" })
        }
    }
}

/** Keep quoted events in their place in the biography; tokenization is done by the repository. */
@Composable
private fun ProfileBiography(profileKey: String, profile: Profile, state: FeedState, onReference: (NostrContent.Reference) -> Unit) {
    var expanded by rememberSaveable(profileKey, profile.about) { mutableStateOf(false) }
    val quotes = remember(profile.aboutTokens) {
        profile.aboutTokens.filter { it.reference?.let { ref -> !ref.profile && ref.hashtag == null && ref.external == null } == true }
            .distinctBy { it.reference?.id }.take(3)
    }
    fun textRange(start: Int, end: Int): Pair<String, List<NostrContent.Token>> {
        val tokens = profile.aboutTokens.filter { it.start >= start && it.end <= end }
            .map { it.copy(start = it.start - start, end = it.end - start) }
        return profile.about.substring(start, end) to tokens
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!expanded) {
            SelectionContainer {
                CompactBiography(
                    richText(profile.about, profile.aboutTokens, state.profiles, onReference),
                    hasQuotes = quotes.isNotEmpty(), onExpand = { expanded = true },
                )
            }
        } else {
            quotes.forEachIndexed { index, token ->
                val (body, tokens) = textRange(if (index == 0) 0 else quotes[index - 1].end, token.start)
                if (body.isNotBlank()) SelectionContainer {
                    LinkText(richText(body, tokens, state.profiles, onReference), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
                }
                val reference = token.reference!!
                EmbeddedNote(reference, state.embedded[reference.id], state.profiles, "bio:${reference.id}", onReference)
            }
            val (body, tokens) = textRange(quotes.lastOrNull()?.end ?: 0, profile.about.length)
            if (body.isNotBlank()) SelectionContainer {
                LinkText(richText(body, tokens, state.profiles, onReference), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
            }
        }
        // Event cards stay out of the compact preview, even when their link fits on one line.
        if (expanded) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse biography" else "Expand biography",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun cacheSizeLabel(bytes: Long): String = when {
    bytes == 0L -> "0 MB"
    bytes < 1024L * 1024 -> "<1 MB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024L * 1024)} MB"
    else -> String.format(java.util.Locale.getDefault(), "%.1f GB", bytes.toDouble() / (1024L * 1024 * 1024))
}

/** Measure at full size, then slide and clip the bar by the actual scroll distance. */
internal fun Modifier.scrollAway(fraction: Float, fromBottom: Boolean = false, onHeight: (Int) -> Unit = {}): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        onHeight(placeable.height)
        val visibleHeight = (placeable.height * fraction).toInt().coerceIn(0, placeable.height)
        layout(placeable.width, visibleHeight) {
            placeable.placeRelative(0, if (fromBottom) 0 else visibleHeight - placeable.height)
        }
    }
