package io.github.isht1008.opensmsbackup.account.data

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import io.github.isht1008.opensmsbackup.database.AccountSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountManagementItemFactoryTest {
    private val accountA = AccountProfileEntity("a", accountEmail = "a@example.com")
    private val accountB = AccountProfileEntity(
        "b",
        accountEmail = "b@example.com",
        connectionState = AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED
    )

    @Test fun `each card receives policy by immutable profile id`() {
        val items = items(
            AccountSettingsEntity("a", backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY.name),
            AccountSettingsEntity("b", backupMode = GmailBackupMode.MIRROR.name)
        )
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, items.single { it.profile.profileId == "a" }.policy)
        assertEquals(GmailBackupMode.MIRROR, items.single { it.profile.profileId == "b" }.policy)
    }

    @Test fun `selected account changes cannot leak policy across cards`() {
        val before = items(
            AccountSettingsEntity("a", backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY.name),
            AccountSettingsEntity("b", backupMode = GmailBackupMode.MIRROR.name)
        )
        val after = AccountManagementItemFactory.create(before.map { it.profile.reorderForSelection() }, listOf(
            AccountSettingsEntity("a", backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY.name),
            AccountSettingsEntity("b", backupMode = GmailBackupMode.MIRROR.name)
        ))
        assertEquals(before.map { it.policy }, after.map { it.policy })
    }

    @Test fun `policy update affects only matching account and disconnected states retain policy`() {
        val disconnectedA = accountA.copy(connectionState = AccountProfileEntity.CONNECTION_STATE_DISCONNECTED)
        val initial = AccountManagementItemFactory.create(listOf(disconnectedA, accountB), listOf(
            AccountSettingsEntity("a", backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY.name),
            AccountSettingsEntity("b", backupMode = GmailBackupMode.MIRROR.name)
        ))
        val updated = AccountManagementItemFactory.create(listOf(disconnectedA, accountB), listOf(
            AccountSettingsEntity("a", backupMode = GmailBackupMode.MIRROR.name),
            AccountSettingsEntity("b", backupMode = GmailBackupMode.MIRROR.name)
        ))
        assertEquals(GmailBackupMode.ARCHIVE_APPEND_ONLY, initial[0].policy)
        assertEquals(GmailBackupMode.MIRROR, updated[0].policy)
        assertEquals(initial[1].policy, updated[1].policy)
        assertEquals(AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED, updated[1].profile.connectionState)
    }

    private fun items(vararg settings: AccountSettingsEntity) =
        AccountManagementItemFactory.create(listOf(accountA, accountB), settings.toList())

    private fun AccountProfileEntity.reorderForSelection() = copy(updatedTime = updatedTime + 1)
}
