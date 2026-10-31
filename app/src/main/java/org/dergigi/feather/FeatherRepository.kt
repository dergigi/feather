package org.dergigi.feather

import android.app.Application
import android.content.Context
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class FeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        crashes.install()
        com.vitorpamplona.quartz.utils.Log.minLevel = com.vitorpamplona.quartz.utils.LogLevel.ERROR
    }
    internal val crashes by lazy { CrashReports(this) }
    val repository by lazy { FeatherRepository(this) }
    val media by lazy { MediaStore(this) }
    internal val audio by lazy { AudioPlayback(this) }
    internal val timestamps by lazy { TimestampPreference(this) }
}

data class Profile(val name: String, val about: String = "", val picture: String? = null, val username: String? = null, val banner: String? = null, val lightning: String? = null, val aboutTokens: List<NostrContent.Token> = emptyList(), val nip05: String? = null, val website: String? = null)
data class FeedState(
    val notes: List<Event> = emptyList(),
    val notifications: List<Event> = emptyList(),
    val followedHashtags: Set<String> = emptySet(),
    val followedHashtagsVersion: Pair<Long, String>? = null,
    val pendingHashtagList: Event? = null,
    val notificationsFor: String? = null,
    val notificationsLoading: Boolean = false,
    val feedTab: ProfileTab = ProfileTab.POSTS,
    val feedTabs: List<ProfileTab> = listOf(ProfileTab.POSTS),
    val feedPreviews: Map<ProfileTab, List<Event>> = emptyMap(),
    val threadId: String? = null,
    val threadNotes: List<Event> = emptyList(),
    val conversation: Conversation? = null,
    val threadContextLoading: Boolean = false,
    val threadLoadingParent: String? = null,
    val deletedNotes: Set<String> = emptySet(),
    val profileEvents: List<Event> = emptyList(),
    val profileTabsFor: String? = null,
    val profileTabs: List<ProfileTab> = emptyList(),
    val hashtagNotes: List<Event> = emptyList(),
    val hashtagFor: String? = null,
    val hashtagAuthor: String? = null,
    val hashtagTab: ProfileTab = ProfileTab.POSTS,
    val hashtagCategories: List<ProfileTab> = emptyList(),
    val profiles: Map<String, Profile> = emptyMap(),
    val embedded: Map<String, Event> = emptyMap(),
    val pendingVotes: Set<String> = emptySet(),
    val issueStatuses: Map<String, Int> = emptyMap(),
    val pollResults: Map<String, PollResults> = emptyMap(),
    val engagement: Map<String, Engagement> = emptyMap(),
    val following: Set<String> = emptySet(),
    val followedAccounts: Set<String> = emptySet(),
    val followedAccountsVersion: Pair<Long, String>? = null,
    val sentReposts: Set<String> = emptySet(),
    val sentReactions: Map<String, List<ReactionChoice>> = emptyMap(),
    val deletedReactions: Set<Pair<String, String>> = emptySet(),
    val reactionChoices: List<ReactionChoice> = defaultReactions,
    val recentEmojis: List<String> = emptyList(),
    val customEmojis: List<ReactionChoice> = emptyList(),
    val mutedAuthors: Set<String> = emptySet(),
    val settingsEvent: Event? = null,
    val showSensitive: Boolean = false,
    val proofOfWork: Boolean = true,
    val pendingMuteList: Event? = null,
    val mutedWords: Set<String> = emptySet(),
    val publicKey: String? = null,
    val signerPackage: String? = null,
    val relays: List<String> = RelayDefaults.seeds.take(4),
    val connected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val error: String? = null,
)

/** Fast follow feedback must not be undone by a snapshot that started before the edit. */
private fun FeedState.withFollowedAccounts(event: Event?): FeedState {
    if (event == null || event.pubKey != publicKey) return this
    val previous = followedAccountsVersion
    if (previous != null && (event.createdAt < previous.first ||
        (event.createdAt == previous.first && event.id >= previous.second))) return this
    return copy(followedAccounts = Protocol.follows(event), followedAccountsVersion = event.createdAt to event.id)
}

/** Keep confirmed interests edits and decrypted state ahead of older background snapshots. */
private fun FeedState.withFollowedHashtags(event: Event?, privateTags: Set<String>?): FeedState {
    if (event == null || event.pubKey != publicKey) return this
    val previous = followedHashtagsVersion
    if (previous != null && (event.createdAt < previous.first ||
        (event.createdAt == previous.first && event.id > previous.second))) return this
    if (previous?.second == event.id && event.content.isNotBlank() && privateTags == null && pendingHashtagList == null) return this
    return copy(followedHashtags = (followedTags(event) + privateTags.orEmpty()).take(256).toSet(),
        followedHashtagsVersion = event.createdAt to event.id,
        pendingHashtagList = event.takeIf { it.content.isNotBlank() && privateTags == null })
}

@OptIn(FlowPreview::class)
class FeatherRepository(context: Context) {
    private val prefs = context.getSharedPreferences("feather", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repostOriginals = Channel<Pair<Event, Set<NormalizedRelayUrl>>>(32)
    internal val blocktime = Blocktime(context.applicationContext)
    private val dbPath = context.getDatabasePath("events.db").also { it.parentFile?.mkdirs() }.absolutePath
    private val db by lazy { EventStore(dbName = dbPath, numReaders = 1) }
    private val mutex = Mutex()
    private val snapshotMutex = Mutex()
    // Accessed under mutex: protect the evidence behind recently displayed counts.
    private val recentEngagement = LinkedHashMap<String, Set<String>>()
    private var retainedEngagementEvents = 0
    private val threadMutex = Mutex()
    private val recentThreads = RecentThreads(context, scope)
    private val scopedContentChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private var hashtagDiscoveryJob: Job? = null
    private val threadChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private val threadGeneration = java.util.concurrent.atomic.AtomicLong()
    private var threadRestoreJob: Job? = null
    private val socialActions = ActionLanes(4)
    private val repostStore = RepostStore(context)
    private val reactionStore = ReactionStore(context)
    internal val postOutbox = PostOutbox(context)
    private val networkAvailability = NetworkAvailability(context)
    internal val networkOnline = networkAvailability.online
    private val outboxWork = PostDeliveryWork(context.applicationContext)
    private val backgroundOutboxOwner = MutableStateFlow<String?>(null)
    private val backgroundOutboxMutex = Mutex()
    private val outboxWake = Channel<Unit>(Channel.CONFLATED)
    private val sendingPosts = MutableStateFlow<Set<String>>(emptySet())
    internal val outboxSending = sendingPosts.asStateFlow()
    private val reactionDeletions = ReactionDeletions(context)
    private val noteDeletions = NoteDeletions(context)
    @Volatile private var deletionTargets: List<Event> = emptyList()
    @Volatile private var deletedReactions: Set<Pair<String, String>> = emptySet()
    private fun engagementDeleted(event: Event) = event.kind in listOf(6, 7, 16) && (event.pubKey to event.id) in deletedReactions
    private val hashtagCache = HashtagCache(context)
    private val muteCache = MuteCache(context)
    private val foregroundActive = MutableStateFlow(false)
    private val publishingAuthors = mutableMapOf<String, Int>()
    private fun beginPublishing(author: String) = synchronized(publishingAuthors) {
        publishingAuthors[author] = (publishingAuthors[author] ?: 0) + 1
    }
    private fun endPublishing(author: String) = synchronized(publishingAuthors) {
        val remaining = (publishingAuthors[author] ?: 1) - 1
        if (remaining == 0) publishingAuthors.remove(author) else publishingAuthors[author] = remaining
    }
    private fun refreshDeletedReactions() = synchronized(reactionDeletions) {
        deletedReactions = reactionDeletions.deleted()
    }
    private val eventRelays = EventRelays(context)
    private val detailAuthors = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private fun recordRelayLocked(id: String, relay: String?) {
        if (relay != null && eventRelays.record(id, relay)) changed.tryEmit(Unit)
    }
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private val zapVerification = ZapVerification(scope) { changed.tryEmit(Unit) }
    @Volatile private var pollVoterAuthors: List<String> = emptyList()
    @Volatile private var visibleIssues: List<Event> = emptyList()
    @Volatile private var issueRepositoryEvents: Map<String, List<Event>> = emptyMap()
    @Volatile private var visiblePolls: List<Event> = emptyList()
    @Volatile private var engagementIds: List<String> = emptyList()
    @Volatile private var engagementApps: List<String> = emptyList()
    private val _state = MutableStateFlow(FeedState(
        showSensitive = prefs.getString("pubkey", null)?.let { prefs.getBoolean("show-sensitive:$it", prefs.getBoolean("show-sensitive", false)) } ?: false,
        proofOfWork = prefs.getString("pubkey", null)?.let { prefs.getBoolean("proof-of-work:$it", prefs.getBoolean("proof-of-work", true)) } ?: true,
        publicKey = prefs.getString("pubkey", null)?.takeIf { !prefs.getString("signer", null).isNullOrBlank() }, signerPackage = prefs.getString("signer", null)?.takeIf { it.isNotBlank() },
    ))
    internal suspend fun applyAccountSettings(owner: String, sensitive: Boolean, pow: Boolean) = withContext(Dispatchers.IO) {
        if (state.value.publicKey != owner) return@withContext
        if (state.value.showSensitive == sensitive && state.value.proofOfWork == pow) return@withContext
        check(prefs.edit().putBoolean("show-sensitive:$owner", sensitive).putBoolean("proof-of-work:$owner", pow).commit())
        _state.update { if (it.publicKey == owner) it.copy(showSensitive = sensitive, proofOfWork = pow) else it }
        feedDiscoveryAt = 0L
        threadChanges.tryEmit(Unit)
        changed.tryEmit(Unit)
    }
    internal suspend fun publishSettings(event: Event): Boolean = withContext(Dispatchers.IO) {
        check(event.pubKey == state.value.publicKey && event.kind == 30078 && event.tagValue("d") == SETTINGS_D && event.verify())
        ready.await()
        repeat(3) { attempt ->
            if (attempt > 0) delay(if (attempt == 1) 1_000 else 3_000)
            // The signer may have covered the activity and disconnected the relay client.
            withTimeout(15_000) { foregroundActive.first { it } }
            currentCoroutineContext().ensureActive()
            check(state.value.publicKey == event.pubKey) { "Account changed" }
            updateRoutingPool()
            client.connect()
            val routes = settingsRelayUrls().filter { it in routingPool }
            val confirmed = try {
                routes.isNotEmpty() && client.publishAndConfirm(event, routes.map { it.normalizeRelayUrl() }.toSet(),
                    timeoutInSeconds = 15, untilFirstAccept = false)
            } catch (_: TimeoutCancellationException) { false }
            catch (_: java.io.IOException) { false }
            if (confirmed) {
                accept(event)
                return@withContext true
            }
        }
        false
    }
    val state: StateFlow<FeedState> = _state.asStateFlow()
    private val threadProgress = ThreadProgress(scope) { target, parent, loading ->
        _state.update { if (it.threadId == target) it.copy(threadContextLoading = loading, threadLoadingParent = parent.takeIf { loading }) else it }
    }
    private val client = NostrClient(RelayTransport(), scope)
    private val ready = CompletableDeferred<Unit>()
    private val verified = object : LinkedHashMap<String, Boolean>(1024, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 2048
    }
    // Full verified events, not a record of IDs that might since have been evicted.
    // Accessed under mutex, alongside the disk cache.
    private val exactEventCache = object : LinkedHashMap<String, Event>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Event>?) = size > 128
    }
    private val eventLookupChanges = Channel<Unit>(Channel.CONFLATED)
    // Repository monitor owns network state; all readers share this one subscription.
    private var pendingEventLookups: Map<String, Set<String>> = emptyMap()
    private val eventIdPattern = Regex("[0-9a-f]{64}")
    @Volatile private var active = false
    private var notificationTimeout: Job? = null
    @Volatile private var viewingNotifications = false
    private var profileRestoreJob: Job? = null
    private val profileRestoreGeneration = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var viewedProfile: String? = null
    @Volatile private var profileTab = ProfileTab.POSTS
    @Volatile private var feedUntil: Long? = null
    @Volatile private var selectedFeed = ProfileTab.POSTS
    private val feedCeilings = java.util.concurrent.ConcurrentHashMap<ProfileTab, Long>()
    // Snapshot mutex owns discovery caches. Avoid re-parsing every category on each relay event.
    private var feedDiscoveryKey: Pair<List<String>?, Set<String>>? = null
    @Volatile private var feedDiscoveryAt = 0L
    private var feedDiscoveryPages: Map<ProfileTab, List<Event>> = emptyMap()
    @Volatile private var hashtagLimit = 200
    // Retain a bounded tail even if disk-cache pruning removes historical events.
    @Volatile private var olderEvents: List<Event> = emptyList()
    private val historySequence = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var profileLimit = 100
    @Volatile private var profileUntil: Long? = null
    @Volatile private var viewedHashtag: String? = null
    @Volatile private var viewedHashtagAuthor: String? = null
    @Volatile private var selectedHashtagTab = ProfileTab.POSTS
    private val hashtagWindows = mutableMapOf<ProfileTab, Pair<Int, Long?>>()
    private var hashtagRestoreJob: Job? = null
    private val hashtagRestoreGeneration = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var hashtagUntil: Long? = null
    @Volatile private var viewedThread: String? = null
    @Volatile private var threadContext: Conversation? = null
    @Volatile private var threadParents: List<String> = emptyList()
    @Volatile private var threadExpanded: Set<String> = emptySet()
    @Volatile private var threadAncestorLimit = INITIAL_THREAD_ANCESTORS
    @Volatile private var threadRequestedParent: NostrContent.Reference? = null
    private var threadParentJob: Job? = null
    private val profileQueries = ProfileRelayQuery()
    private var profileSweep: Job? = null
    private var profileSweepKey: Triple<String, ProfileTab, List<String>>? = null
    @Synchronized
    private fun stopProfileSweep() { profileSweep?.cancel(); profileSweep = null; profileSweepKey = null }
    private fun profileReadRelays(author: String, tab: ProfileTab): List<String> =
        (relayLists[author]?.write.orEmpty().ifEmpty { state.value.relays } +
            listOfNotNull(ZAPSTORE_RELAY.takeIf { tab == ProfileTab.APPS }, RECIPE_RELAY.takeIf { tab == ProfileTab.RECIPES }) +
            if (tab == ProfileTab.ISSUES) IssueReports.RELAYS else emptyList()).distinct()

    private var historyJob: Job? = null
    private var refreshTimeout: Job? = null
    @Volatile private var routingPool: Set<String> = emptySet()
    @Volatile private var relayLists: Map<String, Outbox.RelayList> = emptyMap()
    @Volatile private var visibleIds: List<String> = emptyList()
    @Volatile private var galleryNote: Event? = null
    @Volatile private var emojiPackRequests: List<EmojiPack> = emptyList()
    @Volatile private var references: List<NostrContent.Reference> = emptyList()
    @Volatile private var embeddedIds: List<String> = emptyList()
    @Volatile private var profileAuthors: List<String> = emptyList()
    @Volatile private var openedReference: NostrContent.Reference? = null
    private var discoveryJob: Job? = null
    fun readingNotes(ids: List<String>) {
        val bounded = ids.distinct().take(32)
        if (bounded == visibleIds) return
        visibleIds = bounded
        if (viewedThread != null) threadChanges.tryEmit(Unit)
        changed.tryEmit(Unit)
    }
    fun openReference(reference: NostrContent.Reference?) { openedReference = reference }
    fun readingGallery(note: Event?) {
        if (galleryNote?.id == note?.id) return
        galleryNote = note
        changed.tryEmit(Unit)
    }

    private var seedOffset = 0
    private var embeddedSeedOffset = 6
    private val unavailableUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun healthy(url: String) = (unavailableUntil[Protocol.relay(url) ?: url] ?: 0) <= android.os.SystemClock.elapsedRealtime()
    private fun unavailable(url: String) {
        if (active) unavailableUntil[Protocol.relay(url) ?: url] = android.os.SystemClock.elapsedRealtime() + 120_000
    }
    private var inserts = 0
    private val listener = object : SubscriptionListener {
        override suspend fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
            if (forFilters?.any { it.match(event) } == true) accept(event, seenAt = relay.url)
        }
        override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
            threadProgress.completed(relay.url, forFilters)
            unavailableUntil.remove(Protocol.relay(relay.url) ?: relay.url)
            if (viewingNotifications && forFilters?.any { it.authors == null && it.tags?.get("p") == listOf(state.value.publicKey) && it.kinds?.any { kind -> kind in notificationKinds } == true } == true)
                _state.update { it.copy(notificationsLoading = false) }
            if (forFilters?.any { it.kinds?.any { kind -> kind in profileKinds } == true } == true) _state.update { it.copy(loading = false) }
        }
        override fun onClosed(message: String, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
            threadProgress.completed(relay.url, forFilters)
            unavailable(relay.url)
        }
    }
    init {
        scope.launch { deliverOutbox() }
        scope.launch {
            ready.await()
            if (postOutbox.records().any { it.event.pubKey == state.value.publicKey }) {
                runCatching { outboxWork.schedule(replace = false) }
            }
        }
        // Migrate legacy device preferences only to the currently connected account.
        state.value.publicKey?.let { owner ->
            prefs.edit().putBoolean("show-sensitive:$owner", state.value.showSensitive)
                .putBoolean("proof-of-work:$owner", state.value.proofOfWork).apply()
        }
        prefs.edit().remove("show-sensitive").remove("proof-of-work").apply()
        client.disconnect()
        prefs.edit().remove("relays").remove("draft").remove("draftReply").remove("pending").apply()
        if (_state.value.publicKey == null) prefs.edit().remove("pubkey").remove("signer").apply()
        client.addConnectionListener(object : RelayConnectionListener {
            override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
                unavailableUntil.remove(Protocol.relay(relay.url.url) ?: relay.url.url)
                _state.update { it.copy(connected = it.connected + relay.url.url, error = null) }
            }
            override fun onDisconnected(relay: IRelayClient) { _state.update { it.copy(connected = it.connected - relay.url.url) } }
            override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
                unavailable(relay.url.url)
                _state.update { it.copy(connected = it.connected - relay.url.url) }
            }
        })
        // Optional original-event propagation must never delay explicit repost publication.
        repeat(2) {
            scope.launch {
                for ((original, routes) in repostOriginals) {
                    try {
                        if (original.tags.none { it.firstOrNull() == "-" } && original.verify()) {
                            client.publishAndConfirm(original, routes, timeoutInSeconds = 10, untilFirstAccept = true)
                        }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Best effort: a relay rejecting the original does not undo a repost.
                    }
                }
            }
        }
        scope.launch {
            try {
                noteDeletions.load(mutex.withLock { db.query<Event>(Filter(kinds = listOf(5), limit = 512)) })
                // Display the cached home feed before category discovery, engagement or pruning.
                snapshot(initialFeedOnly = true)
                recentThreads.load()
                ready.complete(Unit)
                snapshot()
                trimCache()
            }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = "Could not open the local cache.") }; ready.completeExceptionally(e) }
        }
        scope.launch { ready.await(); changed.sample(250).collect {
            try { snapshot() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // One failed optional read must not permanently stop cached view updates.
                android.util.Log.w("FeatherRepository", "Could not refresh cached views", e)
            }
        } }
        scope.launch {
            ready.await()
            scopedContentChanges.sample(150).collect {
                // Display arrivals independently of optional discovery and engagement work.
                if (viewedHashtag != null) { restoreHashtagCache(); hashtagRestoreJob?.join() }
                else if (viewedProfile != null) { restoreProfileCache(); profileRestoreJob?.join() }
            }
        }
        scope.launch { ready.await(); threadChanges.sample(150).collect { refreshConversation() } }
        scope.launch {
            ready.await()
            for (ignored in eventLookupChanges) {
                try { reconcileEventLookups() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    // A failed cache read must not turn into a redundant network request.
                    _state.update { it.copy(error = "Could not read cached event: ${e.message ?: e.javaClass.simpleName}") }
                }
            }
        }
    }
    internal val accountDataLoading = MutableStateFlow<String?>(null)
    internal val accountDataError = MutableStateFlow<Pair<String, String>?>(null)
    private val accountQueries = ProfileRelayQuery()
    private var accountRefreshJob: Job? = null
    private var accountRefreshOwner: String? = null
    private var accountRefreshedAt = 0L

    /** Replaceable account discovery, independent of the small feed routing pool. */
    @Synchronized
    fun refreshAccountData(force: Boolean = false) {
        val owner = state.value.publicKey ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (accountRefreshOwner == owner && (accountRefreshJob?.isActive == true ||
                (!force && now - accountRefreshedAt < 60_000))) return
        accountRefreshJob?.cancel()
        accountRefreshOwner = owner
        accountDataLoading.value = owner
        accountDataError.value = null
        accountRefreshJob = scope.launch(Dispatchers.IO) {
            try {
                ready.await()
                refreshMuteState(owner)
                fun current() = check(state.value.publicKey == owner) { "Account changed" }
                val known = followEvent(owner, 10002)?.let(Outbox::parse)
                val bootstrap = (known?.write.orEmpty() + known?.read.orEmpty() + RelayDefaults.seeds.take(3) + "wss://purplepag.es").distinct().take(32)
                accountQueries.completed(bootstrap, listOf(Filter(authors = listOf(owner), kinds = listOf(10002), limit = 1).toJson())) { relay, event ->
                    current()
                    event.pubKey == owner && event.kind == 10002 && accept(event, seenAt = relay)
                }
                current()
                val relays = followEvent(owner, 10002)?.let(Outbox::parse)
                val routes = (relays?.write.orEmpty() + relays?.read.orEmpty() + bootstrap).distinct().take(32)
                val kinds = listOf(0, 3, 10000, 10015, 10030, 10063)
                val filters = kinds.map { Filter(authors = listOf(owner), kinds = listOf(it), limit = 1) } +
                    Filter(authors = listOf(owner), kinds = listOf(30000), tags = mapOf("d" to listOf("mute")), limit = 1) +
                    Filter(authors = listOf(owner), kinds = listOf(30078), tags = mapOf("d" to listOf(SETTINGS_D)), limit = 1)
                val completed = accountQueries.completed(routes, filters.map { it.toJson() }) { relay, event ->
                    current()
                    val allowed = event.pubKey == owner && (event.kind in kinds ||
                        (event.kind == 30000 && event.tagValue("d") == "mute") ||
                        (event.kind == 30078 && event.tagValue("d") == SETTINGS_D))
                    if (allowed && accept(event, seenAt = relay)) {
                        if (event.kind == 10000 || event.kind == 30000) refreshMuteState(owner)
                        true
                    } else false
                }
                current()
                check(completed.isNotEmpty()) { "Could not refresh account data. Cached lists are still available." }
                accountRefreshedAt = android.os.SystemClock.elapsedRealtime()
                changed.tryEmit(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (state.value.publicKey == owner) accountDataError.value = owner to (e.message ?: "Could not refresh account data")
            } finally {
                if (accountRefreshOwner == owner) accountDataLoading.value = null
            }
        }
    }

    private suspend fun refreshMuteState(owner: String) {
        val event = followEvent(owner, 10000) ?: followEvent(owner, 30000)
        val encrypted = event?.takeIf { it.content.isNotBlank() }
        val privateAuthors = encrypted?.let(muteCache::read)
        val authors = mutedPublicKeys(event) + (privateAuthors ?: encrypted?.let { muteCache.read(it, exact = false) }.orEmpty())
        val words = mutedWords(event) + encrypted?.let(muteCache::readWords).orEmpty()
        _state.update { if (it.publicKey == owner) it.copy(mutedAuthors = authors, mutedWords = words,
            pendingMuteList = encrypted?.takeIf { privateAuthors == null }) else it }
    }

    fun foreground() {
        active = true
        refreshAccountData()
        foregroundActive.value = true
        threadChanges.tryEmit(Unit)
        scope.launch { ready.await(); if (active) { changed.tryEmit(Unit); subscribeFeed(); client.connect() } }
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            ready.await()
            var ticks = 0
            while (isActive && active) {
                delay(10_000)
                if (!active) break
                val now = android.os.SystemClock.elapsedRealtime()
                unavailableUntil.entries.removeIf { it.value <= now }
                // Try another seed batch while an identity's relay list remains undiscovered.
                if (++ticks % 3 == 0 && (state.value.connected.isEmpty() || (state.value.publicKey != null && relayLists[state.value.publicKey] == null))) {
                    seedOffset = (seedOffset + 4) % RelayDefaults.seeds.size
                }
                if (ticks % 3 == 0 && embeddedIds.any { it !in state.value.embedded }) embeddedSeedOffset = (embeddedSeedOffset + 2) % RelayDefaults.seeds.size
                changed.tryEmit(Unit) // Retry deferred optional work without waiting for a new relay event.
                subscribeFeed()
            }
        }
    }
    @Synchronized
    fun background() { active = false; stopProfileSweep(); foregroundActive.value = false; blocktime.stop(); zapVerification.stop(); discoveryJob?.cancel(); historyJob?.cancel(); threadParentJob?.cancel(); refreshTimeout?.cancel(); pendingEventLookups = emptyMap(); client.unsubscribe("event-ids"); client.disconnect(); _state.update { it.copy(connected = emptySet(), loading = false) } }
    fun refresh() {
        historyJob?.cancel()
        if (viewedThread != null) {
            _state.update { it.copy(loading = true, error = null) }
            scope.launch {
                ready.await()
                threadChanges.tryEmit(Unit)
                if (active) {
                    client.unsubscribe("thread")
                    client.unsubscribe("thread-deletions")
                    changed.tryEmit(Unit); subscribeFeed()
                }
            }
            return
        }
        val hashtag = viewedHashtag
        if (hashtag != null) {
            val author = viewedHashtagAuthor
            val tab = selectedHashtagTab
            hashtagUntil = null; hashtagLimit = 200
            _state.update { it.copy(loading = true, error = null) }
            restoreHashtagCache()
            changed.tryEmit(Unit)
            scope.launch {
                ready.await()
                if (!active || viewedHashtag != hashtag || viewedHashtagAuthor != author || selectedHashtagTab != tab) return@launch
                client.unsubscribe("hashtag")
                client.subscribe("hashtag", hashtagFilters(hashtag, author = author, tab = tab), listener)
                client.connect()
                refreshTimeout?.cancel()
                refreshTimeout = scope.launch {
                    delay(12_000)
                    if (viewedHashtag == hashtag && viewedHashtagAuthor == author && selectedHashtagTab == tab)
                        _state.update { it.copy(loading = false) }
                }
            }
            return
        }
        val profile = viewedProfile.takeIf { viewedThread == null && viewedHashtag == null }
        if (profile != null) {
            profileUntil = null; profileLimit = 100
            _state.update { it.copy(loading = true, error = null) }
            scope.launch {
                ready.await()
                if (!active || viewedProfile != profile) return@launch
                // Explicit refresh must send a new REQ even when the filters are unchanged.
                relayLists[profile]?.write.orEmpty().forEach { unavailableUntil.remove(it) }
                updateRoutingPool()
                stopProfileSweep()
                client.unsubscribe("profile")
                if (profileTab == ProfileTab.APPS) client.unsubscribe("profile-apps")
                changed.tryEmit(Unit)
                subscribeProfile(profile)
                client.connect()
                refreshTimeout?.cancel()
                refreshTimeout = scope.launch {
                    delay(12_000)
                    _state.update { it.copy(loading = false) }
                }
            }
            return
        }
        feedUntil = null; feedCeilings.remove(selectedFeed); feedDiscoveryAt = 0; profileUntil = null; hashtagUntil = null
        profileLimit = 100; hashtagLimit = 200
        _state.update { it.copy(loading = true, error = null) }
        scope.launch { ready.await(); if (active) { changed.tryEmit(Unit); subscribeFeed(); client.connect() } }
    }

    /** Keep a small live subscription; finite profile sweeps cover every advertised write relay. */
    private fun profileFilters(author: String, kinds: List<Int>, limit: Int, until: Long? = null): Map<NormalizedRelayUrl, List<Filter>> {
        val writes = relayLists[author]?.write.orEmpty()
        val routes = (if (writes.isNotEmpty()) writes else state.value.relays).filter { it in routingPool && healthy(it) }.take(2)
        val filters = kinds.flatMap { kind -> Filter(authors = listOf(author), kinds = listOf(kind), limit = limit, until = until).activityFilters() } +
            if (17 in kinds) Filter(authors = listOf(author), kinds = kinds.filter { it != 17 }, limit = limit, until = until).podcastTagFilters() else emptyList()
        val result = routes.associate { it.normalizeRelayUrl() to filters }
        val catalog = if (32267 in kinds) result + (ZAPSTORE_RELAY.normalizeRelayUrl() to
            listOf(Filter(authors = listOf(author), kinds = listOf(32267), limit = limit, until = until))) else result
        return withIssueRoutes(catalog, listOf(author), kinds, limit, until)
    }
    private fun authorFilters(authors: List<String>?, kinds: List<Int>, limit: Int, until: Long? = null): Map<NormalizedRelayUrl, List<Filter>> {
        val routes = (if (authors == null) routingPool.filter { it != "wss://purplepag.es" }
            .associate { it.normalizeRelayUrl() to listOf(Filter(kinds = kinds, limit = limit, until = until)) }
        else Outbox.readRoutes(authors, relayLists, state.value.relays, allowed = routingPool).mapKeys { it.key.normalizeRelayUrl() }
            .mapValues { (_, keys) -> listOf(Filter(authors = keys, kinds = kinds, limit = limit, until = until)) }).mapValues { (_, filters) -> filters.flatMap { it.activityFilters() } }
        val catalog = if (32267 in kinds) routes + (ZAPSTORE_RELAY.normalizeRelayUrl() to
            listOf(Filter(authors = authors, kinds = listOf(32267), limit = limit, until = until))) else routes
        return withIssueRoutes(catalog, authors, kinds, limit, until)
    }
    private fun withIssueRoutes(routes: Map<NormalizedRelayUrl, List<Filter>>, authors: List<String>?, kinds: List<Int>, limit: Int, until: Long?): Map<NormalizedRelayUrl, List<Filter>> {
        if (1621 !in kinds) return routes
        val result = routes.toMutableMap()
        IssueReports.RELAYS.forEach { url ->
            val relay = url.normalizeRelayUrl()
            result[relay] = result[relay].orEmpty() + Filter(authors = authors, kinds = listOf(1621), limit = limit, until = until)
        }
        return result
    }
    private fun discoverRelays() {
        val s = state.value
        val threadAuthor = s.threadNotes.find { it.matchesThread(viewedThread) }?.pubKey
        val authors = (listOfNotNull(s.publicKey, viewedProfile, threadAuthor) + emojiPackRequests.map { it.author } + profileAuthors.take(64) + s.following.take(999) + s.notes.take(40).map { it.pubKey }.takeIf { s.publicKey == null }.orEmpty()).distinct()
        if (authors.isEmpty()) client.unsubscribe("relay-lists")
        else client.subscribe("relay-lists", routingPool.associate { it.normalizeRelayUrl() to listOf(Filter(authors = authors, kinds = listOf(10002), limit = authors.size)) }, listener)
    }
    // Use the same bounded routes for publishing and latest-version discovery, including
    // stable public fallbacks a fresh installation can find before it knows the relay list.
    private fun settingsRelayUrls(): List<String> {
        val owner = state.value.publicKey ?: return emptyList()
        val own = relayLists[owner]
        return (RelayDefaults.seeds.take(3) + own?.write.orEmpty().take(2) + own?.read.orEmpty().take(2))
            .mapNotNull(Protocol::relay).distinct().filter(::healthy)
    }
    private fun updateRoutingPool() {
        val own = relayLists[state.value.publicKey]
        val personal = (own?.read.orEmpty() + own?.write.orEmpty()).distinct().filter(::healthy).take(4)
        val seeds = (RelayDefaults.seeds.drop(seedOffset) + RelayDefaults.seeds.take(seedOffset)).filter(::healthy)
            .ifEmpty { RelayDefaults.seeds }
        // Keep a bootstrap route for finding relay-list updates beyond the account's own relays.
        val base = (personal.take(3) + seeds.take(4 - personal.take(3).size)).distinct()
        _state.update { it.copy(relays = base) }
        val s = state.value
        val threadAuthor = s.threadNotes.find { it.matchesThread(viewedThread) }?.pubKey
        val fallback = if (embeddedIds.any { it !in s.embedded })
            (RelayDefaults.seeds.drop(embeddedSeedOffset) + RelayDefaults.seeds.take(embeddedSeedOffset)).filter(::healthy).take(2)
            else emptyList()
        val publishRoutes = synchronized(publishingAuthors) { publishingAuthors.keys.toList() }.flatMap { author ->
            own?.write.orEmpty().filter(::healthy).take(2) + relayLists[author]?.read.orEmpty().filter(::healthy).take(2)
        }
        val priority = (publishRoutes + settingsRelayUrls() + relayLists[viewedProfile]?.write.orEmpty().take(2) + listOf("wss://purplepag.es") + openedReference?.relays.orEmpty() +
            references.flatMap { it.relays }.distinct().take(2) + emojiPackRequests.mapNotNull { it.relay }.distinct().take(2) +
            emojiPackRequests.flatMap { relayLists[it.author]?.write.orEmpty() }.distinct().take(2) + fallback +
            relayLists[threadAuthor]?.read.orEmpty().take(2) + relayLists[threadAuthor]?.write.orEmpty().take(2) +
            threadBranchAuthors().flatMap { relayLists[it]?.read.orEmpty().take(1) + relayLists[it]?.write.orEmpty().take(1) }.distinct().take(3) +
            own?.read.orEmpty() + own?.write.orEmpty() +
            profileAuthors.take(12).flatMap { relayLists[it]?.write.orEmpty().take(1) }).distinct().filter(::healthy)
        val authors = (listOfNotNull(s.publicKey, viewedProfile, threadAuthor) + profileAuthors.take(64) + s.following.take(999) + s.notes.take(40).map { it.pubKey }.takeIf { s.publicKey == null }.orEmpty()).distinct()
        val usableLists = relayLists.mapValues { (_, list) -> Outbox.RelayList(list.read.filter(::healthy), list.write.filter(::healthy)) }
        val popular = Outbox.readRoutes(authors, usableLists, s.relays).keys
        val next = (s.relays + RECIPE_RELAY + (priority + popular).mapNotNull(Protocol::relay).distinct().filter { it !in s.relays }.take(8)).toSet()
        if (next != routingPool) {
            routingPool = next
            historyJob?.cancel()
            client.unsubscribe("profiles")
        }
    }
    @Synchronized
    private fun subscribeFeed() {
        if (!active) return
        updateRoutingPool()
        viewedHashtag?.let { client.subscribe("hashtag", hashtagFilters(it, author = viewedHashtagAuthor, tab = selectedHashtagTab), listener) }
        val s = _state.value
        discoverRelays()
        s.publicKey?.let { key ->
            val accountRoutes = authorFilters(listOf(key), listOf(0), 1)
            // Following is essential to the home feed. Request it independently from metadata,
            // including existing bootstrap relays that may have it when write relays do not.
            val contactRelays = (accountRoutes.keys + s.relays.map { it.normalizeRelayUrl() } +
                routingPool.filter { it == "wss://purplepag.es" }.map { it.normalizeRelayUrl() }).distinct()
            client.subscribe("contacts", contactRelays.associateWith {
                listOf(Filter(authors = listOf(key), kinds = listOf(3), limit = 1),
                    Filter(authors = listOf(key), kinds = listOf(10015), limit = 1))
            }, listener)
            val settingsRoutes = settingsRelayUrls().filter { it in routingPool }
            client.subscribe("account-settings", settingsRoutes.associate { it.normalizeRelayUrl() to
                listOf(Filter(authors = listOf(key), kinds = listOf(30078), tags = mapOf("d" to listOf(SETTINGS_D)), limit = 1))
            }, listener)
            val routes = accountRoutes.mapValues { (_, filters) ->
                filters + Filter(authors = listOf(key), kinds = listOf(7), limit = 200) +
                    Filter(authors = listOf(key), kinds = listOf(5), limit = 256) +
                    Filter(authors = listOf(key), kinds = listOf(10000), limit = 1) +
                    Filter(authors = listOf(key), kinds = listOf(10030), limit = 1) +
                    Filter(authors = listOf(key), kinds = listOf(10063), limit = 1) +
                    Filter(authors = listOf(key), kinds = listOf(30000), tags = mapOf("d" to listOf("mute")), limit = 1)
            }
            client.subscribe("account", routes, listener)
        }
            ?: run { client.unsubscribe("account"); client.unsubscribe("contacts"); client.unsubscribe("account-settings") }
        val authors = s.publicKey?.let { (listOf(it) + s.following).take(1000) }
        val feedRoutes = when (selectedFeed) {
            ProfileTab.RECIPES -> recipeRoutes()
            ProfileTab.HASHTAGS -> topicFilters()
            ProfileTab.POSTS -> homeFilters(authors, s.followedHashtags)
            else -> authorFilters(authors, selectedFeed.feedKinds(), 80)
        }
        client.subscribe("feed", feedRoutes, listener)
        val discovery = authorFilters(authors, profileKinds, 8).mapValues { (_, filters) ->
            filters.flatMap { filter -> filter.kinds.orEmpty().map { kind ->
                Filter(authors = filter.authors, kinds = listOf(kind), tags = filter.tags, limit = 8)
            } }
        }
        val topicDiscovery = topicFilters().mapValues { (_, filters) -> filters.map {
            Filter(kinds = it.kinds, since = it.since, limit = 80)
        } }
        val recipeDiscovery = recipeRoutes(limit = 8)
        client.subscribe("feed-discovery", (discovery.keys + topicDiscovery.keys + recipeDiscovery.keys).associateWith {
            discovery[it].orEmpty() + topicDiscovery[it].orEmpty() + recipeDiscovery[it].orEmpty()
        }, listener)
        viewedProfile?.let { subscribeProfile(it) }
        viewedThread?.let { subscribeThread(it) }
        subscribeReadingData()
        subscribeNotifications()
        if (refreshTimeout?.isActive == true) return
        refreshTimeout = scope.launch {
            delay(12_000)
            _state.update { it.copy(loading = false, error = if (it.connected.isEmpty()) "No relay connection. Cached notes are still available." else it.error) }
        }
    }
    /** Notifications use only the active account’s bounded inbox subscription. */
    fun viewNotifications(show: Boolean) {
        if (viewingNotifications == show) return
        viewingNotifications = show
        if (show) refreshNotifications()
        else { notificationTimeout?.cancel(); client.unsubscribe("notifications"); _state.update { it.copy(notificationsLoading = false) } }
        scope.launch { ready.await(); changed.tryEmit(Unit); if (active) subscribeFeed() }
    }
    fun refreshNotifications() {
        if (!viewingNotifications || state.value.publicKey == null) return
        notificationTimeout?.cancel()
        _state.update { it.copy(notificationsLoading = true) }
        notificationTimeout = scope.launch {
            ready.await()
            client.unsubscribe("notifications")
            if (active) { updateRoutingPool(); subscribeNotifications() }
            changed.tryEmit(Unit)
            delay(8_000)
            _state.update { it.copy(notificationsLoading = false) }
        }
    }
    internal fun repositoryIssues(address: String, hints: List<String>, requestedLimit: Int): Flow<RepositoryIssueSnapshot> = channelFlow {
        ready.await()
        val repositoryFilter = issueRepositoryFilter(address) ?: return@channelFlow
        val limit = requestedLimit.coerceIn(100, 600)
        val issueFilter = Filter(kinds = listOf(1621), tags = mapOf("a" to listOf(address)), limit = limit)
        var routes = (hints + IssueReports.RELAYS + state.value.relays).mapNotNull(Protocol::relay).distinct().take(6)
        suspend fun snapshot(loading: Boolean = true, failed: Boolean = false): RepositoryIssueSnapshot = mutex.withLock {
            val repository = db.query<Event>(repositoryFilter).maxByOrNull { it.createdAt }
            val issues = db.query<Event>(issueFilter).filterNot(noteDeletions::hides)
                .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
            val statuses = issues.associate { issue ->
                val repos = listOfNotNull(repository)
                issue.id to latestIssueStatus(issue, repos, db.query<Event>(Filter(
                    kinds = issueStatusKinds, authors = issueStatusAuthors(issue, repos),
                    tags = mapOf("e" to listOf(issue.id)), limit = 64,
                )))
            }
            RepositoryIssueSnapshot(issues, statuses, routes, loading, failed, issues.size >= limit)
        }
        send(snapshot())
        val updates = Channel<Unit>(Channel.CONFLATED)
        val updater = launch { for (signal in updates) { delay(150); send(snapshot()) } }
        try {
            suspend fun acceptIssue(relay: String, event: Event): Boolean {
                val matches = (event.kind == 30617 && ThreadRelations.address(event) == address) ||
                    (event.kind == 1621 && address in issueRepositories(event))
                val valid = matches && accept(event, seenAt = relay)
                if (valid) updates.trySend(Unit)
                return valid
            }
            val initialRoutes = routes
            val completed = profileQueries.completed(initialRoutes,
                listOf(repositoryFilter.toJson(), issueFilter.toJson()), ::acceptIssue).toMutableSet()
            val repo = mutex.withLock { db.query<Event>(repositoryFilter).maxByOrNull { it.createdAt } }
            routes = (repo?.tags.orEmpty().filter { it.firstOrNull() == "relays" }.flatMap { it.drop(1) } + routes)
                .mapNotNull(Protocol::relay).distinct().take(6)
            val additional = routes - initialRoutes.toSet()
            if (additional.isNotEmpty()) completed += profileQueries.completed(additional, listOf(issueFilter.toJson()), ::acceptIssue)
            val issues = snapshot().issues
            val statusFilters = issues.chunked(40).map { batch -> Filter(kinds = issueStatusKinds,
                authors = batch.flatMap { issueStatusAuthors(it, listOfNotNull(repo)) }.distinct(),
                tags = mapOf("e" to batch.map { it.id }), limit = 400).toJson() }
            val issueIds = issues.map { it.id }.toSet()
            val authors = issues.flatMap { issueStatusAuthors(it, listOfNotNull(repo)) }.toSet()
            if (statusFilters.isNotEmpty()) profileQueries.fetch(routes, statusFilters) { relay, event ->
                val valid = event.kind in issueStatusKinds && event.pubKey in authors &&
                    event.tags.any { it.firstOrNull() == "e" && it.getOrNull(1) in issueIds } && accept(event, seenAt = relay)
                if (valid) updates.trySend(Unit)
                valid
            }
            updates.close(); updater.join()
            send(snapshot(loading = false, failed = completed.isEmpty()))
        } finally { updates.close(); updater.cancel() }
    }.flowOn(Dispatchers.IO)

    private suspend fun refreshIssueStatuses(countTargets: List<Event>) {
        visibleIssues = countTargets.filter { it.kind == 1621 }.take(8)
        val statuses = mutex.withLock {
            issueRepositoryEvents = visibleIssues.associate { issue -> issue.id to issueRepositories(issue).mapNotNull { address ->
                db.query<Event>(issueRepositoryFilter(address)!!).sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).firstOrNull()
            } }
            visibleIssues.associate { issue ->
                val repos = issueRepositoryEvents[issue.id].orEmpty()
                issue.id to latestIssueStatus(issue, repos, db.query<Event>(Filter(
                    kinds = issueStatusKinds, authors = issueStatusAuthors(issue, repos),
                    tags = mapOf("e" to listOf(issue.id)), limit = 64,
                )))
            }
        }
        _state.update { current -> current.copy(issueStatuses =
            (current.issueStatuses + statuses).entries.toList().takeLast(128).associate { it.toPair() }) }
    }

    private fun subscribeNotifications() {
        val owner = state.value.publicKey
        if (!viewingNotifications || owner == null) { client.unsubscribe("notifications"); return }
        val inbox = relayLists[owner]?.read.orEmpty().filter { it in routingPool && healthy(it) }
            .ifEmpty { state.value.relays }.take(4)
        client.subscribe("notifications", inbox.associate { relay -> relay.normalizeRelayUrl() to
            notificationKinds.map { kind -> Filter(kinds = listOf(kind), tags = mapOf("p" to listOf(owner)), limit = 100) }
        }, listener)
    }
    /** Small batches keep metadata filters within common relay request limits. */
    private fun subscribeReadingData() {
        if (!active) return
        val packs = emojiPackRequests.takeIf { state.value.publicKey != null }.orEmpty()
        if (packs.isEmpty()) client.unsubscribe("emoji-packs")
        else client.subscribe("emoji-packs", routingPool.filter { it != "wss://purplepag.es" }.associate {
            it.normalizeRelayUrl() to packs.map { pack -> pack.filter() }
        }, listener)

        val deletionNotes = deletionTargets
        if (deletionNotes.isEmpty()) client.unsubscribe("visible-deletions")
        else {
            val filters = listOf(Filter(kinds = listOf(5), tags = mapOf("e" to deletionNotes.map { it.id }.sorted()), limit = 256)) +
                deletionNotes.mapNotNull(ThreadRelations::address).distinct().sorted().takeIf { it.isNotEmpty() }
                    ?.let { listOf(Filter(kinds = listOf(5), tags = mapOf("a" to it), limit = 256)) }.orEmpty()
            val routes = (deletionNotes.flatMap { relayLists[it.pubKey]?.write.orEmpty() } + routingPool)
                .distinct().filter { it != "wss://purplepag.es" && healthy(it) }.take(8)
            client.subscribe("visible-deletions", routes.associate { it.normalizeRelayUrl() to filters }, listener)
        }

        val targets = (listOfNotNull(state.value.publicKey, viewedProfile) + profileAuthors + detailAuthors.values.flatten().distinct().take(64)).distinct().take(160)
        val metadata = routingPool.associate { url ->
            url.normalizeRelayUrl() to targets.chunked(16).map { Filter(authors = it, kinds = listOf(0), limit = it.size) }
        }.filterValues { it.isNotEmpty() }
        if (metadata.isEmpty()) client.unsubscribe("profiles")
        else client.subscribe("profiles", metadata, listener)
        if (engagementIds.isEmpty()) client.unsubscribe("engagement")
        else client.subscribe("engagement", (routingPool.filter { it != "wss://purplepag.es" } + if (engagementApps.isNotEmpty() || state.value.threadNotes.any { it.isAppDiscussion() }) listOf(ZAPSTORE_RELAY) else emptyList()).distinct().associate { url ->
            url.normalizeRelayUrl() to (engagementIds.chunked(6).flatMap { ids ->
                listOf(1, 1111, 1244, 6, 16, 7, 9735).map { kind -> Filter(kinds = listOf(kind), tags = mapOf("e" to ids), limit = 300) }
            } + engagementApps.map { Filter(kinds = listOf(1111), tags = mapOf("A" to listOf(it)), limit = 200) })
        }, listener)
        val issueRoutes = linkedMapOf<String, MutableList<Filter>>()
        visibleIssues.forEach { issue ->
            val repos = issueRepositoryEvents[issue.id].orEmpty()
            val addresses = issueRepositories(issue)
            val filters = addresses.mapNotNull(::issueRepositoryFilter) + Filter(
                kinds = issueStatusKinds, authors = issueStatusAuthors(issue, repos),
                tags = mapOf("e" to listOf(issue.id)), limit = 64,
            )
            val hints = issue.tags.filter { it.firstOrNull() == "a" }.mapNotNull { it.getOrNull(2)?.let(Protocol::relay) }
            val advertised = repos.flatMap { repo -> repo.tags.filter { it.firstOrNull() == "relays" }
                .flatMap { it.drop(1) }.mapNotNull(Protocol::relay) }
            (advertised + hints + IssueReports.RELAYS + routingPool).distinct()
                .filter { it != "wss://purplepag.es" && healthy(it) }.take(6).forEach { relay ->
                    if (relay in issueRoutes || issueRoutes.size < 8) issueRoutes.getOrPut(relay) { mutableListOf() }.addAll(filters)
                }
        }
        if (issueRoutes.isEmpty()) client.unsubscribe("issue-status")
        else client.subscribe("issue-status", issueRoutes.mapKeys { it.key.normalizeRelayUrl() }
            .mapValues { it.value.distinctBy { filter -> filter.toJson() } }, listener)
        val pollRoutes = linkedMapOf<String, MutableList<Filter>>()
        visiblePolls.forEach { event ->
            val poll = parsePoll(event) ?: return@forEach
            val routes = (poll.relays + relayLists[event.pubKey]?.write.orEmpty() + routingPool)
                .distinct().filter { it != "wss://purplepag.es" && healthy(it) }.take(8)
            routes.forEach relay@{ relay ->
                if (relay !in pollRoutes && pollRoutes.size >= 12) return@relay
                pollRoutes.getOrPut(relay) { mutableListOf() }.add(
                Filter(kinds = listOf(1018), tags = mapOf("e" to listOf(event.id)), since = event.createdAt, until = poll.endsAt, limit = 2000)) }
        }
        if (pollRoutes.isEmpty()) client.unsubscribe("poll-votes")
        else client.subscribe("poll-votes", pollRoutes.mapKeys { it.key.normalizeRelayUrl() }, listener)
        val owner = state.value.publicKey
        if (owner == null || engagementIds.isEmpty()) client.unsubscribe("own-reactions")
        else {
            // Dedicated author filters cannot be crowded out by other people's reactions.
            val routes = (relayLists[owner]?.write.orEmpty() + state.value.relays)
                .distinct().filter { it in routingPool && healthy(it) }.take(4)
            client.subscribe("own-reactions", routes.associate { relay -> relay.normalizeRelayUrl() to
                engagementIds.chunked(6).map { targets ->
                    Filter(authors = listOf(owner), kinds = listOf(7), tags = mapOf("e" to targets), limit = 128)
                }
            }, listener)
        }
        // Keep address filters live while visible so replacement article versions arrive.
        val addressFilters = (references.filter { it.id in embeddedIds } + threadContext?.takeIf { it.target == viewedThread }?.missing.orEmpty())
            .distinctBy { it.id }.mapNotNull { it.addressFilter() }.take(24)
        if (addressFilters.isEmpty()) client.unsubscribe("embedded")
        else {
            val routes = routingPool.filter { it != "wss://purplepag.es" }.associate { it.normalizeRelayUrl() to addressFilters }
            routes.forEach { (relay, filters) -> threadProgress.sent(relay.url, filters) }
            client.subscribe("embedded", routes, listener)
        }
        eventLookupChanges.trySend(Unit)
    }

    /** Exact IDs are immutable. Always resolve complete memory/disk copies before relay I/O. */
    private suspend fun cachedEventsLocked(ids: List<String>): Map<String, Event> {
        val wanted = ids.filter(eventIdPattern::matches).distinct()
        if (wanted.isEmpty()) return emptyMap()
        val wantedIds = wanted.toHashSet()
        val s = state.value
        val memory = sequenceOf(
            exactEventCache.values.asSequence(), s.embedded.values.asSequence(), s.threadNotes.asSequence(),
            s.notes.asSequence(), s.profileEvents.asSequence(), s.hashtagNotes.asSequence(),
            s.notifications.asSequence(), s.feedPreviews.values.asSequence().flatten(),
            olderEvents.asSequence(), listOfNotNull(galleryNote).asSequence(),
            recentThreads.events(wanted).asSequence(),
            postOutbox.records().asSequence().flatMap { listOfNotNull(it.event, it.target).asSequence() },
        ).flatten().filter { it.id in wantedIds }.associateBy { it.id }
        val missing = wanted.filterNot(memory::containsKey)
        val disk = if (missing.isEmpty()) emptyList() else db.query<Event>(Filter(ids = missing, limit = missing.size))
        return (memory + disk.associateBy { it.id }).also { found ->
            found.forEach { (id, event) -> exactEventCache[id] = event }
        }
    }

    /** One thread root, eight missing parents and 24 visible embeds; one shared request path. */
    private fun wantedEventLookups(): Map<String, Set<String>> {
        val result = linkedMapOf<String, Set<String>>()
        viewedThread?.takeIf(eventIdPattern::matches)?.let { id -> result[id] = threadRelays(id).toSet() }
        val thread = viewedThread
        threadContext?.takeIf { it.target == thread }?.missing.orEmpty().take(8).forEach { ref ->
            if (eventIdPattern.matches(ref.id) && (ref.kind == null || ref.kind in readableKinds)) {
                result[ref.id] = (thread?.let(::threadRelays).orEmpty() + ref.relays + relayLists[ref.author]?.write.orEmpty())
                    .filter { it in routingPool }.toSet()
            }
        }
        val embeddedRelays = routingPool.filter { it != "wss://purplepag.es" }.toSet()
        embeddedIds.asSequence().filter(eventIdPattern::matches).take(24).forEach { id ->
            result[id] = result[id].orEmpty() + embeddedRelays
        }
        return result.filterKeys { it !in state.value.deletedNotes }
    }

    private suspend fun reconcileEventLookups(retryId: String? = null) = mutex.withLock {
        if (!active) return@withLock
        val wanted = wantedEventLookups()
        val cached = cachedEventsLocked(wanted.keys.toList())
        synchronized(this) {
            if (!active) return@synchronized
            // Navigation/routing may have changed while SQLite was reading. Reconcile the latest intent.
            if (wanted != wantedEventLookups()) { eventLookupChanges.trySend(Unit); return@synchronized }
            pendingEventLookups = wanted.filterKeys { it !in cached }
            // Explicit retry of an exhausted lookup only, after checking the cache again.
            // Restart the shared subscription so all consumers still share one request per ID.
            if (retryId != null && retryId in pendingEventLookups) client.unsubscribe("event-ids")
            sendEventLookups()
        }
        // Let every consumer render the same cached copy, even if it came from another screen.
        val readable = cached.filterValues { it.kind in readableKinds }
        if (readable.any { (id, event) -> state.value.embedded[id]?.id != event.id }) {
            _state.update { s -> s.copy(embedded = (s.embedded + readable).entries.toList().takeLast(128).associate { it.toPair() }) }
            changed.tryEmit(Unit)
        }
    }

    /** Called under the cache mutex after validation, including deliveries on discovery subscriptions. */
    private fun completeEventLookup(event: Event) = synchronized(this) {
        if (event.id !in pendingEventLookups) return@synchronized
        exactEventCache[event.id] = event
        pendingEventLookups = pendingEventLookups - event.id
        sendEventLookups()
        changed.tryEmit(Unit)
    }

    @Synchronized
    private fun sendEventLookups() {
        val routes = pendingEventLookups.values.flatten().distinct().sorted().associate { relay ->
            val ids = pendingEventLookups.filterValues { relay in it }.keys.sorted()
            relay.normalizeRelayUrl() to listOf(Filter(ids = ids, kinds = readableKinds, limit = ids.size))
        }
        if (!active || routes.isEmpty()) client.unsubscribe("event-ids")
        else {
            routes.forEach { (relay, filters) -> threadProgress.sent(relay.url, filters) }
            client.subscribe("event-ids", routes, listener)
        }
    }

    internal suspend fun cacheSearchProfile(event: Event) = withContext(Dispatchers.IO) {
        if (event.kind == 0) accept(event)
    }

    internal suspend fun cacheIssue(event: Event) = withContext(Dispatchers.IO) {
        ready.await()
        if (event.kind == 1621) { accept(event); feedDiscoveryAt = 0; changed.tryEmit(Unit) }
    }

    private suspend fun accept(event: Event, history: Boolean = false, seenAt: String? = null): Boolean = withContext(Dispatchers.IO) {
        val accepted = acceptOnIo(event, history, seenAt)
        // A local cache hit is not delivery evidence. Independently verify the relay
        // payload even when acceptOnIo took its already-cached fast path.
        if (accepted && seenAt?.let(Protocol::relay) != null && event.id in postOutbox.ids.value && event.verify()) {
            runCatching { confirmQueuedEvent(event.id) }
            outboxWake.trySend(Unit)
        }
        if (accepted && event.kind == 5) outboxWake.trySend(Unit)
        accepted
    }

    private suspend fun acceptOnIo(event: Event, history: Boolean, seenAt: String?): Boolean {
        if (event.kind == 5 && event.tags.count { it.firstOrNull() == "e" } > 256) return false
        if (event.kind == 30078 && (event.pubKey != state.value.publicKey || event.tagValue("d") != SETTINGS_D || event.content.length > 8192)) return false
        if (event.kind !in (profileKinds + issueStatusKinds + listOf(0, 3, 5, 6, 1018, 9735, 10015, 10000, 10002, 10030, 10063, 30030, 30078)) || event.content.length > (if (event.kind in listOf(1621, 30023, 10000, 10015) || (event.kind == 30000 && event.tagValue("d") == "mute")) 262_144 else 32_768) || event.tags.size > 5000 || event.createdAt > System.currentTimeMillis() / 1000 + 600) return false
        if (event.kind == 17 && !isPodcastActivity(event)) return false
        mutex.withLock {
            val cached = verified[event.id]
            if (cached == false) return false
            val requested = synchronized(this) { event.id in pendingEventLookups }
            // A repeated claimed ID is not proof that this payload is the verified copy.
            // Discard duplicates; never promote their unchecked contents into the lookup cache.
            if (cached == true && !history && !requested) {
                if (seenAt != null && Protocol.relay(seenAt) !in eventRelays.read(event.id) && event.verify())
                    recordRelayLocked(event.id, seenAt)
                return true
            }
            if (!event.verify()) return false
            recordRelayLocked(event.id, seenAt)
            if (event.kind == 5) {
                noteDeletions.put(event)
                val originals = cachedEventsLocked(event.tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }).values
                rememberDeleted(originals + cachedViewEvents() + exactEventCache.values)
                changed.tryEmit(Unit)
                threadChanges.tryEmit(Unit)
            } else if (noteDeletions.hides(event)) {
                rememberDeleted(listOf(event))
                changed.tryEmit(Unit)
                return false
            }
            // An explicit miss can outlive the stored copy (cache eviction). Re-cache it even
            // when its ID remains in the small verification memo.
            if (cached == null || requested) {
                // Quartz rolls back rejected rows and distinguishes normal NIP-01
                // replacement/duplicate outcomes from actual storage failures.
                val outcome = try { db.batchInsert(listOf(event)).single() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _state.update { it.copy(error = "Could not cache event: ${e.message ?: e.javaClass.simpleName}") }
                    return false
                }
                when (outcome) {
                    IEventStore.InsertOutcome.Accepted -> verified[event.id] = true
                    is IEventStore.InsertOutcome.Rejected -> {
                        val duplicate = outcome.reason.startsWith("duplicate:")
                        verified[event.id] = duplicate
                        if (!duplicate) {
                            if (outcome.reason.startsWith("replaced:")) {
                                // An exact link can intentionally name an older article version.
                                // Retain that verified copy for the lookup without replacing the
                                // newer addressable record used by discovery.
                                verified.remove(event.id)
                                completeEventLookup(event)
                            }
                            return false
                        }
                    }
                    is IEventStore.InsertOutcome.Failed -> {
                        // Do not mark failed writes as cached: a later relay delivery can retry.
                        _state.update { it.copy(error = "Could not cache event: ${outcome.reason}") }
                        return false
                    }
                }
                // Protect an older reply as soon as it arrives, before any cache pruning.
                retainThreadEvent(event)
                if (outcome == IEventStore.InsertOutcome.Accepted && ++inserts % 200 == 0) trimCacheLocked()
            }
            completeEventLookup(event)
            if (history) olderEvents = (olderEvents + event).distinctBy { it.id }
                .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).takeLast(600)
        }
        if (event.kind == 5 && event.pubKey == state.value.publicKey) {
            reactionDeletions.put(event, accepted = true)
            refreshDeletedReactions()
        }
        if (event.kind in listOf(6, 16) && event.content.isNotBlank()) {
            val target = repostTarget(event)
            val original = runCatching { Event.fromJson(event.content) }.getOrNull()
                ?.takeIf { it.kind in readableKinds && (target?.kind == null || it.kind == target.kind) && it.id == target?.id && (target.author == null || it.pubKey == target.author) }
            // Independently validate the enclosed original on IO before storing/displaying it.
            if (original != null) accept(original)
        }
        if (viewedHashtag?.let { tag -> event.tags.any { it.firstOrNull() == "t" && it.getOrNull(1) == tag } } == true ||
            viewedProfile?.let { it == event.pubKey } == true) scopedContentChanges.tryEmit(Unit)
        changed.tryEmit(Unit)
        if (viewedThread != null && event.kind in (readableKinds + 5)) threadChanges.tryEmit(Unit)
        return true
    }
    private fun cachedViewEvents(): List<Event> = state.value.let {
        it.notes + it.profileEvents + it.hashtagNotes + it.notifications + it.threadNotes +
            it.embedded.values + it.feedPreviews.values.flatten() + olderEvents + listOfNotNull(galleryNote)
    }

    private fun rememberDeleted(events: Collection<Event>) {
        val deleted = events.filter(noteDeletions::hides).map { it.id }
        if (deleted.isNotEmpty()) _state.update {
            it.copy(deletedNotes = (it.deletedNotes + deleted).toList().takeLast(4096).toSet())
        }
    }

    private suspend fun timeline(filter: Filter, previous: List<Event>, limit: Int): List<Event> = mutex.withLock {
        val filters = filter.activityFilters()
        val now = System.currentTimeMillis() / 1000
        val matching = (db.query<Event>(filters) + previous + olderEvents + postOutbox.records().map { it.event }).filter { event -> !noteDeletions.hides(event) && filters.any { it.match(event) } &&
            (filter.kinds != podcastKinds || isPodcastActivity(event)) }
        // Sparse posts/replies tabs can have visible items far ahead of their raw-event tail.
        // Keep these anchors when advancing the bounded window.
        val pinned = if (filter.until != null) previous.filter { it.id in visibleIds } else emptyList()
        (matching + pinned).asSequence()
            .filterNot { it.tagValue("expiration")?.toLongOrNull()?.let { expiry -> expiry <= now } == true }
            .filterNot(noteDeletions::hides).filterNot(::engagementDeleted).distinctBy { it.id }
            .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).take(limit).toList()
    }
    private suspend fun snapshot(initialFeedOnly: Boolean = false) = snapshotMutex.withLock { snapshotContents(initialFeedOnly) }

    private data class ThreadRequest(val id: String, val generation: Long, val account: String?, val sensitive: Boolean, val muted: Set<String>, val words: Set<String>,
        val ancestors: Int, val expanded: Set<String>, val requestedParent: NostrContent.Reference?,
        val reference: NostrContent.Reference?, val parents: List<String>)

    @Synchronized
    private fun threadRequest(): ThreadRequest? = viewedThread?.let {
        ThreadRequest(it, threadGeneration.get(), state.value.publicKey, state.value.showSensitive, state.value.mutedAuthors, state.value.mutedWords,
            threadAncestorLimit, threadExpanded, threadRequestedParent, openedReference?.takeIf { ref -> ref.id == it }, threadParents)
    }

    /** A dedicated cache-only path. Feed, profile and engagement work never gates this display. */
    private suspend fun refreshConversation() {
        try { updateConversation() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Keep the existing view and the update collector alive after a transient cache error.
            android.util.Log.w("FeatherRepository", "Could not restore the cached conversation", e)
        }
    }

    private suspend fun updateConversation() = threadMutex.withLock {
        val request = threadRequest() ?: return@withLock
        val muteList = request.account?.let { author -> mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(10000), authors = listOf(author), limit = 1)).firstOrNull()
                ?: db.query<Event>(Filter(kinds = listOf(30000), authors = listOf(author), tags = mapOf("d" to listOf("mute")), limit = 1)).firstOrNull()
        } }
        val muted = request.muted + mutedPublicKeys(muteList) + muteList?.takeIf { it.content.isNotBlank() }?.let { muteCache.read(it, exact = false) }.orEmpty()
        val (notes, view, deletions) = loadConversation(request, muted)
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            if (threadRequest() != request) return@withLock
            recentThreads.remember(request.id, notes, deletions)
            threadContext = view
            if (request.requestedParent != null && view?.rows.orEmpty().none {
                    it.gap == ThreadGap.MISSING && it.reference?.id == request.requestedParent.id
                }) threadRequestedParent = null
            val focus = notes.firstOrNull { it.id == request.id || ThreadRelations.address(it) == request.id }
            val replyRows = view?.rows.orEmpty().filter { !it.ancestor }.mapNotNull { row ->
                row.event ?: row.mutedCopy?.takeUnless { copy ->
                    copy.id !in threadExpanded && focus?.let { ThreadRelations.repliesTo(copy, it) } == true
                }
            }
            threadParents = (threadExpanded.toList().takeLast(4) + replyRows.filter { it.id in visibleIds }.map { it.id } +
                replyRows.take(6).map { it.id }).distinct().take(11)
            _state.update { it.copy(threadNotes = notes, conversation = view,
                loading = if (view != null) false else it.loading,
                deletedNotes = (it.deletedNotes + view?.deleted.orEmpty()).toList().takeLast(4096).toSet()) }
            if (view != null) threadProgress.expect(request.id,
                (threadRequestedParent ?: view.rows.firstOrNull { it.ancestor && it.gap == ThreadGap.MISSING }?.reference)
                    ?.takeIf { it.kind == null || it.kind in readableKinds })
        }
        changed.tryEmit(Unit) // Author pictures and engagement can fill in independently.
        if (active && viewedThread == request.id) { subscribeThread(request.id); subscribeReadingData() }
    }

    /** Retain only events connected to the opened thread, never the concurrent general feed. */
    private fun retainThreadEvent(event: Event) {
        val id = viewedThread ?: return
        val current = state.value.threadNotes.takeIf { state.value.threadId == id }.orEmpty()
        val known = (recentThreads.ids(id) + current.flatMap { listOfNotNull(it.id, ThreadRelations.address(it)) } + id).toSet()
        val missing = threadContext?.takeIf { it.target == id }?.missing.orEmpty().map { it.id }
        if (event.kind == 5) {
            if (event.tags.any { it.firstOrNull() in listOf("e", "a") && it.getOrNull(1) in known })
                recentThreads.remember(id, emptyList(), listOf(event))
        } else if (event.kind in readableKinds && (event.id in known || event.id in missing || event.matchesThread(id) ||
                ThreadRelations.parent(event)?.id in known || ThreadRelations.root(event)?.id in known)) {
            recentThreads.remember(id, listOf(event))
        }
    }

    /** Resolve ancestry locally first, then project a bounded graph around the selected event. */
    private suspend fun loadConversation(request: ThreadRequest, mutedAuthors: Set<String>): Triple<List<Event>, Conversation?, List<Event>> {
        val id = request.id
        val ancestorLimit = request.ancestors
        val requestedParent = request.requestedParent
        val addressFilter = request.reference?.addressFilter()
        val remembered = mutex.withLock { cachedEventsLocked(recentThreads.ids(id)).values.toList() }
        val focus = mutex.withLock {
            if (addressFilter != null) db.query<Event>(addressFilter).firstOrNull()
            else cachedEventsLocked(listOf(id))[id]?.takeIf { it.kind in readableKinds }
        } ?: (state.value.threadNotes + remembered).find { it.matchesThread(id) } ?: return Triple(emptyList(), null, emptyList())
        val filter = ContentFilter(mutedAuthors, state.value.embedded, showSensitive = request.sensitive, deletedNotes = state.value.deletedNotes, words = request.words)
        val chainFilter = filter.copy(authors = emptySet())
        val ancestors = linkedMapOf(focus.id to focus)
        var cursor = focus
        for (depth in 0..ancestorLimit) {
            val ref = ThreadRelations.parent(cursor) ?: break
            if (ref.id in ancestors || chainFilter.hides(cursor) || (ref.kind != null && ref.kind !in readableKinds)) break
            val parent = mutex.withLock {
                ref.addressFilter()?.let { db.query<Event>(it).firstOrNull() } ?: cachedEventsLocked(listOf(ref.id))[ref.id]
            } ?: break
            if (chainFilter.hides(parent)) { ancestors[parent.id] = parent; break }
            ancestors[parent.id] = parent; cursor = parent
        }
        val cached = mutex.withLock { db.query<Event>(ThreadRelations.discovery(focus, id, request.parents)) }
        val retained = state.value.threadNotes.takeIf { state.value.threadId == id }.orEmpty()
        val pool = (ancestors.values + retained.filter { it.id in visibleIds } + remembered + cached + retained).distinctBy { it.id }.take(320).associateByTo(linkedMapOf()) { it.id }
        val missingParents = (listOfNotNull(requestedParent) + threadContext?.takeIf { it.target == id }?.missing.orEmpty() + pool.values.mapNotNull(ThreadRelations::parent))
            .filter { it.id !in pool && (it.kind == null || it.kind in readableKinds) }.distinctBy { it.id }.take(32)
        val resolvedParents = mutex.withLock {
            cachedEventsLocked(missingParents.map { it.id }).values.toList() + missingParents.mapNotNull { ref ->
                ref.addressFilter()?.let { db.query<Event>(it).firstOrNull() }
            }
        }
        val withParents = (ancestors.values + resolvedParents + pool.values).distinctBy { it.id }.take(320)
        pool.clear(); withParents.forEach { pool[it.id] = it }
        // Root-tagged replies arrive together. Parent-only legacy threads need a few local joins.
        var parents = listOf(focus)
        repeat(3) {
            val ids = parents.filterNot(chainFilter::hides).map { it.id }.distinct().take(32)
            if (ids.isNotEmpty()) {
                val children = mutex.withLock { db.query<Event>(Filter(kinds = listOf(1, 1111, 1244), tags = mapOf("e" to ids), limit = 200)) }
                children.forEach { if (pool.size < 320) pool.putIfAbsent(it.id, it) }
                val parentKeys = parents.flatMap { listOfNotNull(it.id, ThreadRelations.address(it)) }.toSet()
                parents = pool.values.filter { ThreadRelations.parent(it)?.id in parentKeys }
            }
        }
        val addresses = pool.values.mapNotNull(ThreadRelations::address).distinct()
        val deletions = mutex.withLock {
            (cachedEventsLocked(recentThreads.deletionIds(id)).values +
                db.query<Event>(listOf(Filter(kinds = listOf(5), tags = mapOf("e" to pool.keys.toList()), limit = 256)) +
                    if (addresses.isEmpty()) emptyList() else listOf(Filter(kinds = listOf(5), tags = mapOf("a" to addresses), limit = 256))))
                .filter { it.kind == 5 }.distinctBy { it.id }
        }
        val deletedIds = mutableSetOf<Pair<String, String>>()
        val deletedAddresses = mutableMapOf<Pair<String, String>, Long>()
        deletions.forEach { deletion -> deletion.tags.forEach { tag ->
            val value = tag.getOrNull(1)
            if (value != null) when {
                tag[0] == "e" && value in pool -> deletedIds += deletion.pubKey to value
                tag[0] == "a" && value in addresses -> {
                    val key = deletion.pubKey to value
                    deletedAddresses[key] = maxOf(deletedAddresses[key] ?: Long.MIN_VALUE, deletion.createdAt)
                }
            }
        } }
        val deleted = pool.values.filter { event ->
            (event.pubKey to event.id) in deletedIds || ThreadRelations.address(event)?.let { address ->
                deletedAddresses[event.pubKey to address]?.let { event.createdAt <= it }
            } == true
        }.map { it.id }.toSet()
        rememberDeleted(pool.values)
        val spam = suspectedReplySpam(pool.values, state.value.following + listOfNotNull(request.account))
        var view = conversation(id, focus, pool.values.toList(), filter, deleted + state.value.deletedNotes, request.expanded, ancestorLimit, spam)
        // A tapped branch gap gets a slot in the existing eight-parent lookup budget.
        if (requestedParent != null && view.rows.any { it.gap == ThreadGap.MISSING && it.reference?.id == requestedParent.id }) {
            view = view.copy(missing = (listOf(requestedParent) + view.missing).distinctBy { it.id }.take(8))
        }
        return Triple(pool.values.toList(), view, deletions)
    }

    private suspend fun refreshFeedDiscovery(feedAuthors: List<String>?, mutedAuthors: Set<String>, mutedWords: Set<String>) {
        val discoveryKey = feedAuthors to mutedAuthors
        val discoveryNow = android.os.SystemClock.elapsedRealtime()
        if (feedDiscoveryKey != discoveryKey || discoveryNow - feedDiscoveryAt >= 5_000) {
            val discoveredFeed = mutex.withLock {
                db.query<Event>(profileKinds.flatMap { kind -> Filter(kinds = listOf(kind), authors = feedAuthors, limit = 80).activityFilters() } +
                    Filter(kinds = listOf(1, 1111), authors = feedAuthors, limit = 80).podcastTagFilters() + recipeFilter(80))
            }
            val feedFilter = ContentFilter(mutedAuthors, state.value.embedded, showSensitive = state.value.showSensitive, deletedNotes = state.value.deletedNotes, words = mutedWords)
            val discoveryEvents = latestProfileEvents(discoveredFeed).filterNot(noteDeletions::hides).filterNot(::engagementDeleted).filterNot(feedFilter::hides)
            val recentTopics = timeline(Filter(kinds = listOf(1), since = topicSince(), limit = 600), emptyList(), 600)
                .filterNot(feedFilter::hides)
            feedDiscoveryPages = ProfileTab.entries.associateWith { candidate ->
                if (candidate == ProfileTab.HASHTAGS) recentTopics.filter(candidate::matches)
                else discoveryEvents.filter(candidate::matchesFeed)
            }
            feedDiscoveryKey = discoveryKey
            feedDiscoveryAt = discoveryNow
        }
    }

    private suspend fun snapshotContents(initialFeedOnly: Boolean = false) {
        refreshDeletedReactions()
        rememberDeleted(cachedViewEvents())
        val key = _state.value.publicKey
        val settings = key?.let { owner -> mutex.withLock {
            db.query<Event>(Filter(authors = listOf(owner), kinds = listOf(30078), tags = mapOf("d" to listOf(SETTINGS_D)), limit = 1)).firstOrNull()
        } }
        _state.update { if (it.publicKey == key) it.copy(settingsEvent = settings) else it }
        val thread = viewedThread
        val ancestorLimit = threadAncestorLimit
        val requestedParent = threadRequestedParent
        val contact = key?.let { mutex.withLock { db.query<Event>(Filter(kinds = listOf(3), authors = listOf(it), limit = 1)).firstOrNull() } }
        val interestList = key?.let { owner -> mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(10015), authors = listOf(owner), limit = 1)).firstOrNull()
        } }
        val privateTags = interestList?.takeIf { it.content.isNotBlank() }?.let(hashtagCache::read)
        val hashtagFollows = (followedTags(interestList) + privateTags.orEmpty()).take(256).toSet()
        val previousHashtags = state.value.followedHashtags
        val muteList = key?.let { author -> mutex.withLock {
            // A current list (including an empty one) supersedes the deprecated mute set.
            db.query<Event>(Filter(kinds = listOf(10000), authors = listOf(author), limit = 1)).firstOrNull()
                ?: db.query<Event>(Filter(kinds = listOf(30000), authors = listOf(author), tags = mapOf("d" to listOf("mute")), limit = 1)).firstOrNull()
        } }
        val privateMutes = muteList?.takeIf { it.content.isNotBlank() }?.let(muteCache::read)
        // Preserve already-known private blocks while a replacement awaits signer approval.
        val cachedPrivateMutes = if (muteList?.content?.isNotBlank() == true) muteCache.read(muteList, exact = false).orEmpty() else emptySet()
        val mutedAuthors = mutedPublicKeys(muteList) + (privateMutes ?: cachedPrivateMutes)
        val previousMutedWords = state.value.mutedWords
        val mutedWords = mutedWords(muteList) + muteList?.takeIf { it.content.isNotBlank() }?.let(muteCache::readWords).orEmpty()
        val pendingMuteList = muteList?.takeIf { it.content.isNotBlank() && privateMutes == null }
        val following = Protocol.follows(contact) - mutedAuthors
        val feedCeiling = feedUntil
        val feed = selectedFeed
        val feedAuthors = key?.let { (listOf(it) + following).take(1000) }
        val previousFeed = state.value.notes.takeIf { state.value.feedTab == feed }.orEmpty()
        val feedLimit = if (initialFeedOnly) 120 else 600
        val all = when (feed) {
            ProfileTab.RECIPES -> latestProfileEvents(timeline(recipeFilter(feedLimit, feedCeiling), previousFeed, feedLimit))
            ProfileTab.HASHTAGS -> timeline(Filter(kinds = listOf(1), since = topicSince(), limit = 600), emptyList(), 600)
            ProfileTab.POSTS -> {
                val authored = timeline(Filter(kinds = feed.feedKinds(), authors = feedAuthors, limit = feedLimit, until = feedCeiling),
                    previousFeed.filter { feedAuthors == null || it.pubKey in feedAuthors }, feedLimit)
                val tagged = if (key == null || hashtagFollows.isEmpty()) emptyList() else timeline(
                    Filter(kinds = listOf(1), tags = mapOf("t" to hashtagFollows.toList()), limit = feedLimit, until = feedCeiling),
                    previousFeed.filter { it.matchesHashtags(hashtagFollows) }, feedLimit)
                latestProfileEvents(authored + tagged).take(feedLimit)
            }
            else -> latestProfileEvents(timeline(Filter(kinds = feed.feedKinds(), authors = feedAuthors, limit = feedLimit, until = feedCeiling), previousFeed, feedLimit))
        }
        val contentFilter = ContentFilter(mutedAuthors, state.value.embedded, showSensitive = state.value.showSensitive, deletedNotes = state.value.deletedNotes, words = mutedWords)
        // Publish local rows before optional category, metadata, engagement and routing work.
        // Keep the old view intact if navigation/account changed during a disk read.
        fun feedCurrent() = key == state.value.publicKey && feed == selectedFeed && feedCeiling == feedUntil
        if (feedCurrent()) _state.update { previous ->
            if (!feedCurrent()) previous else previous.copy(notes = all.filterNot(contentFilter::hides), feedTab = feed,
                loading = if (viewedThread == null && viewedProfile == null && viewedHashtag == null && all.isNotEmpty()) false else previous.loading)
        }
        if (!initialFeedOnly && feedCurrent() && viewedThread == null && viewedProfile == null && viewedHashtag == null) {
            val viewport = all.filter { it.id in visibleIds }.ifEmpty { all.take(24) }.take(40)
            val visibleAuthors = (listOfNotNull(key) + viewport.map { it.pubKey }).distinct()
            val cached = cachedProfiles(visibleAuthors)
            if (feedCurrent()) {
                _state.update { if (it.publicKey == key) it.copy(profiles = it.profiles + cached) else it }
                val prioritized = (visibleAuthors + profileAuthors).distinct().take(160)
                if (prioritized != profileAuthors) { profileAuthors = prioritized; subscribeReadingData() }
            }
        }
        val retainedNotifications = state.value.notifications.takeIf { key != null && state.value.notificationsFor == key }.orEmpty()
        val notifications = if ((viewingNotifications || initialFeedOnly) && key != null) (retainedNotifications + mutex.withLock {
            // Independent per-kind windows must not replace previously discovered rows.
            notificationKinds.flatMap { kind -> db.query<Event>(Filter(kinds = listOf(kind), tags = mapOf("p" to listOf(key)), limit = 100)) }
        }).distinctBy { it.id }.filterNot { it.pubKey == key || engagementDeleted(it) || contentFilter.hides(it) }
            .filter { event -> event.kind !in listOf(9735, 9321) || zapActivity(event)?.let { it.sender != key && it.sender !in mutedAuthors } == true }
            .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).take(1000)
        else state.value.notifications.takeIf { key != null && state.value.notificationsFor == key }.orEmpty()
        if (key == state.value.publicKey) _state.update {
            if (it.publicKey == key) it.copy(notifications = notifications, notificationsFor = key) else it
        }
        if (initialFeedOnly) {
            // Same account/filter rules as the complete snapshot, with no relay work.
            // Metadata is bounded to the initial viewport; the rest fills in independently.
            val authors = (listOfNotNull(key) + all.take(24).map { it.pubKey }).distinct()
            val profiles = cachedProfiles(authors)
            if (key == state.value.publicKey && feed == selectedFeed && feedCeiling == feedUntil) {
                _state.update { it.copy(notes = all, feedTab = feed, following = following,
                    mutedAuthors = mutedAuthors, mutedWords = mutedWords, pendingMuteList = pendingMuteList,
                    deletedReactions = deletedReactions, profiles = it.profiles + profiles,
                    loading = all.isEmpty()).withFollowedAccounts(contact).withFollowedHashtags(interestList, privateTags) }
            }
            return
        }
        refreshFeedDiscovery(feedAuthors, mutedAuthors, mutedWords)
        val previews = feedDiscoveryPages
        val feedTabs = (listOf(ProfileTab.POSTS) + ProfileTab.entries.filter {
            it != ProfileTab.POSTS && previews[it].orEmpty().isNotEmpty()
        }).sortedBy { it.orderPriority }
        val feedPreviews = previews.mapValues { it.value.take(40) }
        val threadState = state.value.takeIf { it.threadId == thread }
        val threadNotes = threadState?.threadNotes.orEmpty()
        val conversation = threadState?.conversation
        val profile = viewedProfile
        val tab = profileTab
        val profileCeiling = profileUntil
        val profileCount = profileLimit
        val profileEvents = profile?.let { author ->
            latestProfileEvents(timeline(if (tab == ProfileTab.RECIPES) recipeFilter(profileCount, profileCeiling, author) else Filter(kinds = tab.kinds, authors = listOf(author), limit = profileCount, until = profileCeiling), state.value.profileEvents, profileCount))
        }.orEmpty()
        val availableTabs = profile?.let { author ->
            val discovered = mutex.withLock {
                db.query<Event>(profileKinds.flatMap { kind ->
                    Filter(kinds = listOf(kind), authors = listOf(author), limit = 600).activityFilters()
                } + Filter(kinds = listOf(1, 1111), authors = listOf(author), limit = 80).podcastTagFilters())
            }
            val events = latestProfileEvents(discovered + profileEvents + olderEvents.filter { it.involves(author) })
                .filterNot(ContentFilter(mutedAuthors, state.value.embedded, showSensitive = state.value.showSensitive, deletedNotes = state.value.deletedNotes, words = mutedWords)::hides)
            val found = ProfileTab.entries.filter { candidate -> events.any(candidate::matches) }
            (listOf(ProfileTab.POSTS) + found + state.value.profileTabs.takeIf { state.value.profileTabsFor == author }.orEmpty())
                .distinct().sortedBy { it.profileOrderPriority }
        }.orEmpty()
        val hashtag = viewedHashtag
        val hashtagAuthor = viewedHashtagAuthor
        val hashtagTab = selectedHashtagTab
        val hashtagCeiling = hashtagUntil
        val hashtagCount = hashtagLimit
        val hashtagNotes = hashtag?.let { tag ->
            hashtagTimeline(tag, hashtagAuthor, hashtagTab, hashtagCeiling, hashtagCount)
        }.orEmpty()
        val hashtagCategories = discoverHashtagCategories(hashtag, hashtagAuthor, hashtagNotes, contentFilter)
        val readingPool = (threadNotes + hashtagNotes + profileEvents + (if (viewingNotifications) notifications else all)).filterNot(contentFilter::hides).distinctBy { it.id }
        val visible = (galleryNote?.let { listOf(it) } ?: visibleIds.mapNotNull { id -> readingPool.find { it.id == id } }.ifEmpty {
            if (viewingNotifications) notifications.take(12) else if (thread != null) threadNotes.take(12) else if (hashtag != null) hashtagNotes.take(12) else if (profile != null) profileEvents.filter(tab::matches).take(12) else all.filter(feed::matchesFeed).take(12)
        }).filterNot(contentFilter::hides)
        val biography = profile?.let { author -> mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(0), authors = listOf(author), limit = 1)).firstOrNull()
        } }
        val bioTokens = biography?.let { event ->
            val about = runCatching { JSONObject(event.content).optString("about") }.getOrDefault("").take(2000).withoutObjectMarkers()
            NostrContent.tokens(about, event)
        }.orEmpty()
        val bioRefs = bioTokens.mapNotNull { it.reference }.filter { it.hashtag == null }.distinctBy { it.id }
        val bioQuotes = bioRefs.filter { !it.profile && it.external == null }.take(3)
        val repostIds = visible.mapNotNull(::repostTarget).map { it.id }.distinct().take(24)
        val reposts = if (repostIds.isEmpty()) emptyList() else mutex.withLock {
            cachedEventsLocked(repostIds).values.filter { it.kind in readableKinds }
        }
        val readingContent = visible + reposts.filterNot(contentFilter::hides)
        val readingTab = if (profile != null) tab else feed
        val collections = if (profile != null) profileEvents else all
        val bookmarkRefs = if (readingTab == ProfileTab.BOOKMARKS)
            collections.flatMap(::bookmarkReferences).filter { it.id in visibleIds }.take(24) else emptyList()
        val peopleRefs = if (readingTab == ProfileTab.PEOPLE)
            collections.flatMap(::peopleReferences).filter { it.id in visibleIds }.distinctBy { it.id }.take(32) else emptyList()
        val refs = (conversation?.missing.orEmpty() + peopleRefs + bookmarkRefs + bioRefs + readingContent.filter { it.kind !in listOf(3, 30000) }.flatMap(NostrContent::references)).distinctBy { it.id }.take(64)
        val notificationRefs = if (viewingNotifications) visible.mapNotNull(::notificationTarget) else emptyList()
        val quoteIds = (notificationRefs.filter { !it.profile } + bookmarkRefs.filter { it.external == null } + bioQuotes + readingContent.flatMap { listOfNotNull(NostrContent.replyReference(it)) + NostrContent.quotes(it) }).map { it.id }.filter { ':' !in it }.distinct().take(24)
        val addressRefs = (bookmarkRefs + refs).distinctBy { it.id }.filter { it.addressFilter() != null }.take((24 - quoteIds.size).coerceAtLeast(0))
        val embedded = mutex.withLock {
            val byId = cachedEventsLocked(quoteIds).filterValues { it.kind in readableKinds }
            val byAddress = addressRefs.mapNotNull { ref ->
                db.query<Event>(ref.addressFilter()!!).sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
                    .firstOrNull()?.let { ref.id to it }
            }.toMap()
            byId + byAddress
        }
        val countTargets = (visible.filter { it.kind in readableKinds } + embedded.values.filterNot(contentFilter::hides)).distinctBy { it.id }.take(24)
        refreshIssueStatuses(countTargets)
        visiblePolls = countTargets.filter { it.kind == 1068 }.take(8)
        val pollResults = mutex.withLock {
            visiblePolls.associate { poll -> poll.id to tallyPoll(poll,
                db.query<Event>(Filter(kinds = listOf(1018), tags = mapOf("e" to listOf(poll.id)), since = poll.createdAt, until = parsePoll(poll)?.endsAt, limit = 2000)), key) }
        }
        engagementIds = countTargets.map { it.id }
        engagementApps = countTargets.mapNotNull { it.appAddress() }
        val countedActivity = cachedCountActivity(countTargets)
        val activity = countedActivity.values.flatten().distinctBy { it.id }
        val authors = (listOfNotNull(key, viewedProfile) + visible.map { it.pubKey } +
            refs.filter { it.profile }.map { it.id } + refs.mapNotNull { it.author } + embedded.values.map { it.pubKey } +
            embedded.values.flatMap(NostrContent::references).filter { it.profile }.map { it.id } + detailAuthors.values.flatten().distinct().take(64) + pollVoterAuthors + activity.filter { it.kind == 9735 }.mapNotNull { it.tagValue("p") }.take(24)).distinct().take(160)
        val profiles = cachedProfiles(authors)
        val counts = countTargets.associate { target ->
            var zappedByAccount: String? = null
            val stableActivity = countedActivity[target.id].orEmpty()
            val replies = stableActivity.count { !contentFilter.hides(it) && ThreadRelations.repliesTo(it, target) }
            val reactions = stableActivity.filter { it.kind == 7 && !engagementDeleted(it) && it.pubKey !in mutedAuthors && reactionTarget(it)?.id == target.id }.distinctBy { it.pubKey to it.content }.size
            val repostAuthors = stableActivity.asSequence().filter {
                it.kind in listOf(6, 16) && !engagementDeleted(it) && it.pubKey !in mutedAuthors && repostTarget(it)?.id == target.id
            }.map { it.pubKey }.toSet()
            val zapSats = stableActivity.asSequence().filter { it.kind == 9735 && it.tagValue("e") == target.id }
                .mapNotNull { receipt ->
                    val amount = zapVerification.amountInSats(receipt, target, profiles[receipt.tagValue("p")] ?: state.value.profiles[receipt.tagValue("p")], active)
                    if (amount.signum() > 0 && key != null && (receipt as? ZapReceiptEvent)?.zapRequest?.pubKey == key) {
                        zappedByAccount = key
                    }
                    amount.takeIf { it.signum() > 0 }?.let { receipt.tagValue("bolt11")?.lowercase(java.util.Locale.ROOT) to it }
                }.distinctBy { it.first }.fold(java.math.BigDecimal.ZERO) { total, (_, amount) -> total + amount }
            target.id to Engagement(replies, reactions, zapSats, zappedByAccount, repostAuthors.size, key?.takeIf { it in repostAuthors })
        }
        val sentReposts = key?.let { author ->
            val known = mutex.withLock { db.query<Event>(Filter(kinds = listOf(6, 16), authors = listOf(author), limit = 256)) }
            (known + postOutbox.records().map { it.event }.filter { it.pubKey == author && it.kind in listOf(6, 16) } + repostStore.records(author).filter { it.accepted }.map { it.event }).filterNot(::engagementDeleted).mapNotNull { repostTarget(it)?.id }.toSet()
        }.orEmpty()
        val ownReactions = key?.let { author ->
            val recent = mutex.withLock { db.query<Event>(Filter(kinds = listOf(7), authors = listOf(author), limit = 256)) }
            val visibleReactions = if (engagementIds.isEmpty()) emptyList() else mutex.withLock {
                db.query<Event>(Filter(kinds = listOf(7), authors = listOf(author), tags = mapOf("e" to engagementIds), limit = 512))
            }
            (postOutbox.records().map { it.event }.filter { it.pubKey == author && it.kind == 7 } + reactionStore.records(author).filter { it.accepted }.map { it.event } + recent + visibleReactions + activity.filter { it.kind == 7 && it.pubKey == author })
                .filterNot(::engagementDeleted).distinctBy { it.id }.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
        }.orEmpty()
        val sentReactions = ownReactions.mapNotNull { event ->
            val target = reactionTarget(event)?.id ?: return@mapNotNull null
            reactionChoice(event)?.let { target to it }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, choices) -> choices.distinctBy { it.key } }
        val reactionChoices = reactionSuggestions(ownReactions)
        val recentEmojis = ownReactions.asSequence().mapNotNull(::reactionChoice)
            .filter { it.image == null && validReactionChoice(it) }.map { it.content }.distinct().take(60).toList()
        val emojiList = key?.let { author -> mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(10030), authors = listOf(author), limit = 1)).firstOrNull()
        } }
        val packs = emojiPacks(emojiList)
        val packEvents = mutex.withLock {
            packs.mapNotNull { pack -> db.query<Event>(pack.filter()).firstOrNull() }
        }
        val collection = (collectionEmojis(emojiList) + packEvents.flatMap(::collectionEmojis))
            .distinctBy { it.key }.take(1024)
        val customEmojis = (ownReactions.mapNotNull(::reactionChoice).filter { it.image != null } + collection)
            .distinctBy { it.key }.take(1024)

        rememberDeleted(embedded.values + all + profileEvents + hashtagNotes + notifications)
        deletionTargets = (visible + reposts + embedded.values).distinctBy { it.id }.filterNot(noteDeletions::hides).take(48)
        val packsChanged = emojiPackRequests != packs
        val readingChanged = references != refs || profileAuthors != authors || packsChanged
        references = refs
        embeddedIds = quoteIds + addressRefs.map { it.id }
        profileAuthors = authors
        if (key != _state.value.publicKey || profile != viewedProfile || tab != profileTab || hashtag != viewedHashtag || hashtagAuthor != viewedHashtagAuthor || hashtagTab != selectedHashtagTab || thread != viewedThread || ancestorLimit != threadAncestorLimit || requestedParent != threadRequestedParent || feed != selectedFeed || feedCeiling != feedUntil || profileCeiling != profileUntil || profileCount != profileLimit || hashtagCeiling != hashtagUntil || hashtagCount != hashtagLimit) { changed.tryEmit(Unit); return }
        emojiPackRequests = packs
        val lists = mutex.withLock { db.query<Event>(Filter(kinds = listOf(10002), limit = 1100)) }
            .associate { it.pubKey to Outbox.parse(it) }
        val routesChanged = lists != relayLists
        relayLists = lists
        val previous = _state.value.following
        val mutesChanged = _state.value.mutedAuthors != mutedAuthors
        val previousThreadAuthor = _state.value.threadNotes.find { it.matchesThread(viewedThread) }?.pubKey
        _state.update { it.copy(pollResults = (it.pollResults.mapValues { (_, result) -> if (result.ballots.isEmpty()) result else result.copy(ballots = emptyList()) } + pollResults).entries.toList().takeLast(64).associate { entry -> entry.toPair() }, notes = all, notifications = notifications, notificationsFor = key, feedTab = feed, feedTabs = feedTabs, feedPreviews = feedPreviews, hashtagNotes = mergeHashtagSnapshot(it, hashtagNotes, hashtag, hashtagAuthor, hashtagTab, hashtagCeiling, hashtagCount), hashtagFor = hashtag, hashtagAuthor = hashtagAuthor, hashtagTab = hashtagTab, hashtagCategories = hashtagCategories, profileEvents = mergeProfileSnapshot(it, profileEvents, profile, tab, profileCeiling, profileCount), profileTabsFor = profile, profileTabs = availableTabs, embedded = (it.embedded + embedded).entries.toList().takeLast(128).associate { entry -> entry.toPair() }, engagement = (it.engagement - counts.keys + counts).entries.toList().takeLast(800).associate { entry -> entry.toPair() }, profiles = (it.profiles - profiles.keys + profiles).entries.toList().takeLast(1000).associate { entry -> entry.toPair() }, following = following, sentReposts = sentReposts, sentReactions = sentReactions, deletedReactions = deletedReactions, reactionChoices = reactionChoices, recentEmojis = recentEmojis, customEmojis = customEmojis, mutedAuthors = mutedAuthors, mutedWords = mutedWords, pendingMuteList = pendingMuteList).withFollowedAccounts(contact).withFollowedHashtags(interestList, privateTags) }
        if (mutesChanged) threadChanges.tryEmit(Unit)
        if (active && (readingChanged || following != previous || previousHashtags != hashtagFollows || routesChanged || previousThreadAuthor != threadNotes.find { it.matchesThread(viewedThread) }?.pubKey)) subscribeFeed()
        else if (active) { viewedThread?.let(::subscribeThread); subscribeReadingData() }
        if (previousMutedWords != mutedWords && viewedThread != null) threadChanges.tryEmit(Unit)
    }

    private fun mergeHashtagSnapshot(previous: FeedState, notes: List<Event>, tag: String?, author: String?, tab: ProfileTab,
        ceiling: Long?, limit: Int): List<Event> =
        (notes + previous.hashtagNotes.takeIf { previous.hashtagFor == tag && previous.hashtagAuthor == author && previous.hashtagTab == tab }.orEmpty())
            .distinctBy { it.id }.filter { ceiling == null || it.createdAt <= ceiling }
            .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).take(limit)

    private fun mergeProfileSnapshot(previous: FeedState, notes: List<Event>, author: String?, tab: ProfileTab,
        ceiling: Long?, limit: Int): List<Event> =
        latestProfileEvents(notes + previous.profileEvents.filter {
            it.pubKey == author && it.kind in tab.kinds && (ceiling == null || it.createdAt <= ceiling)
        }).take(limit)

    private suspend fun cachedCountActivity(countTargets: List<Event>): Map<String, List<Event>> = mutex.withLock {
            countTargets.associate { target ->
                val retained = recentEngagement.remove(target.id).orEmpty()
                retainedEngagementEvents -= retained.size
                val discovered = db.query<Event>(Filter(kinds = listOf(7), tags = mapOf("e" to listOf(target.id)), limit = 2000)) +
                    db.query<Event>(Filter(kinds = listOf(6, 16), tags = mapOf("e" to listOf(target.id)), limit = 2000)) +
                    db.query<Event>(Filter(kinds = listOf(1, 1111, 1244), tags = mapOf("e" to listOf(target.id)), limit = 2000)) +
                    db.query<Event>(Filter(kinds = listOf(9735), tags = mapOf("e" to listOf(target.id)), limit = 2000)) +
                    target.appAddress()?.let { address ->
                        db.query<Event>(Filter(kinds = listOf(1111), tags = mapOf("A" to listOf(address)), limit = 200))
                    }.orEmpty()
                val missingFromWindow = retained - discovered.map { it.id }.toSet()
                val events = (cachedEventsLocked(missingFromWindow.toList()).values + discovered).distinctBy { it.id }
                    .filter { (it.kind == 7 && reactionTarget(it)?.id == target.id) || (it.kind in listOf(6, 16) && repostTarget(it)?.id == target.id) ||
                        ThreadRelations.repliesTo(it, target) || (it.kind == 9735 && it.tagValue("e") == target.id) }
                    .sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id }).take(8200)
                val ids = events.map { it.id }.toSet()
                recentEngagement[target.id] = ids
                retainedEngagementEvents += ids.size
                while (recentEngagement.size > 800 || retainedEngagementEvents > 16_000) {
                    val oldest = recentEngagement.entries.first()
                    retainedEngagementEvents -= oldest.value.size
                    recentEngagement.remove(oldest.key)
                }
                target.id to events
            }
        }

    /** Opening details reads existing discovery/cache data; it never re-fetches the note. */
    internal fun noteDetails(target: Event): Flow<NoteDetails?> = flow {
        val token = java.util.UUID.randomUUID().toString()
        var last: NoteDetails? = null
        try {
            changed.onStart { emit(Unit) }.conflate().collect {
                val relays = eventRelays.read(target.id)
                if (relays.isNotEmpty() || last != null) {
                    last = (last ?: NoteDetails()).copy(relays = relays.ifEmpty { last?.relays.orEmpty() })
                    emit(last)
                }
                delay(400)
                val updated = try {
                    val current = state.value
                    val events = cachedCountActivity(listOf(target))[target.id].orEmpty()
                        .filterNot { engagementDeleted(it) || it.pubKey in current.mutedAuthors }
                    val reposts = events.filter { it.kind in listOf(6, 16) && repostTarget(it)?.id == target.id }
                        .distinctBy { it.pubKey }.take(64).map { DetailPerson(it.pubKey) }
                    val reactions = events.filter { it.kind == 7 && reactionTarget(it)?.id == target.id }
                        .groupBy { reactionChoice(it)?.key ?: it.content }.values.take(16).map { group ->
                            DetailReaction(group.first(), group.distinctBy { it.pubKey }.take(64).map { DetailPerson(it.pubKey) })
                        }
                    val receipts = events.filter { it.kind == 9735 && it.tagValue("e") == target.id }
                        .distinctBy { it.tagValue("bolt11")?.lowercase(java.util.Locale.ROOT) ?: it.id }
                    val recipientProfiles = cachedProfiles(receipts.mapNotNull { it.tagValue("p") }.distinct().take(64))
                    val totals = linkedMapOf<String?, java.math.BigDecimal>()
                    for (receipt in receipts) {
                        val preview = zapActivity(receipt) ?: continue
                        if (preview.sender in current.mutedAuthors) continue
                        val amount = zapVerification.amountInSats(receipt, target,
                            recipientProfiles[preview.recipient] ?: current.profiles[preview.recipient], active)
                        if (amount.signum() > 0) totals[preview.sender] = (totals[preview.sender] ?: java.math.BigDecimal.ZERO) + amount
                    }
                    val zaps = totals.entries.sortedByDescending { it.value }.take(64)
                        .map { DetailPerson(it.key, it.value.stripTrailingZeros().toPlainString()) }
                    val authors = (reposts + reactions.flatMap { it.people } + zaps).mapNotNull { it.pubkey }.distinct().take(192)
                    val profiles = cachedProfiles(authors)
                    val needed = authors.filter { it !in profiles && it !in current.profiles }.take(64)
                    if ((detailAuthors.containsKey(token) || detailAuthors.size < 8) && detailAuthors[token] != needed) {
                        detailAuthors[token] = needed
                        subscribeReadingData()
                    }
                    NoteDetails(reposts, reactions, zaps, profiles, relays.ifEmpty { last?.relays.orEmpty() })
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    android.util.Log.w("FeatherRepository", "Could not load note engagement details", e)
                    last ?: NoteDetails(relays = relays)
                }
                last = updated
                emit(updated)
            }
        } finally {
            detailAuthors.remove(token)
            subscribeReadingData()
        }
    }.flowOn(Dispatchers.IO)

    internal suspend fun zapRecipientProfiles(note: Event): Map<String, Profile> = withContext(Dispatchers.IO) {
        ready.await()
        val splits = zapSplits(note)
        val authors = splits.map { it.pubkey }
        val cached = cachedProfiles(authors)
        val missing = splits.filter { cached[it.pubkey]?.lightning.isNullOrBlank() }
        if (missing.isNotEmpty()) {
            val filters = missing.map { Filter(kinds = listOf(0), authors = listOf(it.pubkey), limit = 1) }
            val routes = (missing.flatMap { it.relays } + missing.flatMap { relayLists[it.pubkey]?.write.orEmpty() } +
                state.value.relays + RelayDefaults.seeds.take(2)).distinct().take(16)
            accountQueries.completed(routes, filters.map { it.toJson() }) { relay, event ->
                filters.any { it.match(event) } && accept(event, seenAt = relay)
            }
        }
        cached + cachedProfiles(authors)
    }

    internal fun zapSplitProfiles(note: Event): Flow<Map<String, Profile>> = accountProfiles(zapSplits(note).map { it.pubkey })

    internal fun accountProfiles(keys: List<String>): Flow<Map<String, Profile>> = flow {
        val token = java.util.UUID.randomUUID().toString()
        val authors = keys.distinct().take(64)
        try {
            changed.onStart { emit(Unit) }.conflate().collect {
                val profiles = try { cachedProfiles(authors) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    android.util.Log.w("FeatherRepository", "Could not read zap recipient profiles", e)
                    state.value.profiles.filterKeys { it in authors }
                }
                emit(profiles)
                val needed = authors.filter { it !in profiles }.take(64)
                if ((detailAuthors.containsKey(token) || detailAuthors.size < 8) && detailAuthors[token] != needed) {
                    detailAuthors[token] = needed
                    subscribeReadingData()
                }
                delay(400)
            }
        } finally { detailAuthors.remove(token); subscribeReadingData() }
    }.flowOn(Dispatchers.IO)

    private suspend fun cachedProfiles(authors: List<String>): Map<String, Profile> = if (authors.isEmpty()) emptyMap() else mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(0), authors = authors, limit = authors.size))
        }.associate { event ->
            val data = runCatching { JSONObject(event.content) }.getOrNull()
            val about = data?.optString("about").orEmpty().take(2000).withoutObjectMarkers()
            event.pubKey to Profile(
                data?.optString("display_name")?.takeIf { it.isNotBlank() } ?: data?.optString("name")?.takeIf { it.isNotBlank() } ?: shortKey(event.pubKey),
                about,
                MediaContent.secureUrl(data?.optString("picture")?.trim()),
                data?.optString("name")?.trim()?.takeIf { it.isNotBlank() },
                listOf("banner", "cover", "header").firstNotNullOfOrNull { field -> MediaContent.secureUrl(data?.optString(field)?.trim()) },
                data?.optString("lud16")?.takeIf { it.isNotBlank() } ?: data?.optString("lud06")?.takeIf { it.isNotBlank() },
                NostrContent.tokens(about, event),
                (data?.opt("nip05") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(512),
                profileWebsite(data?.opt("website") as? String),
            )
        }

    private suspend fun trimCache() = mutex.withLock { trimCacheLocked() }
    private suspend fun trimCacheLocked() {
        // Preserve the records needed to reconstruct the signed-in feed offline.
        val accountRecords = state.value.publicKey?.let { author ->
            db.query<Event>(listOf(0, 3, 10000, 10002, 10015, 10030, 10063).map { kind ->
                Filter(kinds = listOf(kind), authors = listOf(author), limit = 1)
            } + Filter(kinds = listOf(30000), authors = listOf(author), tags = mapOf("d" to listOf("mute")), limit = 1))
        }.orEmpty().map { it.id }
        val cachedState = state.value
        val homeNotes = (if (cachedState.feedTab == ProfileTab.POSTS) cachedState.notes
            else cachedState.feedPreviews[ProfileTab.POSTS].orEmpty())
            .filter(ProfileTab.POSTS::matchesFeed).take(120)
        val homeAuthors = homeNotes.map { it.pubKey }.distinct()
        val homeProfiles = if (homeAuthors.isEmpty()) emptyList() else
            db.query<Event>(Filter(kinds = listOf(0), authors = homeAuthors, limit = homeAuthors.size)).map { it.id }
        val protected = recentThreads.protectedIds() + accountRecords + homeNotes.map { it.id } +
            homeNotes.mapNotNull(::repostTarget).map { it.id } + homeProfiles +
            cachedState.notifications.map { it.id } + cachedState.notifications.mapNotNull(::notificationTarget).map { it.id } +
            recentEngagement.values.flatten()
        for ((kind, max) in listOf(1630 to 300, 1631 to 300, 1632 to 300, 1633 to 300, 1068 to 300, 1018 to 3000, 1621 to 300, 1222 to 300, 1244 to 300, 30023 to 300, 30000 to 300, 30617 to 300, 32267 to 300, 20 to 600, 21 to 300, 22 to 300, 34235 to 300, 34236 to 300, 10003 to 200, 30003 to 300, 1 to 2000, 1111 to 2000, 6 to 1000, 16 to 1000, 7 to 2000, 17 to 300, 5 to 1000, 9735 to 2000, 9321 to 1000, 9802 to 600, 0 to 1000, 3 to 300, 10000 to 300, 10002 to 1100, 10015 to 8, 10030 to 8, 10063 to 8, 30078 to 32, 30030 to 64)) {
            var unprotected = 0
            val evict = mutableListOf<String>()
            // Raw rows avoid decoding every event's tags/content merely to evict it.
            // Quartz only adds its newest-first ordering when a limit is supplied.
            db.rawQuery(listOf(Filter(kinds = listOf(kind), limit = Int.MAX_VALUE))) { row ->
                if (row.id !in protected && ++unprotected > max) evict += row.id
            }
            evict.chunked(128).forEach { ids ->
                db.delete(Filter(ids = ids))
                ids.forEach { verified.remove(it) }
            }
        }
        db.deleteExpiredEvents()
    }
    /** Explicit user action only. Signing and relay I/O stay off the UI thread. */
    private fun recordReaction(author: String, target: String, choice: ReactionChoice) {
        _state.update { if (it.publicKey == author) it.copy(
            sentReactions = it.sentReactions + (target to (listOf(choice) + it.sentReactions[target].orEmpty()).distinctBy { item -> item.key }),
            reactionChoices = (listOf(choice) + it.reactionChoices).distinctBy { item -> item.key }.take(6),
            recentEmojis = if (choice.image == null && validReactionChoice(choice))
                (listOf(choice.content) + it.recentEmojis).distinct().take(60) else it.recentEmojis,
        ) else it }
    }

    internal suspend fun repost(target: Event, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        socialActions.withLock(target.id) {
            ready.await()
            val author = state.value.publicKey ?: error("Connect an Android signer first")
            require(signer.pubKey == author && target.kind in readableKinds)
            val previous = repostStore.records(author).firstOrNull { repostTarget(it.event)?.id == target.id }
            val saved = previous?.takeUnless { engagementDeleted(it.event) }
            val cached = mutex.withLock { db.query<Event>(Filter(kinds = listOf(6, 16), authors = listOf(author), tags = mapOf("e" to listOf(target.id)), limit = 256)) }
            val known = cached.firstOrNull { !engagementDeleted(it) }
            if (saved?.accepted == true || known != null) {
                _state.update { if (it.publicKey == author) it.copy(sentReposts = it.sentReposts + target.id) else it }
                return@withLock
            }
            val kind = if (target.kind == 1) 6 else 16
            val hint = relayHint(target)
                ?: state.value.relays.firstOrNull(::healthy) ?: error("No relay available.")
            val tags = buildList {
                add(arrayOf("e", target.id, hint)); add(arrayOf("p", target.pubKey))
                if (kind == 16) add(arrayOf("k", target.kind.toString()))
                if (target.kind in 30000..39999) target.tagValue("d")?.let { add(arrayOf("a", "${target.kind}:${target.pubKey}:$it", hint.orEmpty())) }
                add(arrayOf("client", "Feather"))
            }.toTypedArray()
            val content = if (target.tags.any { it.firstOrNull() == "-" }) "" else target.toJson().takeIf { it.length <= 64_000 }.orEmpty()
            // A quick undo/repost must not recreate the just-deleted event ID.
            val now = maxOf(System.currentTimeMillis() / 1000,
                maxOf(previous?.event?.createdAt ?: 0, cached.maxOfOrNull { it.createdAt } ?: 0) + 1)
            val signed = saved?.event ?: signerRequests.run {
                check(state.value.publicKey == author)
                withTimeout(180_000) { signer.sign<Event>(now, kind, tags, content) }.also {
                    require(it.createdAt == now && it.tags.contentDeepEquals(tags)) { "Signer changed the repost" }
                }
            }
            require(signed.pubKey == author && signed.kind == kind && signed.content == content &&
                repostTarget(signed)?.id == target.id && signed.tagValue("p") == target.pubKey && signed.verify()) { "Invalid signed repost" }
            check(state.value.publicKey == author)
            repostStore.put(target.id, signed, accepted = false)
            enqueueSocialEvent(signed, target)
            _state.update { if (it.publicKey == author) it.copy(sentReposts = it.sentReposts + target.id) else it }
        }
    }

    internal fun pollVotersVisible(authors: List<String>) {
        val next = authors.distinct().take(24)
        if (pollVoterAuthors != next) { pollVoterAuthors = next; changed.tryEmit(Unit) }
    }

    internal fun voting(id: String, busy: Boolean) {
        _state.update { it.copy(pendingVotes = if (busy) it.pendingVotes + id else it.pendingVotes - id) }
    }

    internal suspend fun followEvent(owner: String, kind: Int): Event? = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock { db.query<Event>(Filter(kinds = listOf(kind), authors = listOf(owner), tags = if (kind == 30000) mapOf("d" to listOf("mute")) else null, limit = 1)).firstOrNull() }
    }

    internal suspend fun cacheFollowEvent(event: Event, relay: String? = null) {
        require(event.kind in listOf(3, 10002, 10015, 10000, 30000) && event.pubKey == state.value.publicKey)
        require(event.kind != 30000 || event.tagValue("d") == "mute")
        accept(event, seenAt = relay)
        if (event.kind == 3) {
            // Use the database winner, in case a newer replacement arrived during publication.
            val latest = followEvent(event.pubKey, 3)
            _state.update { it.withFollowedAccounts(latest) }
        } else if (event.kind == 10015) refreshHashtagFollowing(event.pubKey)
    }

    internal suspend fun awaitListEditingForeground() {
        try {
            withTimeout(180_000) { foregroundActive.first { it } }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("Waiting to return to Feather after signing timed out. Reopen Feather and retry the list change.", e)
        }
    }

    internal suspend fun publishFollowEvent(event: Event, routes: List<String>, baseline: String?, relayBaseline: String?): Boolean = withContext(Dispatchers.IO) {
        // If the signer/app switch backgrounded us during discovery, let the caller
        // wait and refresh again instead of publishing an aging snapshot on resume.
        if (!foregroundActive.value) return@withContext false
        check(state.value.publicKey == event.pubKey) { "Account changed" }
        require(event.kind in listOf(3, 10015, 10000))
        check(followEvent(event.pubKey, event.kind)?.id == baseline && followEvent(event.pubKey, 10002)?.id == relayBaseline) {
            "Account lists changed. Tap again to retry safely."
        }
        client.connect()
        val confirmed = try {
            client.publishAndConfirm(event, routes.map { it.normalizeRelayUrl() }.toSet(), timeoutInSeconds = 15, untilFirstAccept = true)
        } catch (_: TimeoutCancellationException) { false }
        catch (_: java.io.IOException) { false }
        if (confirmed) {
            cacheFollowEvent(event)
            changed.tryEmit(Unit)
        }
        confirmed
    }

    internal suspend fun vote(target: Event, choices: Set<String>, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        ready.await()
        val author = state.value.publicKey ?: error("Connect an Android signer first")
        val poll = parsePoll(target) ?: error("Invalid poll")
        require(signer.pubKey == author && choices.isNotEmpty() && choices.all { id -> poll.options.any { it.first == id } })
        require(poll.multiple || choices.size == 1)
        val previousVote = mutex.withLock {
            db.query<Event>(Filter(kinds = listOf(1018), authors = listOf(author), tags = mapOf("e" to listOf(target.id)), limit = 1)).firstOrNull()
        }
        val now = maxOf(System.currentTimeMillis() / 1000, (previousVote?.createdAt ?: 0) + 1)
        require(now <= System.currentTimeMillis() / 1000 + 600) { "Previous vote is dated in the future" }
        require(poll.endsAt == null || now < poll.endsAt) { "Poll closed" }
        val tags = (listOf(arrayOf("e", target.id)) + choices.sorted().map { arrayOf("response", it) }).toTypedArray()
        val signed = signerRequests.run {
            check(state.value.publicKey == author) { "Account changed" }
            withTimeout(180_000) { signer.sign<Event>(now, 1018, tags, "") }
        }
        require(signed.pubKey == author && signed.kind == 1018 && signed.createdAt == now && signed.content.isEmpty() && signed.tags.contentDeepEquals(tags) && signed.verify()) { "Invalid signed vote" }
        withTimeout(15_000) { foregroundActive.first { it } }
        client.connect()
        val routes = (poll.relays + relayLists[author]?.write.orEmpty() + relayLists[target.pubKey]?.write.orEmpty() + state.value.relays)
            .distinct().filter(::healthy).take(12)
        var confirmed = false
        repeat(3) { attempt ->
            if (!confirmed) {
                if (attempt > 0) delay(1_000L * attempt)
                check(state.value.publicKey == author) { "Account changed" }
                require(poll.endsAt == null || System.currentTimeMillis() / 1000 < poll.endsAt) { "Poll closed" }
                confirmed = try {
                    client.publishAndConfirm(signed, routes.map { it.normalizeRelayUrl() }.toSet(), timeoutInSeconds = 15, untilFirstAccept = true)
                } catch (_: TimeoutCancellationException) { false } catch (_: java.io.IOException) { false }
            }
        }
        check(confirmed) { "No relay confirmed the vote. Try again." }
        accept(signed)
        changed.tryEmit(Unit)
    }

    internal suspend fun react(target: Event, choice: ReactionChoice, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        socialActions.withLock(target.id) {
            ready.await()
            val author = state.value.publicKey ?: error("Connect an Android signer first.")
            require(signer.pubKey == author && target.kind in readableKinds)
            require(validReactionChoice(choice))
            val known = mutex.withLock {
                db.query<Event>(Filter(kinds = listOf(7), authors = listOf(author), tags = mapOf("e" to listOf(target.id)), limit = 100))
                    .firstOrNull { !engagementDeleted(it) && reactionChoice(it) == choice && reactionTarget(it)?.id == target.id }
            }
            val previous = reactionStore.get(author, target.id, choice)
            val saved = previous?.takeUnless { engagementDeleted(it.event) }
            if (known != null || saved?.accepted == true) {
                recordReaction(author, target.id, choice)
                return@withLock
            }
            val hint = relayHint(target)
            val tags = buildList {
                add(arrayOf("e", target.id, hint.orEmpty(), target.pubKey))
                add(arrayOf("p", target.pubKey))
                add(arrayOf("k", target.kind.toString()))
                choice.image?.let { add(arrayOf("emoji", choice.content.removeSurrounding(":"), it)) }
                if (target.kind in 30000..39999) target.tagValue("d")?.let { add(arrayOf("a", "${target.kind}:${target.pubKey}:$it", hint.orEmpty())) }
            }.toTypedArray()
            val createdAt = maxOf(System.currentTimeMillis() / 1000, (previous?.event?.createdAt ?: 0) + 1)
            val signed = saved?.event ?: run {
                val powEnabled = state.value.proofOfWork
                val mined = ProofOfWork.prepare(author, createdAt, 7, tags, choice.content, ProofOfWork.REACTION_BITS, powEnabled)
                signerRequests.run {
                    check(state.value.publicKey == author) { "Account changed" }
                    withTimeout(180_000) { signer.sign<Event>(mined.createdAt, 7, mined.tags, choice.content) }
                }.also {
                    require(it.createdAt == mined.createdAt && it.tags.contentDeepEquals(mined.tags)) { "Signer changed the reaction." }
                    if (powEnabled) ProofOfWork.verifySigned(it, ProofOfWork.REACTION_BITS)
                }
            }
            require(signed.pubKey == author && signed.kind == 7 && signed.content == choice.content && reactionChoice(signed) == choice &&
                reactionTarget(signed)?.id == target.id && signed.tagValue("p") == target.pubKey && signed.verify()) { "Invalid signed reaction." }
            check(state.value.publicKey == author) { "Account changed." }
            reactionStore.put(target.id, signed, accepted = false)
            enqueueSocialEvent(signed, target)
            recordReaction(author, target.id, choice)
        }
    }

    /** Public BUD-03 discovery for BUD-10 reading; does not require a signed-in owner. */
    internal suspend fun blossomReadServers(author: String): List<String> = withContext(Dispatchers.IO) {
        ready.await()
        val filter = Filter(authors = listOf(author), kinds = listOf(10063), limit = 1)
        suspend fun cached() = mutex.withLock { db.query<Event>(filter).maxByOrNull { it.createdAt } }
        var list = cached()
        if (list == null) {
            val relays = (relayLists[author]?.write.orEmpty() + state.value.relays).distinct().take(4)
            profileQueries.fetch(relays, listOf(filter.toJson())) { relay, event ->
                event.kind == 10063 && event.pubKey == author && accept(event, seenAt = relay)
            }
            list = cached()
        }
        list?.tags.orEmpty().filter { it.firstOrNull() == "server" }
            .mapNotNull { it.getOrNull(1)?.let(::blossomServer) }.distinct().take(8)
    }

    internal suspend fun blossomServers(owner: String): List<String> = withContext(Dispatchers.IO) {
        ready.await()
        val filter = Filter(authors = listOf(owner), kinds = listOf(10063), limit = 1)
        suspend fun cached() = mutex.withLock { db.query<Event>(filter).maxByOrNull { it.createdAt } }
        var list = cached()
        if (list == null) {
            val relays = (relayLists[owner]?.write.orEmpty() + state.value.relays).distinct()
            profileQueries.fetch(relays, listOf(filter.toJson())) { relay, event ->
                event.kind == 10063 && event.pubKey == owner && accept(event, seenAt = relay)
            }
            list = cached()
        }
        check(state.value.publicKey == owner) { "Account changed" }
        val servers = list?.tags.orEmpty().filter { it.firstOrNull() == "server" }
            .mapNotNull { it.getOrNull(1)?.let(::blossomServer) }.distinct()
        require(servers.size <= 16) { "Blossom server list exceeds 16 servers" }
        require(servers.isNotEmpty()) { "No HTTPS Blossom servers found on your profile (kind 10063)." }
        servers
    }

    /** Prefer a relay that actually delivered this event, including catalog-only app listings. */
    private fun relayHint(target: Event): String? =
        eventRelays.read(target.id).firstOrNull()
            ?: ZAPSTORE_RELAY.takeIf { target.kind == 32267 }
            ?: relayLists[target.pubKey]?.write?.firstOrNull(::healthy)

    internal suspend fun reference(target: Event): String = withContext(Dispatchers.IO) {
        target.nostrPointer(relayHint(target))
    }

    internal suspend fun reply(draft: ReplyDraft, signer: NostrSignerExternal, signerRequests: SignerRequests, store: ReplyStore, onMining: (Boolean) -> Unit = {}) = withContext(Dispatchers.IO) {
        ready.await()
        val author = draft.owner
        val target = draft.target
        require(state.value.publicKey == author && signer.pubKey == author) { "Account changed" }
        require((target == null || target.kind in readableKinds) && (draft.picturePost || draft.text.isNotBlank()) && draft.text.length <= 8000)
        val pictureTags = if (draft.picturePost) draft.pictureTags() else emptyArray<Array<String>>()
        val saved = store.signed(draft)
        val signed = saved ?: run {
            val hint = target?.let(::relayHint).orEmpty()
            val rootId = target?.takeIf { it.kind in listOf(1111, 1244) }?.tagValue("E")
            val cachedRoot = rootId?.let { id -> mutex.withLock { cachedEventsLocked(listOf(id))[id] } }
            val (kind, threadTags) = target?.let { replyTags(it, hint, cachedRoot) { id ->
                eventRelays.read(id).firstOrNull() ?: ZAPSTORE_RELAY.takeIf { id.startsWith("32267:") }
            } } ?: ((if (draft.picturePost) 20 else 1) to emptyArray<Array<String>>())
            val participants = threadTags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }.toSet()
            val mentions = replyMentions(draft.text).filter { it !in participants }.map { arrayOf("p", it) }
            val quotes = Regex("nostr:(?:nevent|note)1[023456789acdefghjklmnpqrstuvwxyz]+", RegexOption.IGNORE_CASE)
                .findAll(draft.text).take(16).mapNotNull { NostrContent.decode(it.value) }.distinctBy { it.id }.toList()
            val quoteTags = quotes.map { arrayOf("q", it.id, it.relays.firstOrNull().orEmpty(), it.author.orEmpty()) } +
                quotes.mapNotNull { it.author }.distinct().filter { it !in participants && mentions.none { tag -> tag[1] == it } }.map { arrayOf("p", it) }
            val hashtagTags = NostrContent.hashtags(draft.text).map { arrayOf("t", it) }.toTypedArray()
            val gifTags = (if (draft.picturePost) emptyList() else draft.gifs).filter { it.url in draft.text }.distinctBy { it.url }.take(32).map { it.imeta() }.toTypedArray()
            val tags = threadTags + mentions.toTypedArray() + quoteTags.toTypedArray() + hashtagTags + gifTags + pictureTags + arrayOf(arrayOf("client", "Feather"))
            val now = System.currentTimeMillis() / 1000
            val powEnabled = state.value.proofOfWork
            val mined = ProofOfWork.prepare(author, now, kind, tags, draft.text, ProofOfWork.POST_BITS, powEnabled, onMining)
            signerRequests.run {
                check(state.value.publicKey == author) { "Account changed" }
                withTimeout(180_000) { signer.sign<Event>(mined.createdAt, kind, mined.tags, draft.text) }
            }.also {
                require(it.createdAt == mined.createdAt && it.kind == kind && it.tags.contentDeepEquals(mined.tags)) { "Signer changed the post" }
                if (powEnabled) ProofOfWork.verifySigned(it, ProofOfWork.POST_BITS)
            }
        }
        require(signed.pubKey == author && signed.content == draft.text && signed.kind == (if (draft.picturePost) 20 else if (target == null || target.kind == 1) 1 else 1111) &&
            replyTarget(signed) == target?.id && signed.verify()) { "Invalid signed post" }
        check(state.value.publicKey == author) { "Account changed" }
        store.saveSigned(draft, signed)
        postOutbox.put(signed, target)
        // Persist Android's wake-up before acknowledging the composer hand-off.
        outboxWork.schedule(replace = true)
        outboxWake.trySend(Unit)
        // Delivery owns the event now. Cache/display it without waiting for any relay.
        accept(signed)
        _state.update {
            if (it.publicKey != author) it else it.copy(
                notes = if (target == null && it.feedTab == ProfileTab.POSTS) (listOf(signed) + it.notes).distinctBy { note -> note.id }.take(600) else it.notes,
                threadNotes = if (target != null && it.threadId == target.id)
                    (it.threadNotes + signed).distinctBy { note -> note.id } else it.threadNotes)
        }
        changed.tryEmit(Unit)
    }

    /** Saved once; the UI can settle without waiting for a relay or returning from the signer. */
    private suspend fun enqueueSocialEvent(event: Event, target: Event) {
        postOutbox.put(event, target)
        outboxWork.schedule(replace = true)
        accept(event)
        outboxWake.trySend(Unit)
        changed.tryEmit(Unit)
    }

    private fun confirmQueuedEvent(id: String) {
        val record = postOutbox.records().firstOrNull { it.event.id == id } ?: return
        val event = record.event
        // Never let an older acknowledgement overwrite a newer undo/re-add journal entry.
        when (event.kind) {
            7 -> reactionStore.confirm(event)
            6, 16 -> repostStore.confirm(event)
            5 -> reactionDeletions.put(event, accepted = true)
        }
        postOutbox.remove(id)
    }

    private data class OutboxSession(val owner: String?, val online: Boolean)

    /** WorkManager lends the shared delivery loop a bounded background execution window. */
    internal suspend fun deliverPostsInBackground(): Boolean = withContext(Dispatchers.IO) {
        backgroundOutboxMutex.withLock {
            ready.await()
            val owner = state.value.publicKey ?: return@withLock true
            if (postOutbox.records().none { it.event.pubKey == owner }) return@withLock true
            backgroundOutboxOwner.value = owner
            try {
                withTimeoutOrNull(120_000) {
                    combine(postOutbox.ids, state.map { it.publicKey }.distinctUntilChanged()) { _, account ->
                        account != owner || postOutbox.records().none { it.event.pubKey == owner }
                    }.first { it }
                } != null
            } finally {
                backgroundOutboxOwner.value = null
            }
        }
    }

    /** Wake on account/foreground/connectivity changes, never poll an idle queue. */
    private suspend fun deliverOutbox() {
        ready.await()
        var restoredOwner: String? = null
        combine(state.map { it.publicKey }.distinctUntilChanged(),
            foregroundActive, networkOnline, backgroundOutboxOwner) { owner, foreground, online, backgroundOwner ->
            OutboxSession(owner, online && (foreground || (owner != null && backgroundOwner == owner)))
        }.distinctUntilChanged().collectLatest { session ->
            while (currentCoroutineContext().isActive) {
                try {
                    val records = postOutbox.records().filter { it.event.pubKey == session.owner }
                    if (restoredOwner != session.owner) {
                        records.forEach { accept(it.event) }
                        restoredOwner = session.owner
                        changed.tryEmit(Unit)
                    }
                    // Also repair stale pending markers left by earlier app versions.
                    records.filter { eventRelays.read(it.event.id).isNotEmpty() }.forEach { confirmQueuedEvent(it.event.id) }
                    if (!session.online || session.owner == null) return@collectLatest
                    drainOutbox(session.owner)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { delay(5_000) } // Retain durable records on storage failure.
            }
        }
    }

    /** Two independent deliveries; a queued reply waits for its parent's confirmation. */
    private suspend fun drainOutbox(owner: String) = coroutineScope {
        // A publishing-only connection pool survives UI backgrounding without keeping feed REQs alive.
        val deliveryScope = CoroutineScope(currentCoroutineContext() + SupervisorJob(currentCoroutineContext()[Job]))
        val deliveryClient = NostrClient(RelayTransport(), deliveryScope)
        deliveryClient.connect()
        try {
            val jobs = mutableMapOf<String, Job>()
            val completed = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
            val retryAt = mutableMapOf<String, Long>()
            val failures = mutableMapOf<String, Int>()
            while (isActive) {
                for ((id, confirmed) in completed.entries.toList()) {
                    completed.remove(id)
                    jobs.remove(id)?.join()
                    if (confirmed || id !in postOutbox.ids.value) {
                        confirmQueuedEvent(id)
                        retryAt.remove(id)
                        failures.remove(id)
                    } else {
                        val count = ((failures[id] ?: 0) + 1).coerceAtMost(5)
                        failures[id] = count
                        retryAt[id] = android.os.SystemClock.elapsedRealtime() + (5_000L shl (count - 1))
                    }
                }
                // Relay echoes can confirm a post while its publish acknowledgement is still waiting.
                for (id in jobs.keys.toList()) if (id !in postOutbox.ids.value) {
                    jobs.remove(id)?.cancelAndJoin()
                    completed.remove(id)
                    retryAt.remove(id)
                    failures.remove(id)
                }
                val records = postOutbox.records().filter { it.event.pubKey == owner }
                val ids = records.map { it.event.id }.toSet()
                retryAt.keys.retainAll(ids)
                failures.keys.retainAll(ids)
                var nextRetry: Long? = null
                for (record in records) {
                    val event = record.event
                    if (event.id in jobs) continue
                    if (noteDeletions.hides(event) || engagementDeleted(event) || eventRelays.read(event.id).isNotEmpty()) {
                        if (eventRelays.read(event.id).isNotEmpty()) confirmQueuedEvent(event.id) else postOutbox.remove(event.id)
                        outboxWake.trySend(Unit) // A child earlier in this pass may now be eligible.
                        continue
                    }
                    if (reactionDeletions.pending(owner, setOf(event.id)) != null) continue
                    if (event.kind != 5 && record.target?.id in postOutbox.ids.value) continue
                    if (jobs.size >= 2) continue
                    val due = retryAt[event.id] ?: 0L
                    if (due > android.os.SystemClock.elapsedRealtime()) {
                        nextRetry = minOf(nextRetry ?: due, due)
                        continue
                    }
                    if (event.id !in postOutbox.ids.value) continue
                    jobs[event.id] = launch {
                        val author = record.target?.pubKey ?: owner
                        beginPublishing(author)
                        sendingPosts.update { it + event.id }
                        val confirmed = try {
                            publishQueuedPost(deliveryClient, event, record.target)
                        } catch (e: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            false
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { false }
                        finally {
                            sendingPosts.update { it - event.id }
                            endPublishing(author)
                            if (active) updateRoutingPool()
                        }
                        completed[event.id] = confirmed
                        outboxWake.trySend(Unit)
                    }
                }
                if (nextRetry == null || jobs.size >= 2) outboxWake.receive()
                else withTimeoutOrNull((nextRetry - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)) { outboxWake.receive() }
            }
        } finally {
            deliveryClient.disconnect()
            deliveryScope.cancel()
        }
    }

    private suspend fun publishQueuedPost(deliveryClient: NostrClient, event: Event, target: Event?): Boolean {
        currentCoroutineContext().ensureActive()
        check(state.value.publicKey == event.pubKey) { "Account changed" }
        val routes = (relayLists[event.pubKey]?.write.orEmpty().take(2) +
            target?.let { relayLists[it.pubKey]?.read }.orEmpty().take(2) + state.value.relays).distinct().take(4) +
            (if (target?.let { it.kind == 1621 || it.tagValue("K") == "1621" } == true) IssueReports.RELAYS else emptyList()) +
            (if (target?.isAppDiscussion() == true) listOf(ZAPSTORE_RELAY) else emptyList())
        val destinations = routes.map { it.normalizeRelayUrl() }.toSet()
        val confirmed = routes.isNotEmpty() && deliveryClient.publishAndConfirm(event,
            destinations, timeoutInSeconds = 15, untilFirstAccept = true)
        if (confirmed && event.kind in listOf(6, 16) && target != null && target.tags.none { it.firstOrNull() == "-" }) {
            // Confirmation is durable before optional propagation. A slow relay cannot hold the repost pending.
            confirmQueuedEvent(event.id)
            try {
                deliveryClient.publishAndConfirm(target, destinations, timeoutInSeconds = 5, untilFirstAccept = true)
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Best effort only. */ }
        }
        return confirmed
    }

    /** Publish an explicitly signed NIP-56 account report through the account’s existing routes. */
    internal suspend fun publishAccountReport(event: Event) = withContext(Dispatchers.IO) {
        require(event.kind == 1984 && event.pubKey == state.value.publicKey && event.verify()) { "Invalid account report" }
        check(publishPostEvent(event, null)) { "No relay confirmed the report. Send again to retry the same report." }
    }

    /** Sign and persist once; only relay delivery is retried, never signing or image uploads. */
    private suspend fun publishPostEvent(event: Event, target: Event?, attempts: Int = 3): Boolean {
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(if (attempt == 1) 1_000 else 3_000)
            currentCoroutineContext().ensureActive()
            check(state.value.publicKey == event.pubKey) { "Account changed" }
            // The signer may have backgrounded Feather. Do not reconnect while it is still hidden.
            withTimeout(15_000) { foregroundActive.first { it } }
            check(state.value.publicKey == event.pubKey) { "Account changed" }
            updateRoutingPool()
            client.connect()
            val routes = (relayLists[event.pubKey]?.write.orEmpty().filter { it in routingPool && healthy(it) }.take(2) +
                target?.let { relayLists[it.pubKey]?.read }.orEmpty().filter { it in routingPool && healthy(it) }.take(2) +
                state.value.relays.filter(::healthy)).distinct().take(4) +
                (if (target?.let { it.kind == 1621 || it.tagValue("K") == "1621" } == true) IssueReports.RELAYS else emptyList()) +
                if (target?.isAppDiscussion() == true) listOf(ZAPSTORE_RELAY) else emptyList()
            val accepted = try {
                routes.isNotEmpty() && client.publishAndConfirm(event, routes.map { it.normalizeRelayUrl() }.toSet(),
                    timeoutInSeconds = 15, untilFirstAccept = true)
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                false
            } catch (_: java.io.IOException) { false }
            if (accepted) {
                if (event.kind in listOf(6, 16) && target != null && target.tags.none { it.firstOrNull() == "-" }) {
                    repostOriginals.trySend(target to routes.map { it.normalizeRelayUrl() }.toSet())
                }
                return true
            }
        }
        return false
    }

    internal suspend fun removeReaction(target: Event, choice: ReactionChoice, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        socialActions.withLock(target.id) {
            ready.await()
            val author = state.value.publicKey ?: error("Connect a signer first")
            require(author == signer.pubKey)
            val cached = mutex.withLock { db.query<Event>(Filter(kinds = listOf(7), authors = listOf(author), tags = mapOf("e" to listOf(target.id)), limit = 256)) }
            val reactions = (cached + reactionStore.records(author).map { it.event } + postOutbox.records().map { it.event }).filter {
                !engagementDeleted(it) && it.pubKey == author && it.kind == 7 && reactionTarget(it)?.id == target.id && reactionChoice(it) == choice
            }.distinctBy { it.id }
            if (reactions.isEmpty()) { changed.tryEmit(Unit); return@withLock }
            val ids = reactions.map { it.id }.toSet()
            val tags = (ids.sorted().map { arrayOf("e", it) } + listOf(arrayOf("k", "7"))).toTypedArray()
            val now = System.currentTimeMillis() / 1000
            val deletion = reactionDeletions.pending(author, ids) ?: signerRequests.run {
                withTimeout(180_000) { signer.sign<Event>(now, 5, tags, "") }.also {
                    require(it.createdAt == now) { "Signer changed deletion timestamp" }
                }
            }
            require(deletion.pubKey == author && deletion.kind == 5 && deletion.content.isEmpty() && deletion.tags.contentDeepEquals(tags) && deletion.verify())
            check(state.value.publicKey == author)
            enqueueSocialEvent(deletion, target)
            refreshDeletedReactions()
            _state.update { current -> current.copy(deletedReactions = deletedReactions,
                sentReactions = if (current.publicKey == author) current.sentReactions + (target.id to current.sentReactions[target.id].orEmpty().filter { it != choice }) else current.sentReactions) }
            changed.tryEmit(Unit)
        }
    }

    internal suspend fun deleteNote(target: Event, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        socialActions.withLock(target.id) {
            ready.await()
            val author = state.value.publicKey ?: error("Connect a signer first")
            require(author == signer.pubKey && target.pubKey == author && target.kind in listOf(1, 20, 1111) && target.verify()) {
                "Only your own notes and replies can be deleted"
            }
            if (noteDeletions.hides(target)) return@withLock
            val tags = arrayOf(arrayOf("e", target.id), arrayOf("k", target.kind.toString()))
            val now = System.currentTimeMillis() / 1000
            val deletion = reactionDeletions.pending(author, setOf(target.id)) ?: signerRequests.run {
                check(state.value.publicKey == author) { "Account changed" }
                withTimeout(180_000) { signer.sign<Event>(now, 5, tags, "") }.also {
                    require(it.createdAt == now) { "Signer changed deletion timestamp" }
                }
            }
            require(deletion.pubKey == author && deletion.kind == 5 && deletion.content.isEmpty() &&
                deletion.tags.contentDeepEquals(tags) && deletion.verify()) { "Invalid deletion request" }
            check(state.value.publicKey == author) { "Account changed" }
            reactionDeletions.put(deletion, accepted = false)
            withTimeout(15_000) { foregroundActive.first { it } }
            beginPublishing(author)
            try {
                check(publishPostEvent(deletion, target)) { "Deletion not confirmed. Try again to resend the same request." }
                reactionDeletions.put(deletion, accepted = true)
                accept(deletion)
                changed.tryEmit(Unit)
            } finally { endPublishing(author); if (active) updateRoutingPool() }
        }
    }

    internal suspend fun removeRepost(target: Event, signer: NostrSignerExternal, signerRequests: SignerRequests) = withContext(Dispatchers.IO) {
        socialActions.withLock(target.id) {
            ready.await()
            val author = state.value.publicKey ?: error("Connect a signer first")
            require(author == signer.pubKey)
            val cached = mutex.withLock { db.query<Event>(Filter(kinds = listOf(6, 16), authors = listOf(author), tags = mapOf("e" to listOf(target.id)), limit = 256)) }
            val reposts = (cached + repostStore.records(author).map { it.event } + postOutbox.records().map { it.event }).filter {
                !engagementDeleted(it) && it.pubKey == author && it.kind in listOf(6, 16) && repostTarget(it)?.id == target.id
            }.distinctBy { it.id }
            if (reposts.isEmpty()) { changed.tryEmit(Unit); return@withLock }
            val ids = reposts.map { it.id }.toSet()
            val tags = (ids.sorted().map { arrayOf("e", it) } + reposts.map { it.kind }.distinct().sorted().map { arrayOf("k", it.toString()) }).toTypedArray()
            val now = System.currentTimeMillis() / 1000
            val deletion = reactionDeletions.pending(author, ids) ?: signerRequests.run {
                check(state.value.publicKey == author) { "Account changed" }
                withTimeout(180_000) { signer.sign<Event>(now, 5, tags, "") }.also {
                    require(it.createdAt == now) { "Signer changed deletion timestamp" }
                }
            }
            require(deletion.pubKey == author && deletion.kind == 5 && deletion.content.isEmpty() && deletion.tags.contentDeepEquals(tags) && deletion.verify())
            check(state.value.publicKey == author)
            enqueueSocialEvent(deletion, target)
            refreshDeletedReactions()
            _state.update { current -> current.copy(deletedReactions = deletedReactions,
                sentReposts = if (current.publicKey == author) current.sentReposts - target.id else current.sentReposts) }
            changed.tryEmit(Unit)
        }
    }

    internal suspend fun cacheMutePlaintext(event: Event, plaintext: String) = withContext(Dispatchers.IO) {
        if (event.pubKey != state.value.publicKey) return@withContext
        if (followEvent(event.pubKey, 10000)?.id != event.id) return@withContext
        muteCache.write(event, plaintext)
        _state.update { if (it.publicKey == event.pubKey) it.copy(
            mutedWords = mutedWords(event) + muteCache.readWords(event),
            mutedAuthors = mutedPublicKeys(event) + muteCache.read(event).orEmpty(), pendingMuteList = null) else it }
        changed.tryEmit(Unit)
        threadChanges.tryEmit(Unit)
    }

    internal suspend fun cacheHashtagPlaintext(event: Event, plaintext: String) = withContext(Dispatchers.IO) {
        require(event.kind == 10015 && event.pubKey == state.value.publicKey)
        hashtagCache.write(event, plaintext)
        refreshHashtagFollowing(event.pubKey)
    }

    private suspend fun refreshHashtagFollowing(owner: String) {
        val latest = followEvent(owner, 10015)
        val privateTags = latest?.takeIf { it.content.isNotBlank() }?.let(hashtagCache::read)
        val previous = state.value.followedHashtags
        _state.update { if (it.publicKey == owner) it.withFollowedHashtags(latest, privateTags) else it }
        changed.tryEmit(Unit)
        if (active && state.value.publicKey == owner && previous != state.value.followedHashtags) subscribeFeed()
    }

    suspend fun decryptHashtagList(event: Event, signer: NostrSignerExternal) = withContext(Dispatchers.IO) {
        require(event.kind == 10015 && event.pubKey == state.value.publicKey && signer.pubKey == event.pubKey)
        require(event.id == state.value.pendingHashtagList?.id)
        val plaintext = withTimeout(180_000) {
            if ("?iv=" in event.content) signer.nip04Decrypt(event.content, event.pubKey)
            else signer.nip44Decrypt(event.content, event.pubKey)
        }
        ensureActive()
        if (event.pubKey != state.value.publicKey || event.id != state.value.pendingHashtagList?.id) return@withContext
        hashtagCache.write(event, plaintext)
        snapshot()
    }

    suspend fun decryptMuteList(event: Event, signer: NostrSignerExternal) = withContext(Dispatchers.IO) {
        require(event.pubKey == state.value.publicKey && signer.pubKey == event.pubKey)
        require(event.id == state.value.pendingMuteList?.id)
        val plaintext = withTimeout(180_000) {
            if ("?iv=" in event.content) signer.nip04Decrypt(event.content, event.pubKey)
            else signer.nip44Decrypt(event.content, event.pubKey)
        }
        ensureActive()
        if (event.pubKey != state.value.publicKey || event.id != state.value.pendingMuteList?.id) return@withContext
        muteCache.write(event, plaintext)
        refreshMuteState(event.pubKey)
        changed.tryEmit(Unit)
        threadChanges.tryEmit(Unit)
    }

    suspend fun account(pubkey: String, signer: String) = withContext(Dispatchers.IO) {
        require(Protocol.publicKey(pubkey) == pubkey && signer.isNotBlank())
        prefs.edit().putString("pubkey", pubkey).putString("signer", signer).remove("draft").remove("draftReply").remove("pending").apply()
        emojiPackRequests = emptyList()
        client.unsubscribe("emoji-packs")
        feedUntil = null; feedCeilings.clear(); selectedFeed = ProfileTab.POSTS
        _state.update { it.copy(pollResults = emptyMap(), publicKey = pubkey, signerPackage = signer, settingsEvent = null, showSensitive = prefs.getBoolean("show-sensitive:$pubkey", false), proofOfWork = prefs.getBoolean("proof-of-work:$pubkey", true), following = emptySet(), followedAccounts = emptySet(), followedAccountsVersion = null, followedHashtags = emptySet(), followedHashtagsVersion = null, pendingHashtagList = null, notes = emptyList(), notifications = emptyList(), notificationsFor = null, notificationsLoading = false, feedTab = ProfileTab.POSTS, feedTabs = listOf(ProfileTab.POSTS), feedPreviews = emptyMap(), sentReposts = emptySet(), sentReactions = emptyMap(), reactionChoices = defaultReactions, recentEmojis = emptyList(), customEmojis = emptyList(), mutedAuthors = emptySet(), mutedWords = emptySet(), pendingMuteList = null, loading = true) }
        ready.await()
        if (postOutbox.records().any { it.event.pubKey == pubkey }) outboxWork.schedule(replace = true)
        refreshAccountData(force = true); threadChanges.tryEmit(Unit); snapshot(); if (active) subscribeFeed()
    }
    suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.edit().remove("pubkey").remove("signer").remove("draft").remove("draftReply").remove("pending").apply()
        emojiPackRequests = emptyList()
        client.unsubscribe("emoji-packs")
        feedUntil = null; feedCeilings.clear(); selectedFeed = ProfileTab.POSTS
        _state.update { it.copy(pollResults = emptyMap(), publicKey = null, signerPackage = null, settingsEvent = null, showSensitive = false, proofOfWork = true, following = emptySet(), followedAccounts = emptySet(), followedAccountsVersion = null, followedHashtags = emptySet(), followedHashtagsVersion = null, pendingHashtagList = null, notes = emptyList(), notifications = emptyList(), notificationsFor = null, notificationsLoading = false, feedTab = ProfileTab.POSTS, feedTabs = listOf(ProfileTab.POSTS), feedPreviews = emptyMap(), sentReposts = emptySet(), sentReactions = emptyMap(), reactionChoices = defaultReactions, recentEmojis = emptyList(), customEmojis = emptyList(), mutedAuthors = emptySet(), mutedWords = emptySet(), pendingMuteList = null) }
        outboxWork.cancel()
        threadChanges.tryEmit(Unit); snapshot(); if (active) subscribeFeed()
    }
    fun feedTab(tab: ProfileTab) {
        if (selectedFeed == tab) return
        feedUntil?.let { feedCeilings[selectedFeed] = it }
        selectedFeed = tab
        feedUntil = feedCeilings[tab]
        historyJob?.cancel()
        visibleIds = emptyList()
        _state.update { it.copy(loading = true, error = null) }
        changed.tryEmit(Unit)
        scope.launch { ready.await(); if (active) subscribeFeed() }
    }

    /** Restore the selected profile page without waiting for home discovery or engagement work. */
    private fun restoreProfileCache() {
        val generation = profileRestoreGeneration.incrementAndGet()
        profileRestoreJob?.cancel()
        val author = viewedProfile ?: return
        val tab = profileTab
        val owner = state.value.publicKey
        val ceiling = profileUntil
        val limit = profileLimit
        profileRestoreJob = scope.launch {
            ready.await()
            fun current() = profileRestoreGeneration.get() == generation && viewedProfile == author &&
                profileTab == tab && state.value.publicKey == owner && profileUntil == ceiling && profileLimit == limit
            val retained = state.value.profileEvents.filter { it.involves(author) }
            val events = latestProfileEvents(timeline(
                if (tab == ProfileTab.RECIPES) recipeFilter(limit, ceiling, author) else Filter(kinds = tab.kinds, authors = listOf(author), limit = limit, until = ceiling), retained, limit))
            ensureActive()
            if (!current()) return@launch
            rememberDeleted(events)
            _state.update { previous ->
                if (!current()) previous else previous.copy(
                    profileEvents = events,
                    profileTabsFor = author,
                    profileTabs = (listOf(ProfileTab.POSTS) +
                        previous.profileTabs.takeIf { previous.profileTabsFor == author }.orEmpty() +
                        listOfNotNull(tab.takeIf { events.any(tab::matches) })).distinct().sortedBy { it.profileOrderPriority })
            }
            // Author metadata is optional: make cached content visible before querying it.
            val profiles = cachedProfiles((listOf(author) + events.take(24).map { it.pubKey }).distinct())
            ensureActive()
            if (current()) _state.update { if (current()) it.copy(profiles = it.profiles + profiles) else it }
        }
    }

    fun viewProfile(key: String?) { if (key != null && key == state.value.publicKey) refreshAccountData(); if (viewedProfile == key) return; stopProfileSweep(); historyJob?.cancel(); profileLimit = 100; profileUntil = null; profileTab = ProfileTab.POSTS; viewedProfile = key; restoreProfileCache(); changed.tryEmit(Unit); if (key == null) { client.unsubscribe("profile"); client.unsubscribe("profile-activity"); client.unsubscribe("profile-apps"); client.unsubscribe("profile-issues") }; if (active) subscribeFeed() }
    fun profileTab(tab: ProfileTab) {
        if (profileTab == tab) return
        historyJob?.cancel()
        profileTab = tab
        profileLimit = 100
        profileUntil = null
        restoreProfileCache()
        changed.tryEmit(Unit)
        if (active) viewedProfile?.let(::subscribeProfile)
    }
    @Synchronized
    private fun subscribeProfile(key: String) {
        if (!active || viewedProfile != key) return
        discoverRelays()
        // Apps are commonly published only to the catalog relay, not personal write relays.
        client.subscribe("profile-apps", mapOf(ZAPSTORE_RELAY.normalizeRelayUrl() to
            listOf(Filter(kinds = listOf(32267), authors = listOf(key), limit = if (profileTab == ProfileTab.APPS) 80 else 8))), listener)
        client.subscribe("profile-issues", IssueReports.RELAYS.associate { it.normalizeRelayUrl() to
            listOf(Filter(kinds = listOf(1621), authors = listOf(key), limit = if (profileTab == ProfileTab.ISSUES) 80 else 8)) }, listener)
        val tab = profileTab
        val allRelays = profileReadRelays(key, tab)
        val sweepKey = Triple(key, tab, allRelays)
        if (profileSweepKey != sweepKey) {
            stopProfileSweep()
            profileSweepKey = sweepKey
            profileSweep = scope.launch {
                ready.await()
                val filters = profileKinds.flatMap { kind ->
                    Filter(authors = listOf(key), kinds = listOf(kind), limit = if (kind in tab.kinds) 80 else 1).activityFilters()
                }
                val podcastFilters = Filter(authors = listOf(key), kinds = listOf(1, 1111), limit = if (tab == ProfileTab.PODCASTS) 80 else 1).podcastTagFilters()
                val requests = filters + podcastFilters + recipeFilter(if (tab == ProfileTab.RECIPES) 80 else 1, author = key)
                profileQueries.fetch(allRelays, requests.map { it.toJson() }) { relay, event ->
                    active && viewedProfile == key && profileTab == tab && requests.any { it.match(event) } && accept(event, seenAt = relay)
                }
            }
        }
        val routes = if (tab == ProfileTab.RECIPES) allRelays.associate { it.normalizeRelayUrl() to listOf(recipeFilter(80, author = key)) } else profileFilters(key, tab.kinds, 80)
        // Separate kind filters keep ordinary notes from starving dedicated media posts.
        client.subscribe("profile", routes, listener)
        // Small independent existence probes keep rare kinds from being crowded out by notes.
        client.subscribe("profile-activity", profileFilters(key, listOf(1), 80).keys.associateWith {
            profileKinds.filter { it !in profileTab.kinds }.flatMap { kind ->
                Filter(kinds = listOf(kind), authors = listOf(key), limit = if (kind == 1) 80 else if (kind in listOf(10003, 30000, 30003)) 8 else 1).activityFilters()
            }
        }, listener)
    }
    @Synchronized
    fun viewThread(id: String?, clicked: Event? = null, retained: List<Event> = emptyList(), expanded: List<String> = emptyList(),
        ancestorLimit: Int = INITIAL_THREAD_ANCESTORS) {
        if (viewedThread == id) return
        val generation = threadGeneration.incrementAndGet()
        threadRestoreJob?.cancel()
        threadParentJob?.cancel()
        val cached = state.value
        val root = clicked?.takeIf { it.matchesThread(id) && it.kind in readableKinds }
            ?: cached.threadNotes.find { it.matchesThread(id) }
            ?: cached.embedded[id]
            ?: cached.profileEvents.find { it.id == id }
            ?: cached.hashtagNotes.find { it.id == id }
            ?: cached.notes.find { it.id == id }
        historyJob?.cancel()
        viewedThread = id
        threadContext = null; threadParents = emptyList()
        threadExpanded = expanded.takeLast(64).toSet()
        threadAncestorLimit = ancestorLimit.coerceIn(INITIAL_THREAD_ANCESTORS, MAX_THREAD_ANCESTORS)
        threadRequestedParent = null
        // Hand off the already rendered event synchronously; no disk or relay round trip.
        _state.update { it.copy(threadId = id, conversation = null, threadNotes = (listOfNotNull(root) + retained).distinctBy { it.id }.take(320).takeIf { id != null }.orEmpty(),
            loading = id != null && root == null) }
        threadProgress.begin(id)
        changed.tryEmit(Unit)
        if (id != null) threadRestoreJob = scope.launch {
            ready.await()
            if (viewedThread != id || threadGeneration.get() != generation) return@launch
            recentThreads.touch(id)
            recentThreads.remember(id, state.value.threadNotes)
            refreshConversation()
        }
        scope.launch {
            if (viewedThread == null) { client.unsubscribe("thread"); client.unsubscribe("thread-deletions") }
            if (active) subscribeFeed()
        }
    }
    fun expandThread(parent: String) {
        if (viewedThread == null || parent !in state.value.threadNotes.map { it.id }) return
        threadExpanded = (threadExpanded + parent).toList().takeLast(64).toSet()
        threadChanges.tryEmit(Unit)
        changed.tryEmit(Unit)
    }

    /** Extend this visit, never change its focused event or create a navigation entry. */
    fun loadThreadParent(row: ConversationRow): Int? {
        val id = viewedThread ?: return null
        val gap = threadContext?.takeIf { it.target == id }?.rows?.find { it.key == row.key } ?: return null
        val ref = gap.reference ?: return null
        if (gap.gap !in listOf(ThreadGap.MISSING, ThreadGap.LIMIT) ||
            (ref.kind != null && ref.kind !in readableKinds) || ref.author in state.value.mutedAuthors) return null
        if (gap.gap == ThreadGap.LIMIT && threadAncestorLimit >= MAX_THREAD_ANCESTORS) return null
        if (!threadProgress.retry(id, ref)) return null
        val retryExisting = synchronized(this) { ref.id in pendingEventLookups }
        if (gap.gap == ThreadGap.LIMIT) threadAncestorLimit = (threadAncestorLimit + 32).coerceAtMost(MAX_THREAD_ANCESTORS)
        threadRequestedParent = ref
        threadParentJob?.cancel()
        threadParentJob = scope.launch {
            try {
                ready.await()
                if (viewedThread != id || threadRequestedParent != ref) return@launch
                refreshConversation()
                if (active && viewedThread == id && threadRequestedParent == ref) {
                    // A cache miss may already have reached EOSE. Retry it only on this tap.
                    if (ref.addressFilter() != null) {
                        client.unsubscribe("embedded")
                        subscribeReadingData()
                    } else reconcileEventLookups(ref.id.takeIf { retryExisting })
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                threadProgress.failed(id, ref)
                if (viewedThread == id) _state.update { it.copy(error = "Could not load earlier conversation: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
        return threadAncestorLimit
    }
    private fun threadBranchAuthors(): List<String> = threadContext?.takeIf { it.target == viewedThread }?.rows.orEmpty()
        .mapNotNull { it.event?.takeIf { e -> e.id in threadParents }?.pubKey }.distinct().take(4)

    private fun threadRelays(id: String): List<String> {
        val root = state.value.threadNotes.find { it.matchesThread(id) }
        val author = root?.pubKey
        val issue = state.value.threadNotes.find { it.id == id }?.let { it.kind == 1621 || it.tagValue("K") == "1621" } == true
        val branchRelays = threadBranchAuthors().flatMap { relayLists[it]?.read.orEmpty() + relayLists[it]?.write.orEmpty() }
        return ((state.value.relays + openedReference?.takeIf { it.id == id }?.relays.orEmpty() + relayLists[author]?.read.orEmpty() + relayLists[author]?.write.orEmpty() + branchRelays).distinct().filter { it in routingPool } +
            if (issue) IssueReports.RELAYS else emptyList()) +
            if (root?.isAppDiscussion() == true || openedReference?.kind == 32267) listOf(ZAPSTORE_RELAY) else emptyList()
    }
    private fun subscribeThread(id: String) {
        discoverRelays()
        val root = state.value.threadNotes.find { it.matchesThread(id) }
        // An address may have a newer version. Replies are new events. Neither is an ID lookup.
        val filters = listOfNotNull(openedReference?.takeIf { it.id == id }?.addressFilter()) + ThreadRelations.discovery(root, id, threadParents)
        if (filters.isEmpty()) client.unsubscribe("thread")
        else client.subscribe("thread", threadRelays(id).associate { it.normalizeRelayUrl() to filters }, listener)
        val known = state.value.threadNotes.takeIf { state.value.threadId == id }.orEmpty()
        if (known.isEmpty()) client.unsubscribe("thread-deletions")
        else {
            val addresses = known.mapNotNull(ThreadRelations::address).distinct().sorted()
            val deletions = listOf(Filter(kinds = listOf(5), tags = mapOf("e" to known.map { it.id }.distinct().take(320).sorted()), limit = 256)) +
                if (addresses.isEmpty()) emptyList() else listOf(Filter(kinds = listOf(5), tags = mapOf("a" to addresses), limit = 256))
            client.subscribe("thread-deletions", threadRelays(id).associate { it.normalizeRelayUrl() to deletions }, listener)
        }
    }
    /** Restore this hashtag's cached events independently of home feed/engagement snapshots. */
    private fun restoreHashtagCache() {
        val generation = hashtagRestoreGeneration.incrementAndGet()
        hashtagRestoreJob?.cancel()
        val tag = viewedHashtag ?: return
        val author = viewedHashtagAuthor
        val tab = selectedHashtagTab
        val owner = state.value.publicKey
        val ceiling = hashtagUntil
        val count = hashtagLimit
        hashtagRestoreJob = scope.launch {
            ready.await()
            fun current() = hashtagRestoreGeneration.get() == generation && viewedHashtag == tag &&
                viewedHashtagAuthor == author && selectedHashtagTab == tab && state.value.publicKey == owner &&
                hashtagUntil == ceiling && hashtagLimit == count
            val events = hashtagTimeline(tag, author, tab, ceiling, count)
            ensureActive()
            if (!current()) return@launch
            rememberDeleted(events)
            _state.update { if (current()) it.copy(hashtagNotes = events, hashtagFor = tag,
                hashtagAuthor = author, hashtagTab = tab,
                hashtagCategories = it.hashtagCategories.takeIf { _ -> it.hashtagFor == tag && it.hashtagAuthor == author }.orEmpty()) else it }
            val profiles = cachedProfiles(events.take(24).map { it.pubKey }.distinct())
            ensureActive()
            if (current()) _state.update { if (current()) it.copy(profiles = it.profiles + profiles) else it }
        }
    }

    private suspend fun discoverHashtagCategories(
        tag: String?, author: String?, notes: List<Event>, contentFilter: ContentFilter,
    ): List<ProfileTab> {
        if (tag == null) return emptyList()
        val discovered = mutex.withLock {
            db.query<Event>(hashtagDiscoveryKinds.map { kind ->
                Filter(kinds = listOf(kind), authors = author?.let(::listOf),
                    tags = mapOf("t" to listOf(tag)), limit = 24)
            })
        }
        val events = latestProfileEvents(discovered + notes).filterNot(contentFilter::hides)
            .filterNot(::engagementDeleted)
        val found = hashtagTabs.filter { candidate -> events.any { it.matchesHashtagFeed(tag, author, candidate) } }
        val previous = state.value
        return (found + previous.hashtagCategories.takeIf {
            previous.hashtagFor == tag && previous.hashtagAuthor == author
        }.orEmpty()).distinct().sortedBy { it.orderPriority }
    }

    private suspend fun hashtagTimeline(tag: String, author: String?, tab: ProfileTab, until: Long?, limit: Int): List<Event> {
        val retained = state.value.hashtagNotes
        // One chronological window keeps cache and relay pagination on the same boundary.
        val filter = Filter(kinds = tab.hashtagKinds(), authors = author?.let(::listOf),
            tags = mapOf("t" to listOf(tag)), limit = limit, until = until)
        return timeline(filter, retained.filter { filter.match(it) }, limit)
    }

    @Synchronized
    fun viewHashtag(tag: String?, author: String? = null, tab: ProfileTab = ProfileTab.POSTS) {
        val scopedAuthor = author.takeIf { tag != null }
        val selected = tab.takeIf { tag != null && it in hashtagTabs } ?: ProfileTab.POSTS
        if (viewedHashtag == tag && viewedHashtagAuthor == scopedAuthor && selectedHashtagTab == selected) return
        historyJob?.cancel()
        if (viewedHashtag == tag && viewedHashtagAuthor == scopedAuthor) {
            hashtagWindows[selectedHashtagTab] = hashtagLimit to hashtagUntil
        } else hashtagWindows.clear()
        viewedHashtag = tag
        viewedHashtagAuthor = scopedAuthor
        selectedHashtagTab = selected
        hashtagUntil = hashtagWindows[selected]?.second
        hashtagLimit = hashtagWindows[selected]?.first ?: 200
        visibleIds = emptyList()
        _state.update { it.copy(error = null) }
        restoreHashtagCache()
        hashtagDiscoveryJob?.cancel()
        client.unsubscribe("hashtag-discovery")
        if (tag != null) hashtagDiscoveryJob = scope.launch {
            ready.await()
            delay(750)
            if (active && viewedHashtag == tag && viewedHashtagAuthor == scopedAuthor && selectedHashtagTab == selected) {
                val discovery = hashtagDiscoveryKinds.filter { it !in selected.kinds }.map { kind ->
                    Filter(kinds = listOf(kind), authors = scopedAuthor?.let(::listOf),
                        tags = mapOf("t" to listOf(tag)), limit = 8)
                }.flatMap { it.activityFilters() }
                client.subscribe("hashtag-discovery",
                    hashtagFilters(tag, author = scopedAuthor, tab = selected).keys.associateWith { discovery }, listener)
            }
        }
        changed.tryEmit(Unit)
        scope.launch {
            ready.await()
            if (viewedHashtag == null) client.unsubscribe("hashtag")
            if (active) subscribeFeed()
        }
    }
    private fun recipeRoutes(until: Long? = null, limit: Int = 80) =
        (listOf(RECIPE_RELAY) + state.value.relays.take(2)).distinct().associate {
            it.normalizeRelayUrl() to listOf(recipeFilter(limit, until))
        }
    private fun topicSince() = System.currentTimeMillis() / 1000 - 24 * 60 * 60
    private fun topicFilters() = state.value.relays.associate {
        it.normalizeRelayUrl() to listOf(Filter(kinds = listOf(1), since = topicSince(), limit = 200))
    }
    private fun homeFilters(authors: List<String>?, tags: Set<String>, until: Long? = null): Map<NormalizedRelayUrl, List<Filter>> {
        val base = authorFilters(authors, ProfileTab.POSTS.feedKinds(), 80, until)
        val extra = if (authors == null || tags.isEmpty()) emptyMap() else followedHashtagFilters(tags, until)
        return (base.keys + extra.keys).associateWith { base[it].orEmpty() + extra[it].orEmpty() }
    }
    private fun followedHashtagFilters(tags: Set<String>, until: Long? = null) = state.value.relays.associate {
        it.normalizeRelayUrl() to listOf(Filter(kinds = listOf(1), tags = mapOf("t" to tags.take(256)), limit = 80, until = until))
    }
    private fun hashtagFilters(tag: String, until: Long? = null, author: String? = null, tab: ProfileTab = selectedHashtagTab): Map<NormalizedRelayUrl, List<Filter>> {
        val relays = if (author == null) state.value.relays.map { it.normalizeRelayUrl() }
            else authorFilters(listOf(author), listOf(1), 80, until).keys.toList()
        val filters = listOf(Filter(kinds = tab.hashtagKinds(), authors = author?.let(::listOf),
            tags = mapOf("t" to listOf(tag)), limit = 80, until = until))
        return relays.associateWith { filters.flatMap { it.activityFilters() } }
    }
    @Synchronized
    fun older(author: String? = null): Boolean {
        if (!active || viewedThread != null || state.value.loadingOlder || (author == null && viewedHashtag == null && selectedFeed == ProfileTab.HASHTAGS)) return false
        val sequence = historySequence.incrementAndGet()
        val subscription = "older-$sequence"
        val profile = viewedProfile
        val tab = profileTab
        val feed = selectedFeed
        val tag = viewedHashtag
        val tagAuthor = viewedHashtagAuthor
        val tagTab = selectedHashtagTab
        val account = state.value.publicKey
        val followedTagsAtStart = state.value.followedHashtags
        fun sameScreen() = active && profile == viewedProfile && tab == profileTab && feed == selectedFeed && tag == viewedHashtag && tagAuthor == viewedHashtagAuthor && tagTab == selectedHashtagTab && viewedThread == null && account == state.value.publicKey && (feed != ProfileTab.POSTS || followedTagsAtStart == state.value.followedHashtags)
        _state.update { it.copy(loadingOlder = true) }
        historyJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                ready.await()
                if (!sameScreen()) return@launch
                val current = state.value
                val homeFeed = author == null && tag == null && feed == ProfileTab.POSTS
                val authors = if (author == null && tag == null && feed == ProfileTab.RECIPES) null else if (tag != null) tagAuthor?.let(::listOf) else author?.let(::listOf) ?: account?.let { (listOf(it) + current.following).take(1000) }
                val kinds = if (author != null) tab.kinds else if (tag != null) tagTab.hashtagKinds() else feed.feedKinds()
                val events = (if (tag != null) current.hashtagNotes.takeIf {
                    current.hashtagFor == tag && current.hashtagAuthor == tagAuthor && current.hashtagTab == tagTab
                }.orEmpty() else if (author != null) current.profileEvents else current.notes)
                    .filter { it.kind in kinds && (authors == null || it.pubKey in authors || (homeFeed && it.matchesHashtags(current.followedHashtags)) || (it.kind == 9321 && it.tagValue("p") in authors) || (it.kind == 9735 && (it.tagValue("p") in authors || it.tagValue("P") in authors))) }
                val oldest = events.minOfOrNull { it.createdAt } ?: return@launch
                if (oldest <= 0) return@launch
                val filter = if (author != null && tab == ProfileTab.RECIPES) recipeFilter(80, oldest - 1, author) else if (author == null && tag == null && feed == ProfileTab.RECIPES) recipeFilter(80, oldest - 1) else Filter(kinds = kinds, authors = authors, tags = tag?.let { mapOf("t" to listOf(it)) }, limit = 80, until = oldest - 1)
                val filters = filter.activityFilters() + if (homeFeed && authors != null && current.followedHashtags.isNotEmpty())
                    listOf(Filter(kinds = listOf(1), tags = mapOf("t" to current.followedHashtags.toList()), limit = 80, until = oldest - 1)) else emptyList()
                val cached = mutex.withLock { db.query<Event>(filters) }
                var found = cached.isNotEmpty()
                if (author != null) {
                    // Local history is not evidence that every write relay was queried.
                    val filters = if (tab == ProfileTab.RECIPES) listOf(recipeFilter(80, oldest - 1, author)) else kinds.flatMap { kind -> Filter(kinds = listOf(kind), authors = listOf(author), limit = 80, until = oldest - 1).activityFilters() } +
                        if (tab == ProfileTab.PODCASTS) Filter(kinds = listOf(1, 1111), authors = listOf(author), limit = 80, until = oldest - 1).podcastTagFilters() else emptyList()
                    val received = profileQueries.fetch(profileReadRelays(author, tab), filters.map { it.toJson() }) { relay, event ->
                        sameScreen() && historySequence.get() == sequence && filters.any { it.match(event) } && accept(event, history = true, seenAt = relay)
                    }
                    found = found || received
                } else if (!found) {
                    val routes = if (author == null && tag == null && feed == ProfileTab.RECIPES) recipeRoutes(oldest - 1) else if (homeFeed) homeFilters(authors, current.followedHashtags, oldest - 1) else if (tag != null) hashtagFilters(tag, oldest - 1, tagAuthor, tagTab) else if (author != null) profileFilters(author, kinds, 80, oldest - 1) else authorFilters(authors, kinds, 80, oldest - 1)
                    if (routes.isEmpty()) return@launch
                    val complete = CompletableDeferred<Unit>()
                    val ended = java.util.concurrent.ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()
                    val received = java.util.concurrent.atomic.AtomicBoolean()
                    val pageListener = object : SubscriptionListener {
                        override suspend fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                            if (sameScreen() && historySequence.get() == sequence && forFilters?.any { it.match(event) } == true && accept(event, history = true, seenAt = relay.url)) received.set(true)
                        }
                        override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                            ended.add(relay)
                            if (ended.containsAll(routes.keys)) complete.complete(Unit)
                        }
                        override fun onClosed(message: String, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                            ended.add(relay)
                            if (ended.containsAll(routes.keys)) complete.complete(Unit)
                        }
                    }
                    client.subscribe(subscription, routes, pageListener)
                    withTimeoutOrNull(12_000) { complete.await() }
                    found = received.get()
                }
                ensureActive()
                if (!sameScreen() || !found) return@launch
                // Keep the visible tail. Once capped, discard only one page's worth of
                // off-screen leading items so the next page fits, without replacing the list.
                val ceiling = events.getOrNull(80)?.createdAt
                if (tag != null) {
                    if (events.size >= 600) hashtagUntil = ceiling else hashtagLimit = (hashtagLimit + 80).coerceAtMost(600)
                } else if (author != null) {
                    if (events.size >= 600) profileUntil = ceiling else profileLimit = (profileLimit + 80).coerceAtMost(600)
                } else if (events.size >= 600) feedUntil = ceiling
                snapshot()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (sameScreen()) _state.update { it.copy(error = "Couldn't load older notes. Pull to refresh.") }
            } finally {
                client.unsubscribe(subscription)
                if (historySequence.get() == sequence) _state.update { it.copy(loadingOlder = false) }
            }
        }
        historyJob?.start()
        return true
    }
    companion object { fun shortKey(key: String): String = "${key.take(8)}…${key.takeLast(4)}" }
}
