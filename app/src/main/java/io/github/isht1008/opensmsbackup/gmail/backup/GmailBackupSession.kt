package io.github.isht1008.opensmsbackup.gmail.backup

/**
 * In-process ownership marker for the Gmail profile used by the active backup.
 * Process-death survival belongs to the future durable backup implementation.
 */
object GmailBackupSession {
    @Volatile
    private var activeProfileId: String? = null

    @Synchronized
    fun begin(profileId: String): Boolean {
        if (activeProfileId != null) {
            return false
        }

        activeProfileId = profileId
        return true
    }

    @Synchronized
    fun end(profileId: String) {
        if (activeProfileId == profileId) {
            activeProfileId = null
        }
    }

    fun isActive(profileId: String): Boolean =
        activeProfileId == profileId
}
