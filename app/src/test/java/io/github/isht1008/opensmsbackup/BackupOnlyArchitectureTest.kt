package io.github.isht1008.opensmsbackup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupOnlyArchitectureTest {
    @Test fun `manifest and production sources contain no SMS role or provider insertion`() {
        val manifest = File("src/main/AndroidManifest.xml")
        val sources = File("src/main/java")
        assertTrue(manifest.isFile)
        assertTrue(sources.isDirectory)

        val content = buildString {
            append(manifest.readText())
            sources.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .forEach { append('\n').append(it.readText()) }
        }
        listOf(
            "RoleManager.ROLE_SMS",
            "createRequestRoleIntent",
            "Telephony.Sms.Inbox",
            "Telephony.Sms.Sent.CONTENT_URI",
            "SMS_DELIVER",
            "WAP_PUSH_DELIVER",
            "RESPOND_VIA_MESSAGE",
            "android.intent.action.SENDTO"
        ).forEach { forbidden ->
            assertFalse("Backup-only source contains $forbidden", content.contains(forbidden))
        }
    }
}
