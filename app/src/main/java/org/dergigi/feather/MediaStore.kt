@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.dergigi.feather

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import android.net.Uri
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

class MediaStore(private val context: Context) {
    class Resources(val images: ImageLoader, val imageDisk: DiskCache, val videos: SimpleCache, val videoSource: CacheDataSource.Factory, val thumbnails: AvatarThumbnails, val prefetchImages: ImageLoader, val prefetchVideoSource: CacheDataSource.Factory)
    @Volatile internal var audioActive = false
    private val prefs = context.getSharedPreferences("feather-media", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _resources = MutableStateFlow<Resources?>(null)
    val resources = _resources.asStateFlow()
    private val _budgetMb = MutableStateFlow(prefs.getInt("cacheMb", 1024).takeIf { it in MediaContent.cacheOptionsMb } ?: 1024)
    val budgetMb = _budgetMb.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val blossomTransport = BlossomTransport(context)
    val http = OkHttpClient.Builder()
        .addInterceptor(blossomTransport)
        .dispatcher(Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 2 })
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false).build()

    // Separate scheduling prevents speculative downloads from occupying visible-image slots.
    private val prefetchHttp = http.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 2; maxRequestsPerHost = 1 }).build()

    private val videoPrefetchHttp = http.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 2; maxRequestsPerHost = 1 })
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    init { scope.launch { rebuild(_budgetMb.value) } }

    /** Cache-maintained counters include images, thumbnails, preview metadata, and video bytes. */
    suspend fun usedBytes(): Long? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _resources.value ?: return@withLock null
            current.imageDisk.size + current.videos.cacheSpace
        }
    }

    suspend fun setBudget(mb: Int) {
        require(mb in MediaContent.cacheOptionsMb)
        if (mb != _budgetMb.value || _resources.value == null) rebuild(mb)
    }
    private suspend fun rebuild(mb: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _error.value = null
            withContext(Dispatchers.Main.immediate) { VideoPlaybackService.stopPlayback(); AudioPlaybackService.beforeCacheChange() }
            val previous = _resources.value
            _resources.value = null
            http.dispatcher.cancelAll()
            prefetchHttp.dispatcher.cancelAll()
            videoPrefetchHttp.dispatcher.cancelAll()
            previous?.images?.shutdown()
            previous?.prefetchImages?.shutdown()
            previous?.thumbnails?.close()
            previous?.imageDisk?.shutdown()
            previous?.videos?.release()
            var disk: DiskCache? = null
            var videos: SimpleCache? = null
            var thumbnails: AvatarThumbnails? = null
            try {
                disk = DiskCache.Builder().directory(File(context.cacheDir, "images").toOkioPath()).maxSizeBytes(MediaContent.imageBudget(mb)).build()
                thumbnails = AvatarThumbnails(disk)
                val memory = MemoryCache.Builder().maxSizeBytes(minOf(Runtime.getRuntime().maxMemory() * 6 / 100, 64L * 1024 * 1024)).build()
                val images = ImageLoader.Builder(context)
                    .memoryCache(memory)
                    .diskCache(disk)
                    .components { add(BlossomImageInterceptor()); add(thumbnails); add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                    .build()
                val prefetchImages = ImageLoader.Builder(context)
                    .memoryCache(memory).diskCache(disk)
                    .components { add(BlossomImageInterceptor()); add(thumbnails); add(OkHttpNetworkFetcherFactory(callFactory = { prefetchHttp })) }
                    .build()
                videos = SimpleCache(File(context.cacheDir, "videos"), LeastRecentlyUsedCacheEvictor(MediaContent.videoBudget(mb)), StandaloneDatabaseProvider(context))
                // Initializes and trims the cache on IO before any player is created.
                videos.checkInitialization()
                val source = CacheDataSource.Factory().setCache(videos)
                    .setUpstreamDataSourceFactory(blossomSource(http))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                val prefetchSource = CacheDataSource.Factory().setCache(videos)
                    .setUpstreamDataSourceFactory(blossomSource(videoPrefetchHttp))
                    .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
                _budgetMb.value = mb
                prefs.edit().putInt("cacheMb", mb).apply()
                _resources.value = Resources(images, disk, videos, source, thumbnails, prefetchImages, prefetchSource)
            } catch (e: Exception) {
                thumbnails?.close(); disk?.shutdown(); videos?.release()
                _error.value = "Could not open the media cache. Try another storage size."
            }
        }
    }
    private fun blossomSource(client: OkHttpClient) = ResolvingDataSource.Factory(OkHttpDataSource.Factory(client)) { spec ->
        spec.withUri(Uri.parse(BlossomUri.transport(spec.uri.toString())))
    }
    fun background() { if (!audioActive && !VideoPlaybackService.active) http.dispatcher.cancelAll(); prefetchHttp.dispatcher.cancelAll(); videoPrefetchHttp.dispatcher.cancelAll() }
}
