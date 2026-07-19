package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.gmail.backup.SmsFingerprint
import io.github.isht1008.opensmsbackup.sms.SmsMessage

class RestoreDuplicateIndex(existing: Collection<SmsMessage>, private val defaultRegion: String) {
    private val fingerprints = HashSet<String>().apply {
        existing.forEach { addAll(SmsFingerprint.aliases(it, defaultRegion)) }
    }

    fun contains(message: SmsMessage): Boolean =
        SmsFingerprint.aliases(message, defaultRegion).any(fingerprints::contains)

    fun record(message: SmsMessage) {
        fingerprints.addAll(SmsFingerprint.aliases(message, defaultRegion))
    }
}
