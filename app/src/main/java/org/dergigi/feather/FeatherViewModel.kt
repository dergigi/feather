package org.dergigi.feather

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeatherViewModel(application: Application) : AndroidViewModel(application) {
    val repo = (application as FeatherApplication).repository
    internal val onboarding = Onboarding(application, viewModelScope)
    val state = repo.state
    val message = MutableStateFlow<String?>(null)
    internal val audioHeart = MutableStateFlow<Pair<Event, String>?>(null)
    internal val incomingLink = MutableStateFlow<Pair<Long, NostrContent.Reference>?>(null)
    private var linkJob: Job? = null
    private var linkSequence = 0L
    internal fun receiveNostrLink(raw: String) {
        linkJob?.cancel()
        val sequence = ++linkSequence
        linkJob = viewModelScope.launch {
            val reference = withContext(Dispatchers.Default) {
                raw.takeIf { it.length <= 8192 }?.let(NostrContent::decode)
            }
            if (reference == null) message.value = "Invalid or unsupported Nostr link."
            else incomingLink.value = sequence to reference
        }
    }
    internal val notificationFilters = MutableStateFlow<Pair<String?, Int>?>(null)
    private val notificationFilterMutex = Mutex()
    private fun notificationPreferences() = getApplication<Application>().getSharedPreferences("notification-filters", android.content.Context.MODE_PRIVATE)
    private suspend fun readNotificationFilters(owner: String) = withContext(Dispatchers.IO) {
        notificationFilterMutex.withLock { notificationPreferences().getInt(owner, 0) and NotificationFilterSettings.MASK }
    }
    private suspend fun applyNotificationFilters(owner: String, mask: Int) = withContext(Dispatchers.IO) {
        notificationFilterMutex.withLock {
            if (state.value.publicKey != owner) return@withLock
            val prefs = notificationPreferences()
            val edit = prefs.edit().putInt(owner, mask)
            prefs.all.keys.filter { it != owner }.drop(31).forEach(edit::remove)
            check(edit.commit()) { "Could not save notification filters" }
            if (state.value.publicKey == owner) notificationFilters.value = owner to mask
        }
    }
    internal fun setNotificationFilters(mask: Int) {
        val owner = state.value.publicKey ?: return
        if (notificationFilters.value?.first != owner) return
        val selection = mask and NotificationFilterSettings.MASK
        notificationFilters.value = owner to selection
        viewModelScope.launch {
            try { accountSettings.change(filters = selection, expectedOwner = owner) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "Could not save notification filters" }
        }
    }
    internal val wallet = ZapWallet(application)
    val walletBusy = MutableStateFlow(false)
    val walletError = MutableStateFlow<String?>(null)
    internal val walletDetails = MutableStateFlow(WalletDetails())
    internal suspend fun refreshWalletDetails() {
        val identity = signer.value ?: return
        walletDetails.value = WalletDetails()
        try { wallet.details(identity.pubKey) { if (signer.value === identity) walletDetails.value = it } }
        catch (_: TimeoutCancellationException) { /* Optional metadata must not interrupt wallet use. */ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep any available name; omit unavailable balances. */ }
    }
    val zapping = MutableStateFlow<Set<String>>(emptySet())
    internal val zapCelebrations = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var zapCelebrationSequence = 0L
    internal val signerRequests = SignerRequests()
    internal val accountSettings by lazy { AccountSettings(getApplication(), viewModelScope, repo, { signer.value }, signerRequests, ::readNotificationFilters, ::applyNotificationFilters) { message.value = it } }
    internal fun changeSettings(sensitive: Boolean? = null, pow: Boolean? = null) = viewModelScope.launch {
        try { accountSettings.change(sensitive, pow) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message.value = "Settings: ${e.message}" }
    }
    internal val accountReports by lazy { AccountReports(application, viewModelScope, repo, { signer.value }, signerRequests, ::muteAccount) }
    internal val issues by lazy { IssueReports(application, viewModelScope, { signer.value }, signerRequests) }
    internal val followBusy = MutableStateFlow<Set<String>>(emptySet())
    internal val firstFollow = MutableStateFlow<Pair<String, String>?>(null)
    private val followJobs = mutableMapOf<String, Job>()
    private val followQueueMutex = Mutex()
    private val followLists by lazy { FollowLists(repo, signerRequests) }
    internal fun changeHashtagFollow(target: String, follow: Boolean, allowCreate: Boolean = false) {
        val tag = normalizeFollowedTag(target) ?: return
        changeFollow(tag, follow, allowCreate, hashtag = true)
    }
    internal fun changeFollow(target: String, follow: Boolean, allowCreate: Boolean = false, hashtag: Boolean = false) {
        val identity = signer.value ?: return
        val actionKey = if (hashtag) "#$target" else target
        if (actionKey in followJobs) return
        if (followJobs.size >= 16) {
            message.value = "Follow queue is full. Try again shortly."
            return
        }
        if (firstFollow.value?.second == actionKey) firstFollow.value = null
        followBusy.update { it + actionKey }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Queue intentions, not signed snapshots. Each edit reads the result of
                // the previous edit and refreshes it before requesting a signature.
                followQueueMutex.withLock {
                    check(signer.value === identity) { "Account changed" }
                    if (hashtag) followLists.changeHashtag(identity, target, follow, allowCreate)
                    else followLists.change(identity, target, follow, allowCreate)
                }
                if (!follow && !hashtag && signer.value === identity) {
                    val app = getApplication<Application>()
                    AppMessages.showAction(app.getString(R.string.ui_unfollowed), app.getString(R.string.ui_follow_undo)) {
                        if (signer.value === identity) changeFollow(target, true, hashtag = hashtag)
                    }
                }
            } catch (_: MissingFollowList) {
                if (signer.value === identity) firstFollow.value = identity.pubKey to actionKey
            } catch (e: TimeoutCancellationException) {
                if (signer.value === identity) message.value = "Follow operation timed out. Refresh before trying again."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (signer.value === identity) AppMessages.show("Follow: ${e.message}", e.stackTraceToString())
            } finally {
                if (followJobs[actionKey] === currentCoroutineContext()[Job]) {
                    followJobs.remove(actionKey)
                    followBusy.update { it - actionKey }
                }
            }
        }
        followJobs[actionKey] = job
        job.start()
    }
    internal val pendingAccountMutes = MutableStateFlow<Set<String>>(emptySet())
    internal val pendingAccountUnmutes = MutableStateFlow<Set<String>>(emptySet())
    private val accountMuteJobs = mutableMapOf<String, Job>()

    internal fun muteAccount(pubkey: String) = changeAccountMute(pubkey, true)
    internal fun unmuteAccount(pubkey: String) = changeAccountMute(pubkey, false)

    private fun changeAccountMute(pubkey: String, mute: Boolean) {
        val identity = signer.value ?: return
        if (Protocol.publicKey(pubkey) != pubkey || pubkey == identity.pubKey ||
            (pubkey in state.value.mutedAuthors) == mute || pubkey in pendingAccountMutes.value ||
            pubkey in pendingAccountUnmutes.value || pubkey in accountMuteJobs) return
        val pending = if (mute) pendingAccountMutes else pendingAccountUnmutes
        if (accountMuteJobs.size >= 16 || pending.value.size >= 64) {
            message.value = "Action queue is full. Try again shortly."
            return
        }
        pending.update { it + pubkey }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                followQueueMutex.withLock {
                    check(signer.value === identity) { "Account changed" }
                    followLists.changeAccountMute(identity, pubkey, mute)
                }
                // Retain the optimistic mute until the repository has applied the signed list.
                if (signer.value === identity && (pubkey in state.value.mutedAuthors) == mute)
                    pending.update { it - pubkey }
            } catch (e: TimeoutCancellationException) {
                if (signer.value === identity) {
                    pending.update { it - pubkey }
                    AppMessages.show("Mute list: request timed out", e.stackTraceToString())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (signer.value === identity) {
                    pending.update { it - pubkey }
                    AppMessages.show("Mute list: ${e.message}", e.stackTraceToString())
                }
            } finally {
                if (accountMuteJobs[pubkey] === currentCoroutineContext()[Job]) accountMuteJobs.remove(pubkey)
            }
        }
        accountMuteJobs[pubkey] = job
        job.start()
    }

    internal data class MuteWordEdit(val id: Long, val word: String, val add: Boolean, val complete: Boolean = false)
    internal val muteWordEdits = MutableStateFlow<List<MuteWordEdit>>(emptyList())
    private val muteWordJobs = mutableMapOf<Long, Job>()
    private var muteWordSequence = 0L

    internal fun changeMutedWord(raw: String, add: Boolean): Boolean {
        val word = normalizeMutedWord(raw)
        val identity = signer.value ?: return false
        if (word.isBlank() || word.length > 256) return false
        val present = muteWordEdits.value.lastOrNull { it.word == word }?.add ?: (word in state.value.mutedWords)
        if (present == add) return true
        if (muteWordJobs.size >= 16 || muteWordEdits.value.size >= 5000) {
            message.value = "Action queue is full. Try again shortly."
            return false
        }
        val id = ++muteWordSequence
        muteWordEdits.update { it + MuteWordEdit(id, word, add) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                followQueueMutex.withLock {
                    check(signer.value === identity) { "Account changed" }
                    followLists.changeMutedWord(identity, word, add)
                }
                if (signer.value === identity) {
                    // Keep the successful intent visible until the repository catches up.
                    // Newer queued edits to this word remain on top of it.
                    muteWordEdits.update { edits ->
                        edits.filterNot { it.word == word && it.complete }.map {
                            if (it.id == id) it.copy(complete = true) else it
                        }
                    }
                    reconcileMutedWords()
                }
            } catch (e: TimeoutCancellationException) {
                if (signer.value === identity) {
                    muteWordEdits.update { it.filterNot { edit -> edit.id == id } }
                    AppMessages.show("Mute list: request timed out", e.stackTraceToString())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (signer.value === identity) {
                    muteWordEdits.update { it.filterNot { edit -> edit.id == id } }
                    AppMessages.show("Mute list: ${e.message}", e.stackTraceToString())
                }
            } finally { muteWordJobs.remove(id) }
        }
        muteWordJobs[id] = job
        job.start()
        return true
    }

    private fun reconcileMutedWords() {
        val words = state.value.mutedWords
        muteWordEdits.update { edits -> edits.filterNot { it.complete && ((it.word in words) == it.add) } }
    }
    private val paymentActions = ActionLanes(2)
    private val zapJobs = mutableMapOf<Triple<String, Long, String>, Job>()
    val reposting = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val repostJobs = mutableMapOf<String, Job>()
    private val reactionJobs = mutableMapOf<String, Job>()
    // Wallet confirmation can be slow. Keep its backlog independent of social actions,
    // while retaining finite queues and the existing per-note/signer/payment ordering.
    private companion object {
        const val MAX_QUEUED_ZAPS = 32
        const val MAX_QUEUED_REACTIONS = 64
        const val MAX_QUEUED_REPOSTS = 32
    }
    private fun cancelActions() {
        accountMuteJobs.values.toList().forEach { it.cancel() }; accountMuteJobs.clear(); pendingAccountMutes.value = emptySet(); pendingAccountUnmutes.value = emptySet()
        muteWordJobs.values.toList().forEach { it.cancel() }; muteWordJobs.clear(); muteWordEdits.value = emptyList()
        followJobs.values.toList().forEach { it.cancel() }; followJobs.clear()
        followBusy.value = emptySet(); firstFollow.value = null
        issues.accountChanged()
        accountReports.accountChanged()
        if (signer.value != null) cancelSharedMedia()
        cancelImageUpload(); replyJob?.cancel(); replyLoadJob?.cancel(); replyDraft.value = null; replyMining.value = false
        zapJobs.values.toList().forEach { it.cancel() }
        reactionJobs.values.toList().forEach { it.cancel() }
        repostJobs.values.toList().forEach { it.cancel() }; repostJobs.clear(); reposting.value = emptyMap()
        zapJobs.clear(); reactionJobs.clear()
        zapCelebrations.value = emptyMap()
        zapping.value = emptySet(); reacting.value = emptySet(); optimisticReactions.value = emptyMap()
    }
    private var walletJob: Job? = null
    fun connectWallet(raw: String) {
        val identity = signer.value ?: return
        if (walletBusy.value || zapping.value.isNotEmpty()) return
        walletBusy.value = true; walletError.value = null
        walletJob = viewModelScope.launch {
            try { wallet.connect(raw, identity.pubKey) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: WalletFailure) {
                AppMessages.show(failure.message ?: "Wallet connection failed", failure.report ?: failure.message ?: "Wallet connection failed")
            }
            catch (failure: Exception) { AppMessages.show("NWC: ${walletErrorReason(failure)}") }
            finally { walletBusy.value = false }
        }
    }
    fun disconnectWallet() {
        if (signer.value == null || walletBusy.value || zapping.value.isNotEmpty()) return
        walletBusy.value = true
        walletJob = viewModelScope.launch(Dispatchers.IO) {
            try { wallet.store.disconnect(); walletDetails.value = WalletDetails(); walletError.value = null }
            catch (_: Exception) { walletError.value = "Could not disconnect wallet" }
            finally { walletBusy.value = false }
        }
    }
    fun zap(note: Event, amountSats: Long = DEFAULT_ZAP_SATS, comment: String = "") {
        if (amountSats !in 1..MAX_ZAP_SATS) return
        val request = Triple(note.id, amountSats, comment.trimEnd())
        if (request in zapJobs) return
        if (zapJobs.size >= MAX_QUEUED_ZAPS) {
            message.value = "Zap queue is full. Wait for pending payments to finish."
            return
        }
        if (walletBusy.value) { message.value = "Wallet connection is being updated. Try again shortly."; return }
        val connected = signer.value ?: run { message.value = "Connect an Android signer first."; return }
        zapping.update { it + note.id }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                paymentActions.withLock(note.id) {
                    check(signer.value === connected) { "Account changed" }
                    val newlyConfirmed = wallet.zap(note, state.value.profiles[note.pubKey]?.lightning, (state.value.relays + if (note.isAppDiscussion()) listOf(ZAPSTORE_RELAY) else emptyList()).distinct(), connected, signerRequests, amountSats, comment) { repo.zapRecipientProfiles(note) }
                    if (signer.value === connected && newlyConfirmed) {
                        val sequence = ++zapCelebrationSequence
                        zapCelebrations.update { previous -> (previous - note.id).entries.toList().takeLast(63)
                            .associate { it.toPair() } + (note.id to sequence) }
                    }
                }
            }
            catch (_: TimeoutCancellationException) { message.value = "Zap not confirmed. Check your wallet; tap again to check payment status." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: WalletFailure) { AppMessages.show(failure.message ?: "Zap failed", failure.report ?: failure.message ?: "Zap failed") }
            catch (failure: Exception) { message.value = "Zap: ${walletErrorReason(failure)}" }
            finally {
                if (zapJobs[request] === currentCoroutineContext().job) {
                    zapJobs.remove(request)
                    if (zapJobs.keys.none { it.first == note.id }) zapping.update { it - note.id }
                }
            }
        }
        // Different explicit amounts/messages queue behind the active payment. The wallet journal
        // still checks an uncertain previous payment instead of sending another invoice.
        zapJobs[request] = job
        job.start()
    }
    val reacting = MutableStateFlow<Set<String>>(emptySet())
    internal val optimisticReactions = MutableStateFlow<Map<String, List<PendingReaction>>>(emptyMap())
    private var hashtagJob: Job? = null
    private var attemptedHashtags: String? = null
    fun loadHashtags(event: Event?) {
        val connected = signer.value ?: return
        if (event == null || event.pubKey != connected.pubKey || attemptedHashtags == event.id) return
        hashtagJob?.cancel()
        attemptedHashtags = event.id
        hashtagJob = viewModelScope.launch {
            try { signerRequests.run { repo.decryptHashtagList(event, connected) } }
            catch (_: TimeoutCancellationException) { message.value = "Followed hashtags could not be read. Pull to refresh to retry." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Followed hashtags could not be read. Pull to refresh to retry." }
        }
    }
    fun retryHashtags() {
        if (hashtagJob?.isActive == true) return
        attemptedHashtags = null
        loadHashtags(state.value.pendingHashtagList)
    }

    val decryptingMutes = MutableStateFlow(false)
    private var muteJob: Job? = null
    private var attemptedMute: String? = null

    fun loadMutes(event: Event?) {
        val connected = signer.value ?: return
        if (event == null || event.pubKey != connected.pubKey || attemptedMute == event.id) return
        muteJob?.cancel()
        attemptedMute = event.id
        decryptingMutes.value = true
        muteJob = viewModelScope.launch {
            try { signerRequests.run { repo.decryptMuteList(event, connected) } }
            catch (_: TimeoutCancellationException) { message.value = "Private mute list could not be read. Public mutes still apply." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Private mute list could not be read. Public mutes still apply." }
            finally { if (attemptedMute == event.id) decryptingMutes.value = false }
        }
    }

    fun retryMutes() {
        if (muteJob?.isActive == true) return
        attemptedMute = null
        loadMutes(state.value.pendingMuteList)
    }

    fun cancelSignerRequest(reason: String? = null) {
        signerRequests.cancel()
        if (reason != null) message.value = reason
    }
    private fun signerFor(key: String?, packageName: String?) =
        if (key != null && packageName != null) NostrSignerExternal(key, packageName, getApplication<Application>().contentResolver) else null
    private val initialIdentity = state.value.publicKey to state.value.signerPackage
    val signer = MutableStateFlow(signerFor(initialIdentity.first, initialIdentity.second))

    private suspend fun selectWalletAccount(owner: String?) {
        try { withContext(Dispatchers.IO) { wallet.store.selectAccount(owner) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message.value = "Could not update the wallet connection for this account." }
    }

    init {
        viewModelScope.launch { state.map { it.mutedAuthors }.distinctUntilChanged().collect { authors ->
            pendingAccountMutes.update { it - authors }
            pendingAccountUnmutes.update { it.intersect(authors) }
        } }
        viewModelScope.launch { state.map { it.mutedWords }.distinctUntilChanged().collect { reconcileMutedWords() } }
        viewModelScope.launch {
            state.map { it.publicKey }.distinctUntilChanged().collectLatest { owner ->
                notificationFilters.value = null
                withContext(Dispatchers.IO) {
                    notificationFilterMutex.withLock {
                        val mask = if (owner == null) 0 else notificationPreferences().getInt(owner, 0) and NotificationFilterSettings.MASK
                        if (state.value.publicKey == owner) notificationFilters.value = owner to mask
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { wallet.store.load() }
        viewModelScope.launch {
            var identity = initialIdentity
            selectWalletAccount(signer.value?.pubKey)
            state.map { it.publicKey to it.signerPackage }.distinctUntilChanged().collect { next ->
                if (next != identity) {
                    cancelActions()
                    hashtagJob?.cancel(); attemptedHashtags = null
                    muteJob?.cancel(); attemptedMute = null; decryptingMutes.value = false
                    signer.value = null
                    walletJob?.cancelAndJoin()
                    walletDetails.value = WalletDetails(); walletError.value = null
                    val nextSigner = signerFor(next.first, next.second)
                    selectWalletAccount(nextSigner?.pubKey)
                    signer.value = nextSigner
                    identity = next
                }
            }
        }
    }
    fun login(key: String, packageName: String) = viewModelScope.launch {
        runCatching { repo.account(key, packageName) }
            .onFailure { message.value = "Could not connect the signer. Please try again." }
    }
    fun logout() = viewModelScope.launch {
        cancelActions(); muteJob?.cancel(); hashtagJob?.cancel(); attemptedHashtags = null
        signer.value = null
        walletJob?.cancelAndJoin()
        walletDetails.value = WalletDetails(); walletError.value = null
        selectWalletAccount(null)
        repo.logout()
    }

    internal val replyDraft = MutableStateFlow<ReplyDraft?>(null)
    internal val replyBusy = MutableStateFlow(false)
    internal val replyMining = MutableStateFlow(false)
    private val replyStore = ReplyStore(application)
    private var replyJob: Job? = null
    private var replyLoadJob: Job? = null
    private var replySaveJob: Job? = null

    private val sharedMedia = ArrayDeque<android.net.Uri>()
    private var sharedMediaJob: Job? = null

    internal fun receiveSharedMedia(uris: List<android.net.Uri>) {
        if (replyBusy.value) {
            message.value = "Wait for the current post to finish sending, then share again."
            return
        }
        if (sharedMedia.size + uris.size > 256) {
            message.value = "Too many queued attachments. Wait for uploads to finish, then share again."
            return
        }
        sharedMedia.addAll(uris)
        if (signer.value == null) message.value = "Connect an Android signer to upload shared media."
        processSharedMedia()
    }

    internal fun processSharedMedia() {
        val identity = signer.value ?: return
        if (sharedMedia.isEmpty() || sharedMediaJob?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Keep an open reply's target and text; otherwise restore the saved new-post draft.
                replyLoadJob?.join()
                if (replyDraft.value == null) { openReply(); replyLoadJob?.join() }
                if (replyDraft.value == null) { sharedMedia.clear(); return@launch }
                while (sharedMedia.isNotEmpty()) {
                    imageUploadJob?.join()
                    ensureActive()
                    if (signer.value !== identity || replyDraft.value == null) break
                    val batch = sharedMedia.toList()
                    sharedMedia.clear()
                    uploadImages(batch)
                    imageUploadJob?.join()
                }
            } finally {
                if (sharedMediaJob === currentCoroutineContext().job) sharedMediaJob = null
            }
        }
        sharedMediaJob = job
        job.start()
    }

    private fun cancelSharedMedia() {
        sharedMedia.clear()
        sharedMediaJob?.cancel()
        sharedMediaJob = null
    }

    internal val imageUploading = MutableStateFlow(false)
    internal val imageUploadProgress = MutableStateFlow<ImageUploadProgress?>(null)
    private var imageUploadJob: Job? = null
    // A canceled provider read can take time to return. Keep file work serialized without locking the composer.
    private val imageUploadMutex = Mutex()
    private fun cancelImageUpload() {
        val previous = imageUploadJob
        imageUploadJob = null
        imageUploading.value = false
        imageUploadProgress.value = null
        previous?.cancel()
    }
    private val blossom = BlossomUpload(application)
    internal fun uploadImages(uris: List<android.net.Uri>) {
        val draft = replyDraft.value ?: return
        val identity = signer.value ?: return
        if (replyBusy.value || imageUploading.value || uris.isEmpty() || draft.owner != identity.pubKey) return
        imageUploadProgress.value = ImageUploadProgress(stage = "Reading file size")
        imageUploading.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val uploadJob = currentCoroutineContext().job
            var completed = false
            fun currentUpload() = uploadJob.isActive && imageUploadJob === uploadJob
            fun progress(value: ImageUploadProgress) {
                if (currentUpload()) imageUploadProgress.value = value
            }
            try {
                progress(ImageUploadProgress(stage = "Waiting for previous upload"))
                imageUploadMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    progress(ImageUploadProgress(stage = "Reading file size"))
                    val size = blossom.selectedSize(uris.first())
                    progress(ImageUploadProgress(size, stage = "Finding Blossom servers"))
                    val servers = withTimeout(30_000) { repo.blossomServers(draft.owner) }
                    for (uri in uris.distinct()) {
                        check(signer.value === identity) { "Account changed" }
                        currentCoroutineContext().ensureActive()
                        progress(ImageUploadProgress(stage = "Reading file size"))
                        val selectedSize = blossom.selectedSize(uri)
                        progress(ImageUploadProgress(selectedSize))
                        val result = blossom.upload(uri, servers, identity, signerRequests, imagesOnly = draft.picturePost, onProgress = { progress ->
                            if (uploadJob.isActive && imageUploadJob === uploadJob)
                                imageUploadProgress.value = progress.copy(size = progress.size ?: selectedSize)
                        }) { attachment ->
                            withContext(Dispatchers.Main) {
                                check(currentUpload()) { "Upload canceled" }
                                val latest = replyDraft.value
                                check(latest != null && latest.owner == draft.owner && latest.target?.id == draft.target?.id && latest.picturePost == draft.picturePost) { "Composer closed" }
                                val url = attachment.url
                                require(!latest.picturePost || url in latest.pictureUrls() || latest.pictureUrls().size < 32) { "Photo draft is full" }
                                require(!latest.picturePost || attachment.mime in pictureMimeTypes) { "Picture posts require images" }
                                val text = if (latest.picturePost) latest.text else latest.text.trimEnd() + (if (latest.text.isBlank()) "" else "\n") + url
                                require(text.length <= 8000) { "Draft is full; uploaded attachment: $url" }
                                replyDraft.value = latest.copy(uploads = (latest.uploads.filterNot { it.url == url } + attachment).takeLast(32))
                                editReply(text)
                            }
                            replySaveJob?.join()
                        }
                        if (currentUpload() && result.failures.isNotEmpty()) message.value = "Media uploaded; some Blossom servers failed: ${result.failures.joinToString("; ")}"
                    }
                    completed = true
                }
            } catch (e: TimeoutCancellationException) { if (currentUpload()) message.value = "Media upload: ${imageUploadProgress.value?.stage ?: "Upload"} timed out" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (currentUpload()) message.value = "Media upload (${imageUploadProgress.value?.stage ?: "unknown stage"}): ${e.message ?: e.javaClass.simpleName}" }
            finally {
                if (imageUploadJob === uploadJob) {
                    imageUploadProgress.value = if (completed && uploadJob.isActive && replyDraft.value != null)
                        imageUploadProgress.value?.copy(percent = 100) else null
                    imageUploading.value = false
                }
            }
        }
        imageUploadJob = job
        job.start()
    }

    internal fun clearImageUploadProgress(expected: ImageUploadProgress) {
        if (!imageUploading.value) imageUploadProgress.compareAndSet(expected, null)
    }

    internal fun openReply(note: Event? = null, mentionProfile: String? = null, quote: Event? = null, selectedText: String? = null, picturePost: Boolean = false) {
        val owner = signer.value?.pubKey ?: return
        if (replyBusy.value || imageUploading.value) return
        imageUploadProgress.value = null
        replyLoadJob?.cancel()
        replyLoadJob = viewModelScope.launch {
            try {
                replySaveJob?.join()
                val draft = withContext(Dispatchers.IO) {
                    val saved = replyStore.load(owner, note, picturePost && note == null)
                    val mention = mentionProfile?.let(Protocol::publicKey)?.takeIf { note == null }
                    if (selectedText != null && note != null) {
                        saved.withQuotedSelection(selectedText).also(replyStore::save)
                    } else if (quote != null && note == null) {
                        val pointer = repo.reference(quote)
                        val reference = "nostr:$pointer"
                        if (reference in saved.text) saved else {
                            val text = saved.text.trimEnd() + "\n" + reference
                            require(text.length <= 8000) { "Saved draft is full. Shorten it before adding a quote." }
                            saved.copy(text = text, initialCursor = saved.text.trimEnd().length).also(replyStore::save)
                        }
                    } else if (mention == null || mention in replyMentions(saved.text)) saved
                    else {
                        val text = "nostr:${Protocol.npub(mention)} ${saved.text}"
                        require(text.length <= 8000) { "Saved draft is full. Shorten it before adding a mention." }
                        saved.copy(text = text).also(replyStore::save)
                    }
                }
                if (signer.value?.pubKey == owner) replyDraft.value = draft
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Could not open composer: ${e.message}" }
        }
    }
    internal fun attachGif(gif: GifItem) {
        val draft = replyDraft.value ?: return
        if (replyBusy.value || draft.owner != signer.value?.pubKey || gif.url in draft.text || draft.gifs.any { it.url == gif.url }) return
        val text = if (draft.picturePost) draft.text else draft.text.trimEnd() + (if (draft.text.isBlank()) "" else "\n") + gif.url
        if (text.length > 8000 || (if (draft.picturePost) draft.pictureUrls().size else draft.gifs.size) >= 32) { message.value = "Draft is full"; return }
        replyDraft.value = draft.copy(gifs = draft.gifs + gif)
        editReply(text)
    }
    internal fun editReply(text: String) {
        if (replyBusy.value) return
        val previous = replyDraft.value ?: return
        val draft = previous.copy(text = text.take(8000), gifs = previous.gifs.filter { previous.picturePost || it.url in text }, uploads = previous.uploads.filter { previous.picturePost || it.url in text })
        replyDraft.value = draft
        replySaveJob?.cancel()
        replySaveJob = viewModelScope.launch {
            delay(300)
            try { withContext(Dispatchers.IO) { replyStore.save(draft) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Could not save draft: ${e.message}" }
        }
    }
    internal fun removePicture(url: String) {
        val draft = replyDraft.value ?: return
        if (replyBusy.value || !draft.picturePost) return
        replyDraft.value = draft.copy(uploads = draft.uploads.filterNot { it.url == url }, gifs = draft.gifs.filterNot { it.url == url })
        editReply(draft.text)
    }
    internal fun closeReply() {
        cancelSharedMedia()
        cancelImageUpload()
        replyLoadJob?.cancel()
        val draft = replyDraft.value
        replyDraft.value = null
        if (draft == null || replyBusy.value) return
        replySaveJob?.cancel()
        replySaveJob = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { replyStore.save(draft) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Could not save draft: ${e.message}" }
        }
    }
    private var publicationFailureMessage: Long? = null

    private fun showPublicationFailure(draft: ReplyDraft, text: String, error: Exception) {
        if (signer.value?.pubKey != draft.owner) return
        publicationFailureMessage = AppMessages.showTappable(text, "$text\n\n${error.stackTraceToString()}",
            getApplication<Application>().getString(R.string.ui_reopen_draft)) {
            if (signer.value?.pubKey == draft.owner) {
                val open = replyDraft.value
                // Keep any newer edits in the already-open composer. Save a different
                // draft before switching back to the failed post or reply.
                if (open?.owner != draft.owner || open?.target?.id != draft.target?.id || open?.picturePost != draft.picturePost) {
                    if (open != null) closeReply()
                    openReply(draft.target, picturePost = draft.picturePost)
                }
            }
        }
    }

    internal fun sendReply() {
        val current = replyDraft.value ?: return
        val draft = current.copy(text = current.text.trimEnd())
        val identity = signer.value ?: return
        if (replyBusy.value || imageUploading.value || (if (draft.picturePost) draft.pictureUrls().isEmpty() else draft.text.isBlank()) || draft.owner != identity.pubKey) return
        publicationFailureMessage?.let(AppMessages::dismiss)
        publicationFailureMessage = null
        replyBusy.value = true
        replyJob = viewModelScope.launch {
            try {
                replySaveJob?.cancelAndJoin()
                withContext(Dispatchers.IO) { replyStore.save(draft) }
                repo.reply(draft, identity, signerRequests, replyStore) { mining ->
                    if (signer.value === identity) replyMining.value = mining
                }
                withContext(Dispatchers.IO) { replyStore.complete(draft) }
                if (replyDraft.value?.target?.id == draft.target?.id && replyDraft.value?.owner == draft.owner) replyDraft.value = null
            } catch (e: TimeoutCancellationException) {
                showPublicationFailure(draft, "Publication not confirmed. Your draft is saved; send again to retry.", e)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showPublicationFailure(draft, "Publish: ${e.message ?: e.javaClass.simpleName}", e) }
            finally { replyMining.value = false; replyBusy.value = false }
        }
    }

    private val deletingNotes = mutableSetOf<String>()

    fun deleteNote(note: Event) {
        val connected = signer.value ?: return
        if (note.pubKey != connected.pubKey || note.kind !in listOf(1, 20, 1111) ||
            deletingNotes.size >= 8 || !deletingNotes.add(note.id)) return
        viewModelScope.launch {
            try { repo.deleteNote(note, connected, signerRequests) }
            catch (e: TimeoutCancellationException) { AppMessages.show("Delete: confirmation timed out", e.stackTraceToString()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { AppMessages.show("Delete: ${e.message}", e.stackTraceToString()) }
            finally { deletingNotes.remove(note.id) }
        }
    }

    fun repost(note: Event) {
        if (note.id in reposting.value || repostJobs.size >= MAX_QUEUED_REPOSTS) return
        val connected = signer.value ?: run { message.value = "Connect an Android signer first"; return }
        val remove = note.id in state.value.sentReposts
        reposting.update { it + (note.id to !remove) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { if (remove) repo.removeRepost(note, connected, signerRequests) else repo.repost(note, connected, signerRequests) }
            catch (_: TimeoutCancellationException) { /* Revert the optimistic icon quietly. */ }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the signed event for a later retry; revert the icon. */ }
            finally { if (repostJobs[note.id] === currentCoroutineContext().job) {
                repostJobs.remove(note.id); reposting.update { it - note.id }
            } }
        }
        repostJobs[note.id] = job
        job.start()
    }

    internal fun vote(note: Event, choices: Set<String>) {
        val connected = signer.value ?: return
        if (note.id in state.value.pendingVotes || state.value.pendingVotes.size >= 8) return
        repo.voting(note.id, true)
        viewModelScope.launch {
            try { repo.vote(note, choices, connected, signerRequests) }
            catch (e: TimeoutCancellationException) { message.value = "Vote: confirmation timed out" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Vote: ${e.message ?: e.javaClass.simpleName}" }
            finally { repo.voting(note.id, false) }
        }
    }

    fun react(note: Event, choice: ReactionChoice, remove: Boolean = false) {
        val connected = signer.value ?: run { message.value = "Connect an Android signer first."; return }
        val queued = optimisticReactions.value[note.id].orEmpty()
        val visible = queued.fold(state.value.sentReactions[note.id].orEmpty()) { reactions, change -> change.applyTo(reactions) }
        if ((!remove && choice in visible) || (remove && choice !in visible)) return
        // As with publication failure, leave the icon unchanged and fail quietly if full.
        if (optimisticReactions.value.values.sumOf { it.size } >= MAX_QUEUED_REACTIONS) return
        optimisticReactions.update { it + (note.id to (queued + PendingReaction(choice, remove))) }
        reacting.update { it + note.id }
        if (reactionJobs[note.id] != null) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (signer.value === connected) {
                    val change = optimisticReactions.value[note.id]?.firstOrNull() ?: break
                    try {
                        if (change.remove) repo.removeReaction(note, change.choice, connected, signerRequests)
                        else repo.react(note, change.choice, connected, signerRequests)
                    } catch (_: TimeoutCancellationException) { /* Reactions fail quietly. */ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Roll back this action, then process the next selection. */ }
                    currentCoroutineContext().ensureActive()
                    optimisticReactions.update { pending ->
                        val remaining = pending[note.id].orEmpty().drop(1)
                        if (remaining.isEmpty()) pending - note.id else pending + (note.id to remaining)
                    }
                }
            } finally {
                if (reactionJobs[note.id] === currentCoroutineContext().job) {
                    reactionJobs.remove(note.id)
                    optimisticReactions.update { it - note.id }
                    reacting.update { it - note.id }
                }
            }
        }
        reactionJobs[note.id] = job
        job.start()
    }
    fun signerResponse(data: Intent) {
        val connected = signer.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { connected.newResponse(data) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { withContext(Dispatchers.Main) { cancelSignerRequest("Could not read the signer response. Please try again.") } }
        }
    }
}
