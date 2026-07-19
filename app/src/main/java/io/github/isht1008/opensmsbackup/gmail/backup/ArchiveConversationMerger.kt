package io.github.isht1008.opensmsbackup.gmail.backup

class ArchiveConversationMerger(
    private val defaultRegion: String = "US"
) {
    data class MergeResult(
        val conversation: SmsConversationSnapshot,
        val appendedCount: Int
    )

    fun merge(
        archived: SmsConversationSnapshot?,
        phone: SmsConversationSnapshot
    ): MergeResult {
        if (archived == null) {
            val unique = uniqueChronological(phone.messages)
            return MergeResult(phone.copy(messages = unique), unique.size)
        }

        val fingerprints = HashSet<String>(archived.messages.size + phone.messages.size)
        val merged = ArrayList(archived.messages)
        archived.messages.forEach { fingerprints += SmsFingerprint.aliases(it, defaultRegion) }

        var appended = 0
        phone.messages.forEach { message ->
            val aliases = SmsFingerprint.aliases(message, defaultRegion)
            if (aliases.none(fingerprints::contains)) {
                fingerprints += aliases
                merged += message
                appended++
            }
        }

        return MergeResult(
            phone.copy(messages = merged.sortedBy { it.date }),
            appended
        )
    }

    private fun uniqueChronological(
        messages: List<io.github.isht1008.opensmsbackup.sms.SmsMessage>
    ): List<io.github.isht1008.opensmsbackup.sms.SmsMessage> {
        val seen = HashSet<String>(messages.size)
        return messages.sortedBy { it.date }.filterTo(ArrayList()) {
            val aliases = SmsFingerprint.aliases(it, defaultRegion)
            if (aliases.any(seen::contains)) false else {
                seen += aliases
                true
            }
        }
    }
}
