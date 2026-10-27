package org.dergigi.feather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountReportSheet(vm: FeatherViewModel) {
    val reports = vm.accountReports
    val visible by reports.visible.collectAsStateWithLifecycle()
    val draft by reports.draft.collectAsStateWithLifecycle()
    val busy by reports.busy.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val current = draft ?: return
    if (!visible || current.owner != state.publicKey) return
    var expanded by remember(current.owner, current.target) { mutableStateOf(false) }
    val profiles by remember(vm, current.owner, current.target) {
        vm.repo.accountProfiles(listOf(current.owner, current.target))
    }.collectAsStateWithLifecycle(emptyMap())
    val profile = profiles[current.target] ?: state.profiles[current.target]
    val name = profile?.name ?: FeatherRepository.shortKey(current.target)
    ModalBottomSheet(onDismissRequest = reports::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CopyableMessageHost()
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = reports::close) { Icon(Icons.Default.Close, stringResource(R.string.ui_close_report)) }
                Spacer(Modifier.weight(1f))
                val author = profiles[current.owner] ?: state.profiles[current.owner]
                AccountAvatar(author?.name ?: FeatherRepository.shortKey(current.owner), 36, author?.picture)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountAvatar(name, 36, profile?.picture)
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box {
                        TextButton(enabled = !busy, onClick = { expanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = .8f))) {
                            Icon(painterResource(current.reason?.icon ?: R.drawable.report_flag), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(current.reason?.label ?: R.string.ui_report_reason),
                                Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (busy) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AccountReportReason.entries.forEach { reason ->
                                DropdownMenuItem(text = { Text(stringResource(reason.label)) },
                                    leadingIcon = { Icon(painterResource(reason.icon), null, Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = .8f)) }, onClick = {
                                        reports.edit(reason, current.text); expanded = false
                                    })
                            }
                        }
                    }
                }
            }
            val explanation = stringResource(R.string.ui_report_explanation)
            TextField(value = current.text, onValueChange = { reports.edit(current.reason, it) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = explanation },
                placeholder = { Text(explanation) }, minLines = 2, maxLines = 5, shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = current.muteAfterReporting, enabled = !busy, role = Role.Checkbox,
                            onValueChange = reports::setMuteAfterReporting),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = current.muteAfterReporting, onCheckedChange = null, enabled = !busy,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error))
                        Text(stringResource(R.string.ui_mute_after_reporting), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(stringResource(R.string.ui_report_public),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onClick = reports::send, enabled = !busy && current.reason != null,
                    modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ui_send_report))
                }
            }
        }
    }
}
