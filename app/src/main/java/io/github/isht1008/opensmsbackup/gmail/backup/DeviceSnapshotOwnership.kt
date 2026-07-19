package io.github.isht1008.opensmsbackup.gmail.backup

object DeviceSnapshotOwnership {
    fun matchesV2(
        document: GmailArchiveDocument,
        accountEmail: String,
        deviceId: String,
        deviceLabelId: String,
        conversation: SmsConversationSnapshot,
        defaultRegion: String = "US"
    ): Boolean {
        if (!document.accountEmail.equals(accountEmail, ignoreCase = true)) return false
        if (document.deviceIdHeader != deviceId) return false
        if (document.identityVersionHeader !in setOf("2", "3")) return false
        if (deviceLabelId !in document.labelIds) return false
        val version = if (document.identityVersionHeader == "3") {
            ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS
        } else ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS
        val region = document.defaultRegionHeader ?: defaultRegion
        val expected = ArchiveConversationIdentity.key(
            version,
            conversation.address,
            accountEmail,
            deviceId,
            region
        )
        val parsed = ArchiveConversationIdentity.key(
            version,
            document.conversation.address,
            document.accountEmail,
            deviceId,
            region
        )
        return expected == parsed && document.conversationKeyHeader == expected
    }
}
