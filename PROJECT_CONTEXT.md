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
- Room database v10 with account profiles/settings, policy transition metadata, legacy backup and conversation-snapshot tables, local-source Archive checkpoints, Full Mirror journals with immutable superseded-target proof, separate non-executable Full Mirror Preview scan checkpoints, and count-only verification history; explicit non-destructive migrations exist.
- Incremental Gmail comparison using a SHA-256 snapshot hash. Replacement is uploaded and persisted before the previous message is moved to Trash.
- Routine Archive `INCREMENTAL` work bulk-loads Room checkpoints and skips checkpoint-identical conversations without reading Gmail snapshots or building the remote index. `FULL` is the explicit full reconciliation scope.
- Routine Archive checkpoint v2 hashes raw deterministic source fields rather than locale-formatted dates. Exact v1 matches are upgraded in one local Room batch without Gmail access; null or genuinely mismatched rows still receive a safe remote comparison.
- **Implemented:** Legacy null checkpoints may be initialized locally only for incremental Archive work when a cached Gmail ID exists, the current render-versioned merged snapshot hash exactly equals the persisted snapshotHash, and snapshot backupTime is covered by the account's successful zero-failure Full reconciliation timestamp. The partial-column Room batch completes before counts are reported; uncertain rows retain remote comparison.
- Gmail WorkManager progress reports monotonic elapsed time, phase-aware smoothed ETA, local skips, remote no-op comparisons, persisted uploads, recovery counts, failures, and detailed terminal totals.
- Manual Gmail backup executes as unique profile-bound WorkManager foreground work; WorkInfo restores progress across Activity/process recreation and both app/notification cancellation target the exact request.
- Full Backup Now is implemented for Archive accounts. Immutable WorkManager input distinguishes full runs from the explicit Recent-10 test action. Legacy persisted work without scope retains Recent-10 behavior after an in-place upgrade.
- Installation-scoped Device Profiles isolate mirror and append-only Gmail namespaces using device labels, V2 conversation identity, and device ownership headers without hardware identifiers or phone permissions.
- Country-aware SMS identity uses the installation's editable ISO region, official libphonenumber E.164 canonicalization, dual V1/V2 fingerprint matching, and device-isolated archive identity V3 while retaining controlled V1/V2 discovery compatibility.
- Read-only Gmail verification paginates the current device namespace and compares V1/V2 fingerprint aliases, with count-only health history in Room.

**Partially implemented**

- Gmail backup execution is durable, but exact per-conversation resume and remote deduplication are absent. **Partially implemented:** the explicit Recent-10 action deterministically selects the 10 newest conversations. Omitted conversations remain untouched, including in Mirror mode, and limited runs do not advance full-backup completion time.
- Multiple accounts can be managed, selected, reauthorized, logically disconnected, or explicitly revoked through Google Identity Services. Google credential sign-in and Gmail OAuth authorization are separate state transitions; revoked profiles remain authorization-required until `gmail.modify` authorization completes. Settings displays each profile's own policy, last backup, and verification health.
- Gmail labels are created, but conversation uploads use only `SMS` and `SMS/Conversations`.
- Local backup history and Gmail verification history are presented together in Backup Health; local files still use a separate schema and do not persist backup start/duration metadata.
- Stable identity uses account email plus Android `threadId`; that ID is not portable across devices/reinstalls.

**Planned**

Multi-account automation, durable Gmail index persistence, stable identity, full Mirror backup, exact resume/run journals, scheduled and incoming-SMS backups, Drive state/database export, destructive mirror reconciliation, HTML/share/email export, large-conversation handling, and encryption.

**Deferred**

SMS/MMS/call-log restore and default-messaging-app behavior. OpenSMSBackup v1 is backup-only.

## Key code areas

**Implemented:** Legacy null checkpoints may be initialized locally only for incremental Archive work when a cached Gmail ID exists, the current render-versioned merged snapshot hash exactly equals persisted snapshotHash, and snapshot backupTime is covered by the account's successful zero-failure Full reconciliation timestamp. The partial-column Room batch completes before counts are reported; uncertain rows retain remote comparison.

- `sms/`, `contact/`, `conversation/`: device data ingestion and local grouping.
- `backup/`, `json/`, `file/`: local JSON backup and history.
- `gmail/backup`, `gmail/mime`, `gmail/upload`: snapshots, MIME, upload, and replacement.
- `database/`: Room entities/DAOs/migration; schemas are exported under `app/schemas`.
- `viewmodel/`, `ui/`, `navigation/`: manual flows and status UI.

See `ROADMAP.md`, `DESIGN_DECISIONS.md`, and the topic documents under `docs/` before implementation work.

## Full Mirror synchronization status

**Implemented:** Full Mirror uses a persisted, expiring, immutable preview for one Mirror profile and device. It never derives ownership from the later selected account. Account A Archive state is isolated from Account B Mirror state. See `docs/FULL_MIRROR.md` for the safety ordering, recovery journal, and limitations.

**Implemented:** production physical Full Mirror validation completed a 3,332-conversation plan and its journal-aware Trash-only recovery with all items completed, zero duplicate uploads, zero Account A operations, and zero permanent deletions. Diagnostic evidence is retained outside Git.

**Implemented (automated validation):** Full Mirror Preview runs as unique profile/device-bound WorkManager work. Room v10 persists only privacy-safe local/remote scalar scan state, page progress, retry state, and aggregate counters. Cold application startup reconciles resumable Room scans even if a task-removal WorkSpec is terminal or missing, and continues with the same scan ID. Explicit Cancel is durably distinguished from process interruption. Partial scan IDs are never reconciliation runs and cannot be confirmed. A distinct executable PREVIEW run is published atomically only after complete local/remote consistency and binding revalidation. The corrected task-removal path still requires a physical retest.

**Implemented:** Preview requests Android thread ID and snapshot hash in Gmail metadata. An exact current-format Account B cache/header/local-hash match avoids the full attachment; legacy, missing, mismatched, duplicate, remote-only, changed, or ambiguous candidates retain full-document validation. Metadata scheduling and full reads are bounded, while terminal transient transport failure pauses the whole checkpoint instead of classifying unrelated snapshots as unreadable.

## Mirror thread identity correction

**Implemented:** Full Mirror and Recent-10 Mirror use `mirror-thread-v1`, an opaque SHA-256 identity derived from immutable profile, normalized Mirror account ownership, installation device ID, and Android thread ID. Valid short codes, alphanumeric/service senders, non-normalizable sender strings, and supported multi-recipient address strings no longer depend on phone normalization for identity. Archive V2/V3 identity and incremental Archive checkpoints are unchanged.

**Implemented:** preview classification conserves local and remote candidates separately. Every local conversation and accepted remote candidate contributes exactly once, and invariant checks fail safely if totals do not reconcile. The supported plan limit is 10,000 local and 10,000 accepted remote candidates; over-limit plans remain fully classified but are blocked explicitly as `LIMIT_EXCEEDED`, never truncated or converted to generic conflicts.

**Implemented:** cached legacy Mirror identity-version 2/3 snapshots are migration candidates only when the profile-scoped Room reference matches the Gmail message and account/device/label/thread ownership validates. Migration uses upload -> persist -> ownership validation -> recoverable Trash. Uncached or ambiguous legacy state remains a conflict. Account A Archive data is never considered for this path.
