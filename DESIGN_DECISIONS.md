# Design decisions

## Accepted

### Conversation snapshots in Gmail

One Gmail message is the latest snapshot of one Android SMS conversation, combining sent and received messages. This replaces the older per-SMS approach found in Git history. The email must remain human-readable through HTML and plain-text alternatives and must carry versioned restore JSON.

### Transaction ordering across Gmail and Room

For a changed conversation: compute the hash, upload the new message, store its Gmail IDs/hash in Room, then move the previous message to Trash. An upload failure leaves the old snapshot and Room row intact. A Trash failure leaves both Gmail copies, retains the new row, and reports a warning. Permanent deletion is not part of this flow.

### Incremental state

Room v2 keys current snapshots by `(account_id, android_thread_id)` and stores a render-version-sensitive SHA-256 hash. This is implemented but not portable identity; a stable cross-device identity is planned.

### Archive before mirror

Archive is the recommended default: preserve remote backups when device messages disappear. Mirror is optional and must never silently infer deletion intent. It requires an explicit mode choice, dry-run/preview, deletion thresholds, reauthentication/confirmation for large changes, retry safety, and recovery guidance.

### Restore gate

Sprint 4A authorizes a bounded Android 12+ SMS-only Restore MVP. Gmail discovery and parsing remain separate from provider insertion. Preview precedes the SMS-role request; the worker verifies the role again, uses V1/V2 duplicate aliases, never supplies thread IDs, and preserves partial structured results. MMS, call logs, rollback, old-Android compatibility, and unbounded catalogs remain deferred.

### Durable manual Gmail execution

Manual Gmail backup is unique WorkManager work named by immutable profile ID. The worker, not `HomeViewModel`, owns the long-running backup and foreground lifetime. WorkManager progress/output are the durable UI source; the notification and app cancel the exact work ID. Current UI permits one global active Gmail backup, while names/tags remain profile-scoped for future account automation.

Sprint 2B retries remain operation-local. The worker returns typed logical aborts with `Result.success`, infrastructure/input failures with `Result.failure`, and never uses `Result.retry`: restarting the entire worker could duplicate a Gmail insertion whose response was lost.

### Installation-scoped device namespaces

Every installation owns a random UUID Device Profile stored independently of Gmail accounts. Gmail labels are readable aliases; device headers and V2 conversation identity are authoritative. Legacy V1 archives may be continued only through a matching pre-upgrade Room cache. A foreign or unowned legacy snapshot is never automatically claimed, merged, replaced, or moved to Trash.

## Current compromises

- Android `threadId` is used as conversation identity.
- Snapshot account ID remains normalized Gmail email, while account selection and worker ownership use immutable profile IDs.
- Gmail backup UI uploads at most three changed conversations for safety/testing.
- Local JSON is format 2; Gmail conversation attachments are format 3. Compatibility is not yet unified.
- `gmail.modify` is required because labels are created and old snapshots are moved to Trash.

## Planned decisions requiring design review

- Stable identity canonicalization for phone numbers, short codes, group/edge cases, and thread splits/merges.
- Drive storage location, exported database envelope, encryption and key recovery.
- Archive/mirror reconciliation rules after reinstall or multi-device use.
- Large snapshot segmentation without breaking the one-conversation logical model.
