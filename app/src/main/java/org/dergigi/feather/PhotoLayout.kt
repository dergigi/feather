package org.dergigi.feather

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PhotoLayout(val storedValue: String) { STACKED("stacked"), CAROUSEL("carousel") }
internal val LocalPhotoLayout = staticCompositionLocalOf { PhotoLayout.STACKED }

/** Device-wide display choice, like timestamps; disk work never blocks rendering or taps. */
internal class PhotoLayoutPreference(context: Context) {
    private val current = MutableStateFlow(PhotoLayout.STACKED)
    val layout = current.asStateFlow()
    private val writes = Channel<PhotoLayout>(Channel.CONFLATED)
    private var changed = false

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = context.applicationContext.getSharedPreferences("photo-layout", Context.MODE_PRIVATE)
            val saved = preferences.getString("layout", null)
            synchronized(this@PhotoLayoutPreference) {
                if (!changed) current.value = PhotoLayout.entries.firstOrNull { it.storedValue == saved } ?: PhotoLayout.STACKED
            }
            for (value in writes) preferences.edit().putString("layout", value.storedValue).commit()
        }
    }

    @Synchronized
    fun select(layout: PhotoLayout) {
        changed = true
        current.value = layout
        writes.trySend(layout)
    }
}

@Composable
internal fun PhotoLayoutSetting() {
    val preference = (LocalContext.current.applicationContext as FeatherApplication).photoLayout
    val selected by preference.layout.collectAsStateWithLifecycle()
    val title = stringResource(R.string.ui_photo_carousel)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.photo_carousel), null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = selected == PhotoLayout.CAROUSEL,
            onCheckedChange = { enabled -> preference.select(if (enabled) PhotoLayout.CAROUSEL else PhotoLayout.STACKED) },
            modifier = Modifier.semantics { contentDescription = title })
    }
}
