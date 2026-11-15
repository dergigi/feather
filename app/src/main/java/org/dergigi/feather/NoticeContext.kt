package org.dergigi.feather

import com.vitorpamplona.quartz.nip01Core.core.Event

private val noticeWhitespace = Regex("\\s+")

/** Bound previews before normalizing untrusted profile/event text. No fetching or parsing. */
internal fun noticePreview(text: String): String {
    val preview = text.take(161).replace(noticeWhitespace, " ").trim()
    return if (preview.length > 64 || text.length > 161) preview.take(64) + "…" else preview
}

internal fun noticeProfileName(pubkey: String, profiles: Map<String, Profile>): String =
    profiles[pubkey]?.name?.let(::noticePreview)?.takeIf(String::isNotBlank)
        ?: FeatherRepository.shortKey(pubkey)

internal fun noticeEventContext(note: Event, profiles: Map<String, Profile>, type: String = "note"): String {
    val author = noticeProfileName(note.pubKey, profiles)
    val preview = noticePreview(note.content)
    return "$type by $author" + if (preview.isBlank()) " (${FeatherRepository.shortKey(note.id)})" else " “$preview”"
}

internal fun showNoticeFailure(context: String, error: Throwable, target: String,
    reason: String = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName,
    diagnostics: String = error.stackTraceToString(), displayText: String? = null) {
    val text = "$context: $reason"
    AppMessages.show(displayText ?: text, "$text\n\nTarget: $target\n\n$diagnostics")
}
