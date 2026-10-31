package org.dergigi.feather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

internal const val RECIPE_RELAY = "wss://pantry.zap.cooking"
private val recipeTags = listOf("zapcooking", "nostrcooking")

/** Zap.cooking's current and legacy markers, not arbitrary food-related articles. */
internal fun isRecipe(event: Event): Boolean = event.kind == 30023 &&
    !event.tagValue("d").isNullOrBlank() &&
    event.tags.any { it.firstOrNull() == "t" && it.getOrNull(1) in recipeTags }

internal fun recipeFilter(limit: Int, until: Long? = null, author: String? = null) =
    Filter(kinds = listOf(30023), authors = author?.let(::listOf), tags = mapOf("t" to recipeTags), limit = limit, until = until)

/** Structured values are optional; legacy recipes retain their metadata in the Markdown body. */
@Composable
internal fun RecipeDetails(event: Event, modifier: Modifier = Modifier) {
    val values = remember(event.id) {
        listOf("prep_time", "cook_time", "servings").map { key ->
            event.tagValue(key)?.trim()?.takeIf { it.isNotBlank() }?.take(80)
        }
    }
    if (values.all { it == null }) return
    val labels = listOf(R.string.ui_recipe_prep, R.string.ui_recipe_cook, R.string.ui_recipe_servings)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { index, value ->
            if (value != null) Text(stringResource(labels[index], value), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
