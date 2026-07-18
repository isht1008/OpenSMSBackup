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

Restore is deferred until multi-account behavior, stable identity, index rebuild, retry/resume, complete manual backup, automation, Drive state, large-data behavior, encryption, export surfaces, and health reporting are stable.

## Current compromises

- Android `threadId` is used as conversation identity.
- Account ID is normalized Gmail email; only one active email is stored in DataStore.
- Gmail backup UI uploads at most three changed conversations for safety/testing.
- Local JSON is format 2; Gmail conversation attachments are format 3. Compatibility is not yet unified.
- `gmail.modify` is required because labels are created and old snapshots are moved to Trash.

## Planned decisions requiring design review

- Stable identity canonicalization for phone numbers, short codes, group/edge cases, and thread splits/merges.
- Drive storage location, exported database envelope, encryption and key recovery.
- Archive/mirror reconciliation rules after reinstall or multi-device use.
- Large snapshot segmentation without breaking the one-conversation logical model.
