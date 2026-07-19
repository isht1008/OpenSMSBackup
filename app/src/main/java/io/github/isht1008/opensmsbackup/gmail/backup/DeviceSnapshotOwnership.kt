package io.github.isht1008.opensmsbackup.gmail.backup

object DeviceSnapshotOwnership {
    fun matchesV2(
        document: GmailArchiveDocument,
        accountEmail: String,
        deviceId: String,
        deviceLabelId: String,
        conversation: SmsConversationSnapshot
    ): Boolean {
        if (!document.accountEmail.equals(accountEmail, ignoreCase = true)) return false
        if (document.deviceIdHeader != deviceId) return false
        if (document.identityVersionHeader != "2") return false
        if (deviceLabelId !in document.labelIds) return false
        val expected = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            conversation.address,
            accountEmail,
            deviceId
        )
        val parsed = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            document.conversation.address,
            document.accountEmail,
            deviceId
        )
        return expected == parsed && document.conversationKeyHeader == expected
    }
}
