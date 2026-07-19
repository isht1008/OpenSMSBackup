# Multiple Google accounts

## Status

**Implemented:** Room-backed profiles can be added, selected, reauthorized, and logically disconnected. Manual WorkManager backup captures the immutable profile ID at enqueue time and reloads that exact profile. Selecting another account cannot retarget active work. Settings queries WorkManager and refuses to disconnect the profile owning active work.

**Partially implemented:** Only one global Gmail backup is exposed at a time, although unique names and tags are profile-scoped. Snapshot ownership still normalizes account email internally. Independent per-profile manual options, schedules, encryption, and destination policy are absent.

**Planned:** A profile list where every Google account owns separate Gmail/Drive destinations, mode, label policy, contact-name preference, schedule, encryption settings, checkpoints, health, and retry queue. Switching profiles must not reuse credentials, labels, hashes, or remote IDs from another profile.

## Account removal semantics

- **Disconnect:** stop jobs, clear local active credentials/selection as appropriate, and preserve remote Gmail/Drive data. Current `clearAccount()` approximates only this local behavior.
- **Revoke Google access:** explicitly invoke Google authorization revocation after warning the user about all affected features. The current method/button is a stub.
- **Delete cloud backups:** a separate destructive workflow requiring explicit confirmation; never bundle it with disconnect or revocation.

Account email may be useful as a key today, but future identity should prefer an immutable Google account identifier while treating email as mutable display data.

Active-work protection is durable: WorkManager is authoritative after Activity/process recreation. The in-memory `GmailBackupSession` is only a worker-local collision guard and is never the sole disconnect decision.

## Device ownership

**Implemented:** The local Device Profile belongs to the installation and is shared consistently across every connected Gmail account. Each account caches its own Gmail device-label ID, while all accounts use the same immutable device ID and editable friendly metadata. Full manually entered phone numbers remain local; Gmail labels and ordinary Settings display use only the final five digits.
