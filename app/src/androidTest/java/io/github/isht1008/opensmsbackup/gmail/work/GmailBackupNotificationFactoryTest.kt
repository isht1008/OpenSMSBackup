package io.github.isht1008.opensmsbackup.gmail.work

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GmailBackupNotificationFactoryTest {
    @Test fun progressNotificationContainsExactCancelActionAndNoMessageContent() {
        val notification = GmailBackupNotificationFactory(
            ApplicationProvider.getApplicationContext()
        ).notification(
            UUID.randomUUID(),
            GmailBackupWorkProgress(
                phase = GmailBackupPhase.RUNNING,
                profileId = "profile-a",
                accountEmail = "user@example.com",
                checked = 2,
                total = 3,
                uploaded = 1,
                unchanged = 1,
                statusMessage = "Checking conversation 2 of 3"
            )
        )

        assertEquals(1, notification.actions.size)
        assertEquals("Cancel backup", notification.actions[0].title.toString())
        assertNotNull(notification.actions[0].actionIntent)
        val rendered = notification.extras.toString().lowercase()
        assertFalse(rendered.contains("oauth"))
        assertFalse(rendered.contains("mime"))
        assertFalse(rendered.contains("sms body"))
    }
}
