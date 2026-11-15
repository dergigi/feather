package org.dergigi.feather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ProfileMutedIndicator(vm: FeatherViewModel, target: String, onReveal: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val removing by vm.pendingAccountUnmutes.collectAsStateWithLifecycle()
    val adding by vm.pendingAccountMutes.collectAsStateWithLifecycle()
    if (state.publicKey == null || target == state.publicKey ||
        (target !in state.mutedAuthors && target !in adding) || target in removing) return
    val name = state.profiles[target]?.name ?: FeatherRepository.shortNpub(target)
    IconButton(onClick = onReveal) {
        Icon(painterResource(R.drawable.account_hidden), stringResource(R.string.ui_muted_account_actions, name),
            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = .8f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MutedAccountsSheet(vm: FeatherViewModel, onDismiss: () -> Unit, onProfile: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val removing by vm.pendingAccountUnmutes.collectAsStateWithLifecycle()
    val refreshing by vm.repo.accountDataLoading.collectAsStateWithLifecycle()
    val error by vm.repo.accountDataError.collectAsStateWithLifecycle()
    val decrypting by vm.decryptingMutes.collectAsStateWithLifecycle()
    val keys = remember(state.mutedAuthors, removing) { (state.mutedAuthors - removing).sorted() }
    val list = rememberLazyListState()
    var visible by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(state.publicKey) { vm.repo.refreshAccountData(force = true); vm.retryMutes() }
    LaunchedEffect(keys, list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.ifEmpty { keys.take(8) }.take(24) }
            .distinctUntilChanged().collect { visible = it }
    }
    val profiles by remember(vm, visible) { vm.repo.accountProfiles(visible) }.collectAsStateWithLifecycle(emptyMap())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CopyableMessageHost()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            Icon(painterResource(R.drawable.account_hidden), stringResource(R.string.ui_muted_accounts),
                Modifier.padding(start = 24.dp, bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
            if (keys.isNotEmpty()) LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(keys, key = { it }) { key ->
                    val profile = profiles[key] ?: state.profiles[key]
                    val name = profile?.name ?: FeatherRepository.shortNpub(key)
                    Row(Modifier.fillMaxWidth().clickable { onProfile(key) }.padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(name, 40, profile?.picture)
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(profile?.nip05?.takeIf { it.isNotBlank() } ?: FeatherRepository.shortNpub(key),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HoldToUnmuteButton(key, name) { vm.unmuteAccount(key) }
                    }
                }
            }
            val loading = refreshing == state.publicKey || decrypting
            val failed = state.pendingMuteList != null || error?.first == state.publicKey
            if (loading || failed || keys.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (failed) IconButton(onClick = { vm.repo.refreshAccountData(force = true); vm.retryMutes() }) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.ui_retry))
                } else Icon(painterResource(R.drawable.account_hidden), stringResource(R.string.ui_no_muted_accounts),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
            }
        }
    }
}
