package org.dergigi.feather

import android.content.Context
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal const val DEFAULT_ZAP_SATS = 21L
internal const val MAX_ZAP_SATS = 2_100_000_000_000_000L
internal val defaultZapAmounts = listOf(21L, 42L, 69L, 210L, 420L, 2_100L, 9_001L, 69_420L)
internal data class WalletDetails(val name: String? = null, val balanceSats: Long? = null)
internal fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

/** Decode the BOLT11 hash tags after checksum/amount validation. Wallet verifies the invoice signature. */
internal fun invoiceHash(invoice: String, field: Char): String? {
    val alphabet = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    require(invoice == invoice.lowercase() || invoice == invoice.uppercase()) { "Invalid invoice" }
    val text = invoice.lowercase()
    val split = text.lastIndexOf('1')
    require(split > 0 && text.startsWith("lnbc")) { "Expected a Bitcoin Lightning invoice" }
    val words = text.substring(split + 1).map { alphabet.indexOf(it).also { n -> require(n >= 0) } }
    val hrp = text.substring(0, split)
    var checksum = 1
    val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    for (value in hrp.map { it.code shr 5 } + listOf(0) + hrp.map { it.code and 31 } + words) {
        val top = checksum ushr 25
        checksum = ((checksum and 0x1ffffff) shl 5) xor value
        for (i in 0..4) if (((top ushr i) and 1) != 0) checksum = checksum xor generators[i]
    }
    require(checksum == 1 && words.size >= 117) { "Invalid invoice checksum" }
    var position = 7
    val end = words.size - 6 - 104
    var result: String? = null
    while (position < end) {
        require(position + 3 <= end) { "Invalid invoice tags" }
        val tag = alphabet[words[position]]
        val length = words[position + 1] * 32 + words[position + 2]
        position += 3
        require(position + length <= end) { "Invalid invoice tags" }
        if (tag == field) {
            require(length == 52 && result == null) { "Invalid invoice hash" }
            var bits = 0; var buffer = 0
            val bytes = ArrayList<Byte>()
            for (word in words.subList(position, position + length)) {
                buffer = (buffer shl 5) or word; bits += 5
                if (bits >= 8) { bits -= 8; bytes.add((buffer ushr bits).toByte()) }
            }
            require(bits == 4 && (buffer and 15) == 0 && bytes.size == 32)
            result = bytes.joinToString("") { "%02x".format(it) }
        }
        position += length
    }
    return result
}

internal class ZapWallet(context: Context) {
    val store = WalletStore(context)
    suspend fun details(owner: String, onDetails: (WalletDetails) -> Unit) = withContext(Dispatchers.IO) {
        val connection = store.connection(owner) ?: return@withContext
        try {
            val info = NwcWallet.request(connection, "get_info", JSONObject())
            val name = info.optString("alias").filterNot { it.isISOControl() }.trim().take(80).takeIf { it.isNotBlank() }
            val details = WalletDetails(name)
            onDetails(details)
            val methods = info.optJSONArray("methods")
            if (methods != null && (0 until methods.length()).any { methods.optString(it) == "get_balance" }) {
                val balance = NwcWallet.request(connection, "get_balance", JSONObject()).getLong("balance")
                require(balance >= 0)
                onDetails(details.copy(balanceSats = balance / 1000))
            }
        } finally { connection.secret.fill(0) }
    }
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private suspend fun get(url: String, onRedirect: (String) -> Unit = {}): JSONObject {
        var endpoint = url.toHttpUrl()
        val visited = mutableSetOf<String>()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        repeat(6) { hop ->
            currentCoroutineContext().ensureActive()
            require(MediaContent.secureUrl(endpoint.toString()) != null) { "A secure Lightning endpoint is required" }
            require(visited.add(endpoint.toString())) { "Lightning redirect loop at ${endpoint.host}" }
            val call = http.newCall(Request.Builder().url(endpoint).header("Accept", "application/json").build())
            call.timeout().deadlineNanoTime(deadline)
            val json = call.execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    require(hop < 5) { "Too many Lightning redirects from ${response.request.url.host}" }
                    val next = response.header("Location")?.let(response.request.url::resolve)
                    require(next != null) { "HTTP ${response.code} from ${response.request.url.host}: missing or invalid redirect location" }
                    require(MediaContent.secureUrl(next.toString()) != null) { "Unsafe Lightning redirect from ${response.request.url.host}: HTTPS required" }
                    onRedirect("HTTP ${response.code}: ${response.request.url.host} → ${next.host}")
                    endpoint = next
                    return@use null
                }
                // Read a bounded body on errors too: LNURL providers put the useful reason
                // in JSON even when returning HTTP 400/500.
                val source = response.body.source()
                source.request(131073)
                val bytes = source.buffer.clone().readByteArray(minOf(source.buffer.size, 131073L))
                val body = bytes.toString(Charsets.UTF_8)
                val json = if (bytes.size <= 131072) runCatching { JSONObject(body) }.getOrNull() else null
                val reason = listOf("reason", "message", "error").firstNotNullOfOrNull { key ->
                    json?.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.let(::walletErrorText)?.takeIf(String::isNotBlank)
                }
                require(response.isSuccessful) {
                    val detail = reason ?: if (response.header("Content-Type").orEmpty().contains("text/plain"))
                        walletErrorText(body).takeIf(String::isNotBlank) else null
                    "HTTP ${response.code} from ${response.request.url.host}${detail?.let { ": $it" }.orEmpty()}"
                }
                require(bytes.size <= 131072) { "Lightning response too large" }
                requireNotNull(json) { "Invalid JSON response from ${response.request.url.host}" }
                require(json.optString("status") != "ERROR") { "Lightning endpoint: ${reason ?: "request rejected"}" }
                json
            }
            if (json != null) return json
        }
        error("Too many Lightning redirects")
    }
    suspend fun connect(raw: String, owner: String) = withContext(Dispatchers.IO) {
        var step = "Reading wallet connection"
        var connection: WalletConnection? = null
        try {
            val parsed = WalletConnection.parse(raw)
            connection = parsed
            step = "Requesting wallet information (get_info)"
            val info = NwcWallet.request(parsed, "get_info", JSONObject())
            step = "Checking wallet payment permission"
            val methods = info.optJSONArray("methods")
            require(methods != null && (0 until methods.length()).any { methods.optString(it) == "pay_invoice" }) { "Wallet does not allow payments" }
            currentCoroutineContext().ensureActive()
            step = "Saving wallet connection"
            store.save(raw, owner)
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            currentCoroutineContext().ensureActive()
            fun sanitized(text: String): String = walletErrorText(
                if (raw.isNotEmpty()) text.replace(raw, "[connection redacted]") else text
            )
            val reason = if (failure is TimeoutCancellationException) "Wallet connection timed out"
                else sanitized(failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName)
            val report = buildString {
                appendLine("Feather ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}")
                appendLine("Time: ${java.time.Instant.now()}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("Operation: Connecting Nostr Wallet Connect")
                appendLine("Step: $step")
                connection?.relays?.forEach { appendLine("Wallet relay: ${walletErrorText(it)}") }
                (failure as? WalletFailure)?.code?.let { appendLine("Wallet error code: ${sanitized(it)}") }
                appendLine(reason)
                appendLine()
                val stack = failure.stackTraceToString().let {
                    if (raw.isNotEmpty()) it.replace(raw, "[connection redacted]") else it
                }
                stack.take(24000).lineSequence().forEach { appendLine(sanitized(it)) }
            }
            throw WalletFailure("NWC: $step: $reason", (failure as? WalletFailure)?.code, report)
        } finally { connection?.secret?.fill(0) }
    }
    /** True only when this call newly confirms payment; false for an already-paid journal entry. */
    suspend fun zap(note: Event, lightning: String?, relays: List<String>, signer: NostrSignerExternal,
        signerRequests: SignerRequests, amountSats: Long, comment: String = "",
        recipientProfiles: suspend () -> Map<String, Profile> = { emptyMap() }): Boolean = withContext(Dispatchers.IO) {
        val declared = note.tags.any { it.firstOrNull() == "zap" }
        val splits = zapSplits(note)
        if (!declared) return@withContext zapRecipient(note, lightning, relays, signer, signerRequests, amountSats, comment,
            resolveLightning = {
                val profile = recipientProfiles()[note.pubKey]
                    ?: error("Could not load this recipient's profile. No new payment sent; try again when the profile is available.")
                profile.lightning
            })
        require(splits.isNotEmpty()) { "Invalid zap split; no payment sent" }
        val amounts = allocateZap(splits, amountSats)
        val message = comment.trimEnd()
        require(message.codePointCount(0, message.length) <= 280) { "Zap message is too long" }
        val messageHash = sha256(message.toByteArray(Charsets.UTF_8))
        val connection = store.connection(signer.pubKey) ?: error("Connect a wallet in settings")
        val batchId = try { sha256("${connection.pubkey}:${signer.pubKey}:${note.id}".toByteArray()) }
            finally { connection.secret.fill(0) }
        var batch = store.payment(batchId)
        if (batch != null && !batch.optBoolean("split")) {
            if (batch.optString("status") != "paid") {
                zapRecipient(note, lightning, relays, signer, signerRequests, batch.getLong("sats"), comment)
                throw WalletFailure("Previous author-only zap checked. No split payment sent; submit again to send the split.")
            }
            store.savePayment(sha256(batch.getString("invoice").toByteArray()), batch)
            batch = null
        }
        if (batch?.optBoolean("split") == true) {
            val same = batch.optLong("sats") == amountSats && batch.optString("messageHash") == messageHash
            if (batch.optString("status") == "paid" && same) return@withContext false
            require(batch.optString("status") == "paid" || same) {
                "Finish the previous split zap with its original amount and message before sending a different zap"
            }
            if (batch.optString("status") == "paid") {
                store.savePayment(sha256(batch.getString("batch").toByteArray()), batch)
                batch = null
            }
        }
        // Resolve every endpoint before sending the first payment. An unavailable recipient
        // never silently redirects their share to the author or other recipients.
        val profiles = recipientProfiles()
        val addresses = splits.map { profiles[it.pubkey]?.lightning }
        val record = batch ?: JSONObject().put("split", true).put("batch", java.util.UUID.randomUUID().toString())
            .put("status", "split_pending").put("sats", amountSats).put("messageHash", messageHash)
            .put("time", System.currentTimeMillis()).put("author", signer.pubKey).put("note", note.id)
            .put("completed", org.json.JSONArray())
        val completed = record.getJSONArray("completed")
        val done = (0 until completed.length()).map { completed.getString(it) }.toMutableSet()
        splits.forEachIndexed { index, recipient ->
            val child = sha256("${record.getString("batch")}:${recipient.pubkey}".toByteArray())
            if (recipient.pubkey !in done && store.payment(child) == null) {
                val profile = profiles[recipient.pubkey]
                    ?: error("Could not load the profile for split recipient ${FeatherRepository.shortKey(recipient.pubkey)}; no new payment sent")
                val name = noticeProfileName(recipient.pubkey, profiles)
                require(!profile.lightning.isNullOrBlank()) { "$name's loaded profile has no Lightning address; no new payment sent" }
                require(addresses[index]?.let { LnurlForm.toUrl(it) } != null) { "$name's Lightning address is invalid; no new payment sent" }
            }
        }
        store.savePayment(batchId, record)
        try {
            for ((index, recipient) in splits.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (recipient.pubkey in done) continue
                val child = sha256("${record.getString("batch")}:${recipient.pubkey}".toByteArray())
                zapRecipient(note, addresses[index], (recipient.relays + relays).distinct(), signer, signerRequests,
                    amounts[index], message, recipient.pubkey, child, batchId)
                // Even a false result means this exact child is already confirmed in the journal.
                done.add(recipient.pubkey)
                completed.put(recipient.pubkey)
                store.savePayment(batchId, record)
            }
            store.savePayment(batchId, record.put("status", "paid"))
            // The durable batch tracks completion; settled child journals are now redundant.
            splits.forEach { runCatching { store.forgetPayment(sha256("${record.getString("batch")}:${it.pubkey}".toByteArray())) } }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val progress = "Split zap: ${done.size}/${splits.size} recipients confirmed"
            val report = (e as? WalletFailure)?.report ?: walletErrorText(e.stackTraceToString())
            throw WalletFailure("$progress. ${walletErrorReason(e)}", (e as? WalletFailure)?.code,
                "$progress\nTotal: $amountSats sats\n$report")
        }
    }

    private suspend fun zapRecipient(note: Event, lightning: String?, relays: List<String>, signer: NostrSignerExternal, signerRequests: SignerRequests, amountSats: Long, comment: String = "", recipient: String = note.pubKey, journalId: String? = null, splitBatch: String? = null, resolveLightning: (suspend () -> String?)? = null): Boolean = withContext(Dispatchers.IO) {
        require(amountSats in 1..MAX_ZAP_SATS) { "Invalid zap amount" }
        val message = comment.trimEnd()
        require(message.codePointCount(0, message.length) <= 280) { "Zap message is too long" }
        val messageHash = sha256(message.toByteArray(Charsets.UTF_8))
        val connection = store.connection(signer.pubKey) ?: error("Connect a wallet in settings")
        var step = "Loading payment history"
        var paymentUncertain = false
        val diagnostics = linkedMapOf("Amount" to "$amountSats sats", "Note" to note.id.take(12), "Recipient" to recipient.take(12))
        // Only pre-payment HTTP requests and status lookups retry; never resend pay_invoice.
        suspend fun lightningGet(url: String, label: String): JSONObject {
            val failures = mutableListOf<java.io.IOException>()
            repeat(3) { attempt ->
                if (attempt > 0) delay(if (attempt == 1) 1_000 else 3_000)
                currentCoroutineContext().ensureActive()
                diagnostics["$label attempts"] = (attempt + 1).toString()
                try {
                    // Reuse the exact callback URL and signed zap request. An invoice
                    // response is validated before any wallet payment can be submitted.
                    return get(url) { redirect ->
                        diagnostics["$label redirects"] = listOfNotNull(diagnostics["$label redirects"], redirect).joinToString("; ")
                    }
                } catch (failure: java.io.IOException) {
                    currentCoroutineContext().ensureActive()
                    diagnostics["$label network error ${attempt + 1}"] = walletErrorReason(failure)
                    // Certificate/TLS and malformed HTTP failures require a fix, not retries.
                    if (failure is javax.net.ssl.SSLException || failure is java.net.ProtocolException || attempt == 2) {
                        failures.filter { it !== failure }.forEach(failure::addSuppressed)
                        throw failure
                    }
                    failures += failure
                }
            }
            error("Unreachable Lightning request attempt")
        }
        suspend fun lookupPayment(invoice: String): JSONObject {
            for (attempt in 1..3) {
                currentCoroutineContext().ensureActive()
                diagnostics["Wallet lookup attempts"] = attempt.toString()
                try {
                    return NwcWallet.request(connection, "lookup_invoice", JSONObject().put("invoice", invoice))
                } catch (failure: WalletFailure) {
                    diagnostics["Wallet lookup error $attempt"] = walletErrorReason(failure)
                    if (!failure.retryable || attempt == 3) throw failure
                    delay(if (attempt == 1) 1_000 else 3_000)
                }
            }
            error("Unreachable lookup attempt")
        }
        try {
            val paymentId = journalId ?: sha256("${connection.pubkey}:${signer.pubKey}:${note.id}".toByteArray())
            val existing = store.payment(paymentId)
            if (existing?.optString("status") == "paid" && existing.optLong("sats", DEFAULT_ZAP_SATS) == amountSats &&
                existing.optString("messageHash", sha256(ByteArray(0))) == messageHash) {
                store.savePayment(paymentId, existing.put("author", signer.pubKey).put("note", note.id))
                return@withContext false
            }
            if (existing?.optString("status") == "paid") {
                // Preserve history before replacing the settled journal with a different explicit amount.
                store.savePayment(sha256(existing.getString("invoice").toByteArray()), existing)
            }
            if (existing != null && existing.optString("status") != "paid") {
                step = "Checking previous payment"
                paymentUncertain = true
                // Unknown outcomes must be checked, never replaced with a fresh payable invoice.
                val status = lookupPayment(existing.getString("invoice"))
                diagnostics["Wallet lookup response"] = paymentResponseShape(status)
                if (confirmedPayment(status, existing.getString("hash"), existing.optLong("sats", DEFAULT_ZAP_SATS), lookup = true)) {
                    store.savePayment(paymentId, existing.put("status", "paid").put("author", signer.pubKey).put("note", note.id))
                    paymentUncertain = false
                    if (existing.optLong("sats", DEFAULT_ZAP_SATS) != amountSats ||
                        existing.optString("messageHash", sha256(ByteArray(0))) != messageHash)
                        throw WalletFailure("Previous zap confirmed. No new payment was sent; submit your new zap again to send it.")
                    return@withContext true
                }
                throw WalletFailure("Payment not confirmed. Check your wallet; no new payment was sent.")
            }
            step = "Loading Lightning address"
            // Only new payments need metadata. Journal recovery above must never wait on profiles
            // or turn a previous uncertain payment into a new payment attempt.
            val address = lightning?.takeIf(String::isNotBlank) ?: resolveLightning?.invoke()
            require(!address.isNullOrBlank()) { "This recipient's loaded profile has no Lightning address" }
            val endpoint = LnurlForm.toUrl(address) ?: error("This recipient's Lightning address is invalid")
            val lnurl = LnurlForm.urlToBech32(endpoint)
            diagnostics["Provider"] = endpoint.toHttpUrl().host
            val params = lightningGet(endpoint, "Address")
            require(params.optString("tag") == "payRequest" && params.optBoolean("allowsNostr") && Protocol.publicKey(params.optString("nostrPubkey")) != null) { "This address does not support Nostr zaps" }
            val msats = amountSats * 1000
            require(msats >= params.getLong("minSendable") && msats <= params.getLong("maxSendable")) { "This address does not accept a $amountSats-sat zap" }
            val callback = params.getString("callback")
            require(MediaContent.secureUrl(callback) != null) { "Invalid Lightning callback" }
            val tags = buildList {
                add(arrayOf("p", recipient)); add(arrayOf("e", note.id)); add(arrayOf("k", note.kind.toString()))
                add(arrayOf("amount", msats.toString()))
                add(arrayOf("lnurl", lnurl))
                add((listOf("relays") + relays.mapNotNull(Protocol::relay).distinct().take(6)).toTypedArray())
                if (note.kind in 30000..39999) note.tagValue("d")?.let { add(arrayOf("a", "${note.kind}:${note.pubKey}:$it")) }
            }.toTypedArray()
            val now = System.currentTimeMillis() / 1000
            step = "Signing zap request"
            val request = signerRequests.run { withTimeout(180_000) { signer.sign<Event>(now, 9734, tags, message) } }
            require(request.pubKey == signer.pubKey && request.kind == 9734 && request.createdAt == now && request.content == message &&
                request.tags.contentDeepEquals(tags) && request.verify()) { "Invalid signed zap request" }
            val json = request.toJson()
            val url = callback.toHttpUrl().newBuilder().setQueryParameter("amount", msats.toString()).setQueryParameter("nostr", json).setQueryParameter("lnurl", lnurl).build()
            step = "Requesting Lightning invoice"
            diagnostics["Callback host"] = url.host
            val invoice = lightningGet(url.toString(), "Invoice").getString("pr")
            step = "Validating Lightning invoice"
            require(LnInvoiceUtil.getAmountInSats(invoice).compareTo(BigDecimal.valueOf(amountSats)) == 0) { "Invoice amount does not match $amountSats sats" }
            val descriptionHash = invoiceHash(invoice, 'h')
            val requestHash = sha256(json.toByteArray(Charsets.UTF_8))
            // Match established clients: the endpoint's invoice is payable even when its
            // description does not commit to the zap request. Receipt validation is separate.
            diagnostics["Description hash"] = when {
                descriptionHash == null -> "absent"
                descriptionHash == requestHash -> "matches zap request"
                descriptionHash == sha256(params.optString("metadata").toByteArray(Charsets.UTF_8)) -> "matches LNURL metadata instead of zap request"
                else -> "differs from zap request"
            }
            diagnostics["Expected description hash prefix"] = requestHash.take(12)
            diagnostics["Received description hash prefix"] = descriptionHash?.take(12) ?: "absent"
            val hash = invoiceHash(invoice, 'p') ?: error("Invoice is missing its payment hash (BOLT11 p tag). No payment sent")
            diagnostics["Payment hash prefix"] = hash.take(12)
            require((LnInvoiceUtil.parseDetails(invoice)?.expiresAt() ?: 0L) > System.currentTimeMillis() / 1000) { "Invoice expired" }
            val record = JSONObject().put("invoice", invoice).put("hash", hash).put("status", "pending").put("messageHash", messageHash).put("sats", amountSats).put("time", System.currentTimeMillis()).put("author", signer.pubKey).put("note", note.id)
            if (splitBatch != null) record.put("splitBatch", splitBatch)
            step = "Saving payment"
            store.savePayment(paymentId, record)
            // Persist before sending: process death or timeout must never cause a duplicate payment.
            step = "Paying Lightning invoice"
            paymentUncertain = true
            var recoveredByLookup = false
            val paid = try { NwcWallet.request(connection, "pay_invoice", JSONObject().put("invoice", invoice)) }
            catch (failure: WalletFailure) {
                if (failure.code in setOf("UNAUTHORIZED", "RESTRICTED", "QUOTA_EXCEEDED", "INSUFFICIENT_BALANCE", "NOT_IMPLEMENTED", "RATE_LIMITED", "UNSUPPORTED_ENCRYPTION")) {
                    paymentUncertain = false
                    store.forgetPayment(paymentId)
                }
                if (failure.code != "NWC_TIMEOUT") throw failure
                diagnostics["Wallet payment error"] = walletErrorReason(failure)
                step = "Checking payment after timeout"
                recoveredByLookup = true
                lookupPayment(invoice)
            }
            step = "Verifying payment confirmation"
            diagnostics[if (recoveredByLookup) "Wallet lookup response" else "Wallet payment response"] = paymentResponseShape(paid)
            if (!confirmedPayment(paid, hash, amountSats, lookup = recoveredByLookup)) {
                require(!recoveredByLookup) {
                    "Wallet payment timed out and its lookup has no verifiable confirmation (${paymentResponseShape(paid)})"
                }
                // Some wallets acknowledge before returning the preimage. Query only;
                // never submit a second payment to recover a missing confirmation.
                step = "Checking payment confirmation"
                val status = lookupPayment(invoice)
                diagnostics["Wallet lookup response"] = paymentResponseShape(status)
                require(confirmedPayment(status, hash, amountSats, lookup = true)) {
                    "Wallet response has no verifiable payment confirmation (${paymentResponseShape(status)})"
                }
            }
            store.savePayment(paymentId, record.put("status", "paid"))
            paymentUncertain = false
            true

        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            currentCoroutineContext().ensureActive()
            val reason = if (failure is TimeoutCancellationException) "timed out waiting for signer" else walletErrorReason(failure)
            val uncertainty = if (paymentUncertain) ". Payment may have completed; tap again to check status." else ""
            val report = buildString {
                appendLine("Feather ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}")
                appendLine("Time: ${java.time.Instant.now()}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("Step: $step")
                diagnostics.forEach { (key, value) -> appendLine("$key: $value") }
                appendLine("Payment outcome uncertain: $paymentUncertain")
                appendLine("$reason$uncertainty")
                appendLine()
                // Sanitize each line, retaining stack layout without wallet secrets or invoices.
                failure.stackTraceToString().take(24000).lineSequence().forEach { appendLine(walletErrorText(it)) }
            }
            throw WalletFailure("$step: $reason$uncertainty", (failure as? WalletFailure)?.code, report)
        } finally { connection.secret.fill(0) }
    }
    /** Only schema information is copied, never a preimage or arbitrary response values. */
    private fun paymentResponseShape(result: JSONObject): String {
        val value = result.opt("preimage")
        val preimage = when (value) {
            null, JSONObject.NULL -> "missing"
            is String -> "string(${value.length}), hex=${Regex("[a-fA-F0-9]{64}").matches(value.trim())}"
            else -> "non-string"
        }
        val state = result.optString("state").takeIf { it in setOf("pending", "settled", "accepted", "expired", "failed") } ?: "absent/unknown"
        val settlement = when {
            !result.has("settled_at") || result.isNull("settled_at") -> "absent"
            settlementTime(result) != null -> "valid"
            else -> "invalid/zero/future"
        }
        val amount = result.opt("amount")?.toString()?.takeIf { it.length <= 64 }?.toBigDecimalOrNull()?.toString()?.take(24) ?: "absent/invalid"
        return "preimage=$preimage; state=$state; payment_hash=${result.has("payment_hash")}; amount_msats=$amount; settled_at=$settlement"
    }

    private fun settlementTime(result: JSONObject): Long? = result.opt("settled_at")?.toString()?.toLongOrNull()
        ?.takeIf { it > 0 && it <= System.currentTimeMillis() / 1000 + 300 }

    private fun confirmedPayment(result: JSONObject, hash: String, sats: Long, lookup: Boolean = false): Boolean {
        val value = (result.opt("preimage") as? String)?.trim().orEmpty()
        if (Regex("[a-fA-F0-9]{64}").matches(value)) {
            require(sha256(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) == hash) { "Invalid payment confirmation: preimage does not match invoice payment hash" }
            return true
        }
        // A signed, correlated lookup response can establish settlement without a
        // preimage, provided it identifies this exact payment and amount.
        val state = (result.opt("state") as? String)?.trim()
        // NIP-47 state is optional. Legacy wallets report settlement with settled_at.
        // Do not let that timestamp override an explicit pending/failed/unknown state.
        val settled = state == "settled" ||
            ((result.isNull("state") || state == "") && settlementTime(result) != null)
        val amount = result.opt("amount")?.toString()?.takeIf { it.length <= 64 }?.toBigDecimalOrNull()
        return lookup && settled && result.optString("payment_hash").equals(hash, ignoreCase = true) &&
            amount?.compareTo(BigDecimal.valueOf(sats).multiply(BigDecimal.valueOf(1000))) == 0
    }
}
