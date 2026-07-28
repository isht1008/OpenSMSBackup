package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.database.ConversationSnapshotEntity
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ArchiveIncrementalCheckpointTest {
    @Test fun `matching checkpoint with cached Gmail reference skips remote comparison`() {
        val conversation = conversation(message(1, 100, "one"))
        val hash = LocalConversationSourceHashGenerator.generate(conversation)
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(conversation.threadId to snapshot(hash)),
            ACCOUNT,
            DEVICE
        )
        assertEquals(1, result.locallyUnchanged.size)
        assertEquals(0, result.requiringComparison.size)
    }

    @Test fun `legacy null checkpoint requires one comparison`() {
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation(message(1, 100, "one"))),
            mapOf(7L to snapshot(null)),
            ACCOUNT,
            DEVICE
        )
        assertEquals(0, result.locallyUnchanged.size)
        assertEquals(1, result.requiringComparison.size)
        assertEquals(1, result.uninitialized)
    }

    @Test fun `covered null checkpoint with exact merged hash bootstraps locally`() {
        val conversation = conversation(message(1, 100, "one"))
        val result = classifyForBootstrap(
            conversation,
            snapshot(null).copy(
                snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
                backupTime = 1_000L
            ),
            completedFullBackupTime = 2_000L
        )
        assertEquals(1, result.locallyUnchanged.size)
        assertEquals(1, result.locallyBootstrappable.size)
        assertEquals(0, result.requiringComparison.size)
        assertEquals(
            LocalConversationSourceHashGenerator.generate(conversation),
            result.locallyBootstrappable.single().localSourceHash
        )
    }

    @Test fun `bootstrap requires completed full coverage and cached Gmail id`() {
        val conversation = conversation(message(1, 100, "one"))
        val exact = snapshot(null).copy(
            snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
            backupTime = 2_000L
        )
        assertEquals(1, classifyForBootstrap(conversation, exact, 0L).requiringComparison.size)
        assertEquals(1, classifyForBootstrap(conversation, exact, 1_999L).requiringComparison.size)
        assertEquals(
            1,
            classifyForBootstrap(
                conversation,
                exact.copy(gmailMessageId = ""),
                2_000L
            ).requiringComparison.size
        )
    }

    @Test fun `snapshot mismatch deletion addition and equal-count replacement refuse bootstrap`() {
        val original = conversation(message(1, 100, "one"), message(2, 200, "two"))
        val persisted = snapshot(null).copy(
            messageCount = 2,
            snapshotHash = ConversationSnapshotHashGenerator.generate(original),
            backupTime = 1_000L
        )
        val variants = listOf(
            conversation(message(1, 100, "one")),
            conversation(message(1, 100, "one"), message(2, 200, "two"), message(3, 300, "three")),
            conversation(message(1, 100, "one"), message(3, 300, "three"))
        )
        variants.forEach {
            assertEquals(1, classifyForBootstrap(it, persisted, 2_000L).requiringComparison.size)
        }
    }

    @Test fun `account or unsafe installation context refuses bootstrap`() {
        val conversation = conversation(message(1, 100, "one"))
        val exact = snapshot(null).copy(
            snapshotHash = ConversationSnapshotHashGenerator.generate(conversation),
            backupTime = 1_000L
        )
        assertEquals(
            1,
            classifyForBootstrap(
                conversation,
                exact.copy(accountEmail = "other@example.com"),
                2_000L
            ).requiringComparison.size
        )
        val unsafe = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(conversation.threadId to exact),
            ACCOUNT,
            ACCOUNT,
            DEVICE,
            2_000L,
            installationContextSafe = false
        )
        assertEquals(1, unsafe.requiringComparison.size)
    }

    @Test fun `missing cached Gmail reference requires recovery despite matching hash`() {
        val conversation = conversation(message(1, 100, "one"))
        val hash = LocalConversationSourceHashGenerator.generate(conversation)
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(conversation.threadId to snapshot(hash).copy(gmailMessageId = "")),
            ACCOUNT,
            DEVICE
        )
        assertEquals(1, result.requiringComparison.size)
        assertEquals(1, result.missingCachedGmailId)
    }

    @Test fun `delete plus add with same count changes local source hash`() {
        val before = conversation(message(1, 100, "one"), message(2, 200, "two"))
        val after = conversation(message(1, 100, "one"), message(3, 300, "three"))
        assertNotEquals(
            LocalConversationSourceHashGenerator.generate(before),
            LocalConversationSourceHashGenerator.generate(after)
        )
    }

    @Test fun `account cannot use another accounts checkpoint`() {
        val conversation = conversation(message(1, 100, "one"))
        val hash = LocalConversationSourceHashGenerator.generate(conversation)
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(conversation.threadId to snapshot(hash).copy(accountId = "account-b")),
            ACCOUNT,
            DEVICE
        )
        assertEquals(1, result.requiringComparison.size)
    }

    @Test fun `device profile cannot use another devices checkpoint`() {
        val conversation = conversation(message(1, 100, "one"))
        val hash = LocalConversationSourceHashGenerator.generate(conversation)
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(
                conversation.threadId to snapshot(hash).copy(
                    localSourceDeviceId = "device-b"
                )
            ),
            ACCOUNT,
            DEVICE
        )
        assertEquals(1, result.requiringComparison.size)
    }

    @Test fun `only incremental Archive mode is eligible for local fast path`() {
        assertEquals(
            true,
            ArchiveLocalCheckpointClassifier.isFastPathEligible(
                GmailBackupScope.INCREMENTAL,
                GmailBackupMode.ARCHIVE_APPEND_ONLY
            )
        )
        assertEquals(
            false,
            ArchiveLocalCheckpointClassifier.isFastPathEligible(
                GmailBackupScope.FULL,
                GmailBackupMode.ARCHIVE_APPEND_ONLY
            )
        )
        assertEquals(
            false,
            ArchiveLocalCheckpointClassifier.isFastPathEligible(
                GmailBackupScope.RECENT_TEST,
                GmailBackupMode.ARCHIVE_APPEND_ONLY
            )
        )
    }

    @Test fun `all generated output fields affect local source hash`() {
        val base = message(1, 100, "one")
        val baseline = LocalConversationSourceHashGenerator.generate(conversation(base))
        val variants = listOf(
            base.copy(id = 2),
            base.copy(threadId = 8),
            base.copy(address = "other"),
            base.copy(contactName = "Other"),
            base.copy(body = "changed"),
            base.copy(date = 101),
            base.copy(type = 2),
            base.copy(subscriptionId = 2),
            base.copy(isRead = false),
            base.copy(serviceCenter = "changed")
        )
        variants.forEach { changed ->
            assertNotEquals(
                baseline,
                LocalConversationSourceHashGenerator.generate(conversation(changed))
            )
        }
    }

    @Test fun `derived formatted date does not invalidate raw source checkpoint`() {
        val base = message(1, 100, "one")
        assertEquals(
            LocalConversationSourceHashGenerator.generate(conversation(base)),
            LocalConversationSourceHashGenerator.generate(
                conversation(base.copy(dateFormatted = "different locale rendering"))
            )
        )
    }

    @Test fun `matching legacy v1 checkpoint skips Gmail and is marked for local upgrade`() {
        val conversation = conversation(message(1, 100, "one"))
        val legacyHash = LocalConversationSourceHashGenerator.generateLegacyV1(conversation)
        val result = ArchiveLocalCheckpointClassifier.classify(
            listOf(conversation),
            mapOf(conversation.threadId to snapshot(legacyHash)),
            ACCOUNT,
            DEVICE
        )
        assertEquals(1, result.locallyUnchanged.size)
        assertEquals(0, result.requiringComparison.size)
        assertEquals(1, result.requiringLocalHashUpgrade.size)
        assertNotEquals(legacyHash, result.requiringLocalHashUpgrade.single().localSourceHash)
    }

    @Test fun `local source hash is stable across input order locale and timezone`() {
        val originalLocale = Locale.getDefault()
        val originalZone = TimeZone.getDefault()
        try {
            val first = message(1, 100, "one")
            val second = message(2, 200, "two")
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val baseline = LocalConversationSourceHashGenerator.generate(
                conversation(first, second)
            )
            Locale.setDefault(Locale.JAPAN)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val repeated = LocalConversationSourceHashGenerator.generate(
                conversation(second, first)
            )
            assertEquals(baseline, repeated)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalZone)
        }
    }

    @Test fun `shared archive index builds at most once`() = runBlocking {
        var builds = 0
        val lazy = LazyGmailArchiveIndex {
            builds++
            GmailArchiveIndex.build(EmptyIndexSource, "user@example.com", "device", "label")
        }
        List(10) { async { lazy.get() } }.awaitAll()
        assertEquals(1, builds)
    }

    @Test fun `failed lazy index build is not cached as initialized`() = runBlocking {
        var builds = 0
        val lazy = LazyGmailArchiveIndex {
            builds++
            if (builds == 1) error("failed")
            GmailArchiveIndex.build(EmptyIndexSource, ACCOUNT, DEVICE, "label")
        }
        assertEquals(true, runCatching { lazy.get() }.isFailure)
        assertEquals(false, lazy.wasBuilt)
        lazy.get()
        assertEquals(true, lazy.wasBuilt)
        assertEquals(2, builds)
    }

    private fun snapshot(localHash: String?) = ConversationSnapshotEntity(
        accountId = ACCOUNT,
        accountEmail = "user@example.com",
        androidThreadId = 7,
        address = "address",
        messageCount = 1,
        snapshotHash = "merged",
        localSourceHash = localHash,
        localSourceDeviceId = DEVICE,
        gmailMessageId = "cached-message",
        firstMessageDate = 100,
        lastMessageDate = 100
    )

    private fun classifyForBootstrap(
        conversation: SmsConversationSnapshot,
        snapshot: ConversationSnapshotEntity,
        completedFullBackupTime: Long
    ) = ArchiveLocalCheckpointClassifier.classify(
        listOf(conversation),
        mapOf(conversation.threadId to snapshot),
        ACCOUNT,
        ACCOUNT,
        DEVICE,
        completedFullBackupTime
    )

    private fun conversation(vararg messages: SmsMessage) =
        SmsConversationSnapshot(7, "address", "Contact", messages.toList())

    private fun message(id: Long, date: Long, body: String) =
        SmsMessage(id, 7, "address", "Contact", body, date, "date", 1)

    private object EmptyIndexSource : GmailArchiveIndexSource {
        override suspend fun listPage(deviceLabelId: String, pageToken: String?) =
            GmailArchiveMessagePage(emptyList(), null)
        override suspend fun readMetadataPage(messageIds: List<String>) =
            emptyList<GmailArchiveMetadataReference>()
    }

    private companion object {
        const val ACCOUNT = "user@example.com"
        const val DEVICE = "device-a"
    }
}
