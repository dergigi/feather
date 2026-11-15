package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class WalletFailure(message: String, val code: String? = null, val report: String? = null, val retryable: Boolean = false,
    val missingLightningRecipient: String? = null) : Exception(message)

/** Bound and redact external error text before displaying it or copying it into a report. */
internal fun walletErrorText(raw: String): String = raw.take(8192)
    .replace(Regex("(?i)(?:nostr\\+walletconnect|nostrwalletconnect|bunker)://[^\\s]+"), "[connection redacted]")
    .replace(Regex("(?i)nsec1[a-z0-9]+"), "[secret redacted]")
    .replace(Regex("(?i)(?:https?|wss?)://[^\\s]+")) { match ->
        runCatching { java.net.URI(match.value).host }.getOrNull() ?: "[endpoint]"
    }
    .replace(Regex("(?i)[a-f0-9]{64,}"), "[redacted]")
    .replace(Regex("(?i)lnbc[a-z0-9]+"), "[invoice]")
    .map { if (it.isISOControl()) ' ' else it }.joinToString("")
    .replace(Regex("\\s+"), " ").trim().take(320)

internal fun walletErrorReason(error: Exception): String = walletErrorText(
    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
)

/** Dedicated bounded NWC sockets. First connected relay handles the request; no automatic payment retry. */
internal object NwcWallet {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    suspend fun request(connection: WalletConnection, method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val signer = NostrSignerInternal(KeyPair(privKey = connection.secret))
        val inbox = Channel<Event>(64)
        val opened = Channel<WebSocket>(3)
        val infoId = UUID.randomUUID().toString()
        val responseId = UUID.randomUUID().toString()
        val relayErrors = java.util.concurrent.ConcurrentHashMap<WebSocket, String>()
        val requestId = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val responseSubscription = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val sockets = connection.relays.map { relay ->
            val authSent = java.util.concurrent.atomic.AtomicBoolean(false)
            val authId = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val infoRequest = JSONArray().put("REQ").put(infoId).put(JSONObject().put("kinds", JSONArray().put(13194))
                .put("authors", JSONArray().put(connection.pubkey)).put("limit", 1)).toString()
            http.newWebSocket(Request.Builder().url(relay).build(), object : WebSocketListener() {
                override fun onFailure(socket: WebSocket, error: Throwable, response: Response?) {
                    val causes = generateSequence(error) { it.cause }.take(12).toList()
                    val certificateReason = when {
                        causes.any { it is java.security.cert.CertificateExpiredException } -> "TLS certificate expired; the wallet relay needs to renew its certificate"
                        causes.any { it is java.security.cert.CertificateNotYetValidException } -> "TLS certificate is not valid yet; check the device date and time"
                        causes.any { it is java.security.cert.CertPathValidatorException || it is java.security.cert.CertificateException } ->
                            "TLS certificate chain rejected: " + causes.last().let { it.message ?: it.javaClass.simpleName }
                        else -> null
                    }
                    val reason = response?.let { "HTTP ${it.code}" } ?: certificateReason ?: error.message ?: error.javaClass.simpleName
                    relayErrors[socket] = walletErrorText("Wallet relay $relay: $reason")
                }
                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    relayErrors[socket] = walletErrorText("Wallet relay closed ($code): $reason")
                }
                override fun onOpen(socket: WebSocket, response: Response) {
                    socket.send(infoRequest)
                    opened.trySend(socket)
                }
                override fun onMessage(socket: WebSocket, text: String) {
                    if (text.length > 131072) return
                    // Parsing and signature verification run on the wallet IO coroutine below.
                    runCatching {
                        val data = JSONArray(text)
                        if (data.optString(0) == "AUTH" && data.optString(1).length in 1..1024 && authSent.compareAndSet(false, true)) {
                            launch(Dispatchers.IO) {
                                runCatching {
                                    val auth = signer.sign<Event>(System.currentTimeMillis() / 1000, 22242,
                                        arrayOf(arrayOf("relay", relay), arrayOf("challenge", data.getString(1))), "")
                                    authId.set(auth.id)
                                    socket.send(JSONArray().put("AUTH").put(JSONObject(auth.toJson())).toString())
                                }
                            }
                        }
                        if (data.optString(0) == "OK" && data.optString(1) == authId.get() && data.optBoolean(2)) {
                            socket.send(infoRequest)
                            responseSubscription.get()?.let { socket.send(it) }
                        }
                        if (data.optString(0) == "OK" && !data.optBoolean(2) &&
                            data.optString(1) in listOfNotNull(requestId.get(), authId.get()))
                            relayErrors[socket] = "Wallet relay rejected request: ${walletErrorText(data.optString(3))}"
                        if (data.optString(0) == "CLOSED" && data.optString(1) == responseId)
                            relayErrors[socket] = "Wallet relay closed subscription: ${walletErrorText(data.optString(2))}"
                        if (data.optString(0) == "EVENT" && data.optString(1) in listOf(infoId, responseId))
                            inbox.trySend(Event.fromJson(data.getJSONObject(2).toString()))
                    }
                }
            })
        }
        try {
            val socket = withTimeoutOrNull(12_000) { opened.receive() }
                ?: throw WalletFailure(relayErrors.values.firstOrNull() ?: "NWC relay connection timed out after 12s", retryable = true)
            val info = withTimeoutOrNull(4_000) {
                while (true) {
                    val event = inbox.receive()
                    if (event.kind == 13194 && event.pubKey == connection.pubkey && event.verify()) return@withTimeoutOrNull event
                }
                @Suppress("UNREACHABLE_CODE") null
            }
            val nip44 = info?.tags?.any { it.firstOrNull() == "encryption" && it.drop(1).any { value -> "nip44_v2" in value.split(' ') } } == true
            val plain = JSONObject().put("method", method).put("params", params).toString()
            val encrypted = if (nip44) signer.nip44Encrypt(plain, connection.pubkey) else signer.nip04Encrypt(plain, connection.pubkey)
            val now = System.currentTimeMillis() / 1000
            val event = signer.sign<Event>(now, 23194, buildList {
                add(arrayOf("p", connection.pubkey)); add(arrayOf("expiration", (now + 90).toString()))
                if (nip44) add(arrayOf("encryption", "nip44_v2"))
            }.toTypedArray(), encrypted)
            val subscription = JSONArray().put("REQ").put(responseId).put(JSONObject().put("kinds", JSONArray().put(23195))
                .put("authors", JSONArray().put(connection.pubkey)).put("#e", JSONArray().put(event.id))
                .put("#p", JSONArray().put(signer.pubKey))).toString()
            requestId.set(event.id)
            responseSubscription.set(subscription)
            socket.send(subscription)
            if (!socket.send(JSONArray().put("EVENT").put(JSONObject(event.toJson())).toString()))
                throw WalletFailure("Wallet relay disconnected", retryable = true)
            withTimeoutOrNull(if (method == "pay_invoice") 90_000 else 20_000) {
                while (true) {
                    val response = inbox.receive()
                    if (response.kind != 23195 || response.pubKey != connection.pubkey || response.tagValue("e") != event.id ||
                        response.tagValue("p") != signer.pubKey || !response.verify()) continue
                    val decoded = runCatching { if (nip44) signer.nip44Decrypt(response.content, connection.pubkey)
                        else signer.nip04Decrypt(response.content, connection.pubkey) }.getOrNull() ?: continue
                    val json = runCatching { JSONObject(decoded) }.getOrNull() ?: continue
                    if (json.optString("result_type") != method) continue
                    json.optJSONObject("error")?.let {
                        val code = it.optString("code").takeIf { value -> Regex("[A-Z_]{1,40}").matches(value) } ?: "OTHER"
                        val reason = walletErrorText(it.optString("message")).takeIf(String::isNotBlank)
                        throw WalletFailure("$code${reason?.let { detail -> ": $detail" }.orEmpty()}", code)
                    }
                    return@withTimeoutOrNull json.optJSONObject("result") ?: throw WalletFailure("Invalid wallet response")
                }
                @Suppress("UNREACHABLE_CODE") JSONObject()
            } ?: throw WalletFailure(relayErrors[socket] ?: "NWC $method: wallet did not respond within ${if (method == "pay_invoice") 90 else 20}s", "NWC_TIMEOUT", retryable = true)
        } finally {
            sockets.forEach { it.cancel() }; inbox.close(); opened.close()
        }
    }
}
