# Project context

## Product boundary

OpenSMSBackup is an Android 12+ privacy-first SMS backup app. The current code reads the system SMS provider, optionally resolves contacts, creates local JSON exports, and can upload incremental Gmail conversation snapshots. Restore and IMAP claims in the original README are aspirations, not current features.

## Current implementation

**Implemented**

- Kotlin/Compose single-activity app; min SDK 31, target SDK 36, application version 0.3.0.
- Runtime `READ_SMS`/optional `READ_CONTACTS` permission flow.
- Local format-v2 JSON export to `Documents/OpenSMSBackup` through MediaStore, plus a simple backup-history list.
- Multiple Room-backed Google account profiles with selected profile ID in DataStore and profile-aware Gmail `gmail.modify` access.
- Gmail format-v3 snapshot generation: one email per Android `threadId`, chronological sent/received content, HTML and plain text, custom headers, and restore JSON attachment.
- Room database v3 with account profiles/settings plus legacy backup and conversation-snapshot tables; explicit migrations exist.
- Incremental Gmail comparison using a SHA-256 snapshot hash. Replacement is uploaded and persisted before the previous message is moved to Trash.
- Manual Gmail backup executes as unique profile-bound WorkManager foreground work; WorkInfo restores progress across Activity/process recreation and both app/notification cancellation target the exact request.

**Partially implemented**

- Gmail backup remains a manual test action capped at three changed conversations. Execution is durable, but exact per-conversation resume after worker interruption and remote deduplication are absent.
- Multiple accounts can be managed, selected, reauthorized, and logically disconnected. Independent per-account backup configuration and actual OAuth revocation are absent.
- Gmail labels are created, but conversation uploads use only `SMS` and `SMS/Conversations`.
- Backup history reads local JSON files, but cards have no details/share action and local backups contain a separate schema from Gmail attachments.
- Stable identity uses account email plus Android `threadId`; that ID is not portable across devices/reinstalls.

**Planned**

Multi-account profiles, Gmail index reconstruction, stable identity, full Backup Now, retry/resume, scheduled and incoming-SMS backups, Drive state/database export, archive/mirror policy, HTML/share/email export, large-conversation handling, encryption, access revocation, and a backup-health dashboard.

**Deferred**

SMS restore. Do not start it until the backup platform and its prerequisite features are stable.

## Key code areas

- `sms/`, `contact/`, `conversation/`: device data ingestion and local grouping.
- `backup/`, `json/`, `file/`: local JSON backup and history.
- `gmail/backup`, `gmail/mime`, `gmail/upload`: snapshots, MIME, upload, and replacement.
- `database/`: Room entities/DAOs/migration; schemas are exported under `app/schemas`.
- `viewmodel/`, `ui/`, `navigation/`: manual flows and status UI.

See `ROADMAP.md`, `DESIGN_DECISIONS.md`, and the topic documents under `docs/` before implementation work.
