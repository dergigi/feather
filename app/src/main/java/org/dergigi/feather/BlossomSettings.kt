package org.dergigi.feather

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlossomSettings(vm: FeatherViewModel, owner: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember(owner) { mutableStateOf<BlossomLists.Snapshot?>(null) }
    var urls by remember(owner) { mutableStateOf(emptyList<String>()) }
    var busy by remember(owner) { mutableStateOf(true) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var confirmed by remember(owner) { mutableStateOf(false) }
    var confirmPublish by remember(owner) { mutableStateOf(false) }
    var refresh by remember(owner) { mutableIntStateOf(0) }
    var publishJob by remember(owner) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(owner, refresh) {
        busy = true; error = null; snapshot = null
        try {
            val result = vm.blossomLists.load(owner)
            snapshot = result; urls = result.urls
        } catch (e: TimeoutCancellationException) { error = "Reading account relays timed out. Retry when connected." }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not read your Blossom list." }
        finally { busy = false }
    }
    // Removing this account-scoped sheet cancels discovery and signing/publication work.
    DisposableEffect(owner) { onDispose { publishJob?.cancel() } }
    val validation = remember(urls) { runCatching { blossomListUrls(urls) } }
    if (confirmPublish) AlertDialog(
        onDismissRequest = { confirmPublish = false },
        title = { Text("Publish Blossom servers?") },
        text = { Text("Replace your public server list with these ${urls.size} servers in this order? Other clients can read this list. Your signer will ask for approval." +
            (snapshot?.warning?.let { "\n\n$it\n\nConfirm publication despite this uncertainty?" } ?: "")) },
        confirmButton = { IconButton(onClick = {
            confirmPublish = false
            val base = snapshot ?: return@IconButton
            val identity = vm.signer.value ?: return@IconButton
            busy = true; error = null; confirmed = false
            publishJob = scope.launch {
                try {
                    val result = vm.blossomLists.publish(base, urls, identity, allowIncomplete = base.incomplete)
                    snapshot = result; urls = result.urls; confirmed = true
                } catch (e: TimeoutCancellationException) {
                    error = "Signing or discovery timed out. Reopen to check the latest list before retrying."
                } catch (e: CancellationException) {
                    error = "Publication cancelled. Reopen to check delivery before trying again."
                    throw e
                } catch (e: Exception) { error = e.message ?: "Could not publish your Blossom list." }
                finally { busy = false }
            }
        }) { Icon(Icons.Default.Send, "Confirm public Blossom list publication") } },
        dismissButton = { IconButton(onClick = { confirmPublish = false }) { Icon(Icons.Default.Close, "Cancel publication") } }
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Blossom servers", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close Blossom settings") }
            }
            Text("Media uploads try the first server first. Use full HTTPS URLs; at least one server is required. Changes stay here until you publish.", style = MaterialTheme.typography.bodyMedium)
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            snapshot?.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (snapshot != null) {
                urls.forEachIndexed { index, url ->
                    OutlinedTextField(value = url, onValueChange = { value ->
                        if (value.length <= 2048) { urls = urls.toMutableList().also { it[index] = value }; confirmed = false }
                    }, enabled = !busy, singleLine = true, label = { Text("Server ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(enabled = !busy && index > 0, onClick = {
                            urls = urls.toMutableList().also { it.add(index - 1, it.removeAt(index)) }; confirmed = false
                        }) { Icon(Icons.Default.KeyboardArrowUp, "Move server ${index + 1} earlier") }
                        IconButton(enabled = !busy && index < urls.lastIndex, onClick = {
                            urls = urls.toMutableList().also { it.add(index + 1, it.removeAt(index)) }; confirmed = false
                        }) { Icon(Icons.Default.KeyboardArrowDown, "Move server ${index + 1} later") }
                        IconButton(enabled = !busy, onClick = { urls = urls.filterIndexed { i, _ -> i != index }; confirmed = false }) {
                            Icon(Icons.Default.Close, "Remove server ${index + 1}")
                        }
                    }
                }
                validation.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (confirmed) Text("Publication confirmed by a relay.", color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
                if (snapshot == null) IconButton(enabled = !busy, onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Retry loading Blossom servers") }
                IconButton(enabled = !busy && snapshot != null && urls.size < 16, onClick = { urls = urls + ""; confirmed = false }) {
                    Icon(Icons.Default.Add, "Add Blossom server")
                }
                IconButton(enabled = !busy && snapshot != null && validation.isSuccess && urls != snapshot?.urls,
                    onClick = { confirmPublish = true }) { Icon(Icons.Default.Send, "Publish Blossom server list") }
            }
        }
    }
}
