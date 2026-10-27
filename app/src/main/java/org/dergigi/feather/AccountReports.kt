package org.dergigi.feather

import android.content.Context
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal enum class AccountReportReason(val tag: String, val label: Int, val icon: Int) {
    SPAM("spam", R.string.ui_report_spam, R.drawable.spam),
    AI_SLOP("other", R.string.ui_report_ai_slop, R.drawable.report_robot),
    IMPERSONATION("impersonation", R.string.ui_report_impersonation, R.drawable.report_mask),
    NUDITY("nudity", R.string.ui_report_nudity, R.drawable.sensitive_content),
    PROFANITY("profanity", R.string.ui_report_profanity, R.drawable.muted_words),
    MALWARE("malware", R.string.ui_report_malware, R.drawable.bug_report),
    ILLEGAL("illegal", R.string.ui_report_illegal, R.drawable.report_gavel),
    OTHER("other", R.string.ui_report_other, R.drawable.report_flag),
}
internal data class AccountReportDraft(val owner: String, val target: String,
    val reason: AccountReportReason? = null, val text: String = "", val muteAfterReporting: Boolean = true)

/** Public NIP-56 account reports. Opening/editing never requests a signature. */
internal class AccountReports(private val context: Context, private val scope: CoroutineScope,
    private val repo: FeatherRepository, private val signer: () -> NostrSignerExternal?,
    private val requests: SignerRequests, private val muteAccount: (String) -> Unit) {
    val draft = MutableStateFlow<AccountReportDraft?>(null)
    val visible = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    private var job: Job? = null
    private var generation = 0
    private fun prefs() = context.getSharedPreferences("account-reports", Context.MODE_PRIVATE)

    fun open(target: String) {
        val owner = signer()?.pubKey ?: return
        if (target == owner || Protocol.publicKey(target) != target) return
        if (busy.value) { visible.value = true; return }
        if (draft.value?.let { it.owner == owner && it.target == target } != true)
            draft.value = AccountReportDraft(owner, target)
        visible.value = true
    }
    fun edit(reason: AccountReportReason?, text: String) {
        if (!busy.value) draft.value = draft.value?.copy(reason = reason, text = text.take(1000))
    }
    fun setMuteAfterReporting(enabled: Boolean) {
        if (!busy.value) draft.value = draft.value?.copy(muteAfterReporting = enabled)
    }
    fun close() { visible.value = false }
    fun accountChanged() {
        generation++
        job?.cancel(); job = null
        visible.value = false; draft.value = null; busy.value = false
    }
    fun send() {
        val identity = signer() ?: return
        val current = draft.value ?: return
        val reason = current.reason ?: return
        if (busy.value || current.owner != identity.pubKey) return
        val run = generation
        busy.value = true
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fun checkAccount() = check(signer() === identity && generation == run) { "Account changed" }
                    checkAccount()
                    val tags = arrayOf(arrayOf("p", current.target, reason.tag))
                    val explanation = current.text.trimEnd()
                    // Keep the wire type interoperable while retaining the specific reason publicly.
                    val content = if (reason == AccountReportReason.AI_SLOP)
                        "AI slop" + if (explanation.isBlank()) "" else "\n\n$explanation"
                    else explanation
                    // Persist the signed event before sending. Retry the same report if an OK is lost.
                    val reasonKey = if (reason == AccountReportReason.AI_SLOP) "ai-slop" else reason.tag
                    val key = "${current.owner}:${current.target}:$reasonKey"
                    val saved = prefs().getString(key, null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                    val previous = saved?.optString("event")?.let { raw -> runCatching { Event.fromJson(raw) }.getOrNull() }
                        ?.takeIf { it.kind == 1984 && it.pubKey == current.owner && it.content == content &&
                            it.tags.contentDeepEquals(tags) && it.verify() }
                    if (previous != null && saved?.optBoolean("confirmed") == true) return@withContext
                    val event = previous ?: requests.run {
                        checkAccount()
                        val now = System.currentTimeMillis() / 1000
                        withTimeout(180_000) { identity.sign<Event>(now, 1984, tags, content) }.also {
                            require(it.kind == 1984 && it.pubKey == current.owner && it.createdAt == now &&
                                it.content == content && it.tags.contentDeepEquals(tags) && it.verify()) { "Invalid signed report" }
                        }
                    }
                    checkAccount()
                    val record = JSONObject().put("event", event.toJson()).put("confirmed", false)
                        .put("saved_at", System.currentTimeMillis())
                    val store = prefs()
                    val edit = store.edit().putString(key, record.toString())
                    store.all.entries.filter { it.key != key }.sortedByDescending {
                        runCatching { JSONObject(it.value as String).optLong("saved_at") }.getOrDefault(0L)
                    }.drop(31).forEach { edit.remove(it.key) }
                    check(edit.commit()) { "Could not save report for retry" }
                    checkAccount()
                    repo.publishAccountReport(event)
                    // Confirmation has already succeeded; cache bookkeeping must not report failure.
                    runCatching { store.edit().putString(key, record.put("confirmed", true).toString()).commit() }
                }
                if (generation == run && signer() === identity) {
                    visible.value = false; draft.value = null
                    if (current.muteAfterReporting) muteAccount(current.target)
                }
            } catch (e: TimeoutCancellationException) {
                if (generation == run) {
                    visible.value = true
                    AppMessages.show("Report: request timed out. Send again to retry.", e.stackTraceToString())
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == run) {
                    visible.value = true
                    AppMessages.show("Report: ${e.message}", e.stackTraceToString())
                }
            } finally { if (generation == run) { busy.value = false; job = null } }
        }
    }
}
