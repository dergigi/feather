package org.dergigi.feather

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoLayoutSetting() {
    val preference = (LocalContext.current.applicationContext as FeatherApplication).photoLayout
    val selected by preference.layout.collectAsStateWithLifecycle()
    val title = stringResource(R.string.ui_photo_layout)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(title) } }, state = rememberTooltipState()) {
            Icon(painterResource(R.drawable.photo_collection), title,
                Modifier.padding(vertical = 12.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp).selectableGroup()) {
            listOf(PhotoLayout.CAROUSEL, PhotoLayout.STACKED).forEach { layout ->
                val label = stringResource(if (layout == PhotoLayout.CAROUSEL) R.string.ui_photo_carousel else R.string.ui_photo_stacked)
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .background(if (selected == layout) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .selectable(selected = selected == layout, role = Role.RadioButton, onClick = { preference.select(layout) }),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (layout == PhotoLayout.CAROUSEL) R.drawable.photo_carousel else R.drawable.photo_stacked),
                            label, tint = if (selected == layout) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
