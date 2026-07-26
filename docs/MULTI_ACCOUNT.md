# Multiple Google accounts

## Status

**Implemented:** Room-backed profiles can be added, selected, reauthorized, logically disconnected, or explicitly revoked. Credential sign-in identifies an account but never establishes Gmail authorization by itself. A reconnect executes account-specific Google Identity authorization for `gmail.modify`; only a completed authorization result marks the profile connected. Manual WorkManager backup captures the immutable profile ID and rejects authorization-required profiles.

**Partially implemented:** Only one global Gmail backup is exposed at a time, although unique names and tags are profile-scoped. Snapshot ownership still normalizes account email internally. Backup policy is independent per profile; schedules, encryption, and destination policy remain absent.

**Planned:** A profile list where every Google account owns separate Gmail/Drive destinations, mode, label policy, contact-name preference, schedule, encryption settings, checkpoints, health, and retry queue. Switching profiles must not reuse credentials, labels, hashes, or remote IDs from another profile.

## Account removal semantics

- **Disconnect Account:** after confirmation, clear the app credential-provider session and mark only the selected profile disconnected locally. The Google OAuth grant remains available for later reconnection. Profile settings, policy metadata, backup history, verification history, device metadata, and Gmail data are preserved.
- **Disconnect and Revoke Google Access:** after exact typed confirmation, revoke the selected account's existing `gmail.modify` grant through Google Identity Services, then clear the Credential Manager session and logically disconnect the profile. Reconnection requires consent again. If post-revocation cleanup fails, the profile is marked authorization-required rather than falsely reported as connected or successfully disconnected.
- **Delete cloud backups:** a separate destructive workflow requiring explicit confirmation; never bundle it with disconnect or revocation.

Credential-session clearing is sign-out/session cleanup for account selection. It does not revoke OAuth access. Conversely, neither logical disconnect nor OAuth revocation deletes or changes Gmail messages, labels, Mirror snapshots, or Archive snapshots.

After successful revocation the retained profile is `AUTHORIZATION_REQUIRED`. Reconnect calls `AuthorizationClient.authorize()` for that exact account and scope. Google may return a resolution that the app launches, or may complete authorization without visible UI when Google determines interaction is unnecessary; OpenSMSBackup cannot and does not force a consent screen. A successful Credential Manager sign-in or successful authorization Task containing an unresolved `PendingIntent` is not treated as completed Gmail authorization.

**Implemented:** The Settings account list joins settings by immutable `profileId`. Every connected, disconnected, or authorization-required card independently displays Archive/Mirror policy plus available last-backup and current-device verification health. Selection does not supply or overwrite card policy.

**Implemented:** Policy changes and both account-exit actions are located on each profile card in Settings. Home shows the selected account and backup status but directs account administration to Settings. Disconnect/revoke always targets the immutable profile represented by the card.

Account email may be useful as a key today, but future identity should prefer an immutable Google account identifier while treating email as mutable display data.

Active-work protection is durable: WorkManager is authoritative after Activity/process recreation. The in-memory `GmailBackupSession` is only a worker-local collision guard and is never the sole disconnect decision.

## Device ownership

**Implemented:** The local Device Profile belongs to the installation and is shared consistently across every connected Gmail account. Each account caches its own Gmail device-label ID, while all accounts use the same immutable device ID and editable friendly metadata. Full manually entered phone numbers remain local; Gmail labels and ordinary Settings display use only the final five digits.
