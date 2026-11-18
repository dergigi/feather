package org.dergigi.feather

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** AlertDialog's intrinsic sizing cannot measure lazy lists, including nested zap splits.
 * Keep the standard dialog window and styling, but measure its body with real constraints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListContentDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(Modifier.fillMaxWidth(), shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.padding(24.dp)) {
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                if (confirmButton != null) {
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.align(Alignment.End)) { confirmButton() }
                }
            }
        }
    }
}
