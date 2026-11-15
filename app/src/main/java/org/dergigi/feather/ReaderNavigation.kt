package org.dergigi.feather

import android.os.Bundle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import com.vitorpamplona.quartz.nip01Core.core.Event
import java.util.UUID

internal enum class ReaderShortcut { NEXT_TAB, PREVIOUS_TAB, SEARCH }
internal class FeedTabRequest(val tab: ProfileTab)

/** Each visit owns its UI state; opening another reader never overwrites it. */
internal class ReaderEntry(
    val screen: String,
    val target: String? = null,
    val key: String = UUID.randomUUID().toString(),
    index: Int = 0,
    offset: Int = 0,
    tab: ProfileTab = ProfileTab.POSTS,
    toolbar: Boolean = true,
) {
    val list = LazyListState(index, offset)
    val hashtagLists = mutableMapOf<ProfileTab, LazyListState>()
    fun hashtagList(category: ProfileTab): LazyListState =
        if (category == ProfileTab.POSTS) list else hashtagLists.getOrPut(category) { LazyListState() }
    val feedPages = mutableMapOf<ProfileTab, ReaderEntry>()
    fun feedPage(category: ProfileTab): ReaderEntry = feedPages.getOrPut(category) { ReaderEntry("feed", tab = category) }
    var issueFilter by mutableIntStateOf(0)
    var issueLimit by mutableIntStateOf(100)
    var repositoryIssues: RepositoryIssueSnapshot? = null
    var searchQuery by mutableStateOf("")
    // Explicit, transient keyboard requests; never replay them after restoring a screen.
    var requestedFeedTab by mutableStateOf<FeedTabRequest?>(null)
    var requestSearchFocus by mutableStateOf(false)
    var tab by mutableStateOf(tab)
    var toolbarFraction by mutableStateOf(if (toolbar) 1f else 0f)
    var toolbar: Boolean
        get() = toolbarFraction > 0f
        set(value) { toolbarFraction = if (value) 1f else 0f }
    var toolbarTravelPx by mutableStateOf(0)
    fun scrollToolbar(delta: Float, height: Int = toolbarTravelPx): Float {
        if (height <= 0) return 0f
        val before = toolbarFraction * height
        val after = (before + delta).coerceIn(0f, height.toFloat())
        toolbarFraction = after / height
        return after - before
    }
    var reference: NostrContent.Reference? = null
    // One image hint for this visit, carried from the gallery's open-note action.
    var galleryPhoto by mutableStateOf<String?>(null)
    var hashtagAuthor: String? = null
    // Event objects are shared with the repository, never serialized into saved instance state.
    val notes = mutableMapOf<ProfileTab, List<Event>>()
    var topics: List<String> = emptyList()
    var homeOrder: List<String> = emptyList()
    var homeRows: List<ConversationRow> = emptyList()
    var homeRefresh by mutableIntStateOf(0)
    var refreshRequested by mutableIntStateOf(0)
    var notificationSearch by mutableStateOf("")
    var notificationSearchOwner: String? = null
    var notifications: NotificationSnapshot? = null
    var conversation: Conversation? = null
    val threadViewport = ThreadViewport()
    var threadPositioned = index != 0 || offset != 0
    var threadRestoreAnchor: Pair<String, Int>? = null
    var expandedThreadParents: List<String> = emptyList()
    var threadAncestorLimit = INITIAL_THREAD_ANCESTORS
    var tabs: List<ProfileTab> = emptyList()
    var bookmarks: List<Pair<Event, NostrContent.Reference>> = emptyList()
    var people: List<Pair<Event, NostrContent.Reference>> = emptyList()
    var photos = ProfilePhotos()
}

internal class ReaderNavigation(entries: List<ReaderEntry> = listOf(ReaderEntry("feed"))) {
    val entries = mutableStateListOf<ReaderEntry>().apply { addAll(entries) }
    val current get() = entries.last()
    fun push(entry: ReaderEntry) {
        if (entry.screen == current.screen && entry.target == current.target && entry.hashtagAuthor == current.hashtagAuthor) return
        entries.add(entry)
        // Bound retained event references; older destinations still restore from the repository.
        entries.dropLast(8).forEach { it.repositoryIssues = null; it.notes.clear(); it.topics = emptyList(); it.conversation = null; it.threadViewport.clear(); it.bookmarks = emptyList(); it.people = emptyList(); it.photos = ProfilePhotos() }
    }
    fun back() { if (entries.size > 1) entries.removeAt(entries.lastIndex) }
    fun home() { entries.clear(); entries.add(ReaderEntry("feed")) }

    companion object {
        val saver = Saver<ReaderNavigation, ArrayList<Bundle>>(
            save = { navigation -> ArrayList(navigation.entries.map { entry -> Bundle().apply {
                putInt("issueFilter", entry.issueFilter); putInt("issueLimit", entry.issueLimit)
                putString("screen", entry.screen); putString("target", entry.target); putString("key", entry.key)
                putInt("index", entry.list.firstVisibleItemIndex); putInt("offset", entry.list.firstVisibleItemScrollOffset)
                putString("tab", entry.tab.name); putBoolean("toolbar", entry.toolbar); putFloat("toolbarFraction", entry.toolbarFraction)
                putBoolean("threadPositioned", entry.threadPositioned)
                putStringArrayList("threadExpanded", ArrayList(entry.expandedThreadParents))
                putInt("threadAncestorLimit", entry.threadAncestorLimit)
                if (entry.screen == "thread") {
                    putString("galleryPhoto", entry.galleryPhoto)
                    val anchor = entry.list.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
                    putString("threadAnchor", anchor ?: entry.threadRestoreAnchor?.first)
                    putInt("threadAnchorOffset", entry.threadRestoreAnchor?.second ?: entry.list.firstVisibleItemScrollOffset)
                }
                putStringArrayList("feedTabs", ArrayList(entry.tabs.map { it.name }))
                entry.feedPages.forEach { (category, page) -> putBundle("feed:${category.name}", Bundle().apply {
                    putString("key", page.key); putInt("index", page.list.firstVisibleItemIndex)
                    putInt("offset", page.list.firstVisibleItemScrollOffset); putBoolean("toolbar", page.toolbar); putFloat("toolbarFraction", page.toolbarFraction)
                }) }
                putStringArrayList("relays", ArrayList(entry.reference?.relays.orEmpty()))
                putString("author", entry.reference?.author)
                putString("hashtagAuthor", entry.hashtagAuthor)
                entry.hashtagLists.forEach { (category, list) -> putBundle("hashtag-list:${category.name}", Bundle().apply {
                    putInt("index", list.firstVisibleItemIndex); putInt("offset", list.firstVisibleItemScrollOffset)
                }) }
                entry.reference?.kind?.let { putInt("kind", it) }
            } }) },
            restore = { saved -> ReaderNavigation(saved.map { bundle ->
                ReaderEntry(bundle.getString("screen")!!, bundle.getString("target"), bundle.getString("key")!!,
                    bundle.getInt("index"), bundle.getInt("offset"), ProfileTab.valueOf(bundle.getString("tab")!!),
                    bundle.getBoolean("toolbar")).also { entry ->
                    entry.toolbarFraction = bundle.getFloat("toolbarFraction", entry.toolbarFraction).coerceIn(0f, 1f)
                    entry.issueFilter = bundle.getInt("issueFilter", 0).coerceIn(0, 2)
                    entry.issueLimit = bundle.getInt("issueLimit", 100).coerceIn(100, 600)
                    if (entry.screen == "repository") entry.reference = NostrContent.Reference(entry.target.orEmpty(), kind = 30617, relays = bundle.getStringArrayList("relays").orEmpty())
                    entry.hashtagAuthor = bundle.getString("hashtagAuthor")
                    if (entry.screen == "hashtag") hashtagTabs.forEach { category ->
                        bundle.getBundle("hashtag-list:${category.name}")?.let { saved ->
                            entry.hashtagLists[category] = LazyListState(saved.getInt("index"), saved.getInt("offset"))
                        }
                    }
                    entry.threadPositioned = bundle.getBoolean("threadPositioned")
                    entry.galleryPhoto = bundle.getString("galleryPhoto")
                    entry.expandedThreadParents = bundle.getStringArrayList("threadExpanded").orEmpty().takeLast(64)
                    entry.threadAncestorLimit = bundle.getInt("threadAncestorLimit", INITIAL_THREAD_ANCESTORS)
                        .coerceIn(INITIAL_THREAD_ANCESTORS, MAX_THREAD_ANCESTORS)
                    entry.threadRestoreAnchor = bundle.getString("threadAnchor")?.let { it to bundle.getInt("threadAnchorOffset") }
                    entry.tabs = bundle.getStringArrayList("feedTabs").orEmpty().mapNotNull { name -> ProfileTab.entries.find { it.name == name } }
                    if (entry.screen == "feed") ProfileTab.entries.forEach { category ->
                        bundle.getBundle("feed:${category.name}")?.let { page ->
                            entry.feedPages[category] = ReaderEntry("feed", key = page.getString("key") ?: UUID.randomUUID().toString(),
                                index = page.getInt("index"), offset = page.getInt("offset"), tab = category, toolbar = page.getBoolean("toolbar")).also {
                                    it.toolbarFraction = page.getFloat("toolbarFraction", it.toolbarFraction).coerceIn(0f, 1f)
                                }
                        }
                    }
                    if (entry.screen == "profile" || entry.screen == "thread") entry.reference =
                        NostrContent.Reference(entry.target!!, profile = entry.screen == "profile", relays = bundle.getStringArrayList("relays").orEmpty(),
                            author = bundle.getString("author"), kind = if (bundle.containsKey("kind")) bundle.getInt("kind") else null)
                }
            }) },
        )
    }
}
