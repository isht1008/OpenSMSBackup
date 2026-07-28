# Design decisions

## Accepted

### Conversation snapshots in Gmail

One Gmail message is the latest snapshot of one Android SMS conversation, combining sent and received messages. This replaces the older per-SMS approach found in Git history. The email must remain human-readable through HTML and plain-text alternatives and must carry versioned restore JSON.

### Transaction ordering across Gmail and Room

For a changed conversation: compute the hash, upload the new message, store its Gmail IDs/hash in Room, then move the previous message to Trash. An upload failure leaves the old snapshot and Room row intact. A Trash failure leaves both Gmail copies, retains the new row, and reports a warning. Permanent deletion is not part of this flow.

### Incremental state

Room v2 keys current snapshots by `(account_id, android_thread_id)` and stores a render-version-sensitive SHA-256 hash. This is implemented but not portable identity; a stable cross-device identity is planned.

**Implemented:** Room v7 keeps two distinct hashes. `snapshotHash` continues to describe the merged snapshot persisted in Gmail. `localSourceHash` v2 hashes canonically ordered raw source/output inputs: membership, IDs, thread IDs, address/contact output fields, body, raw timestamp, direction/type, subscription, read state, and service center. Locale/time-zone-formatted dates are derived output and are not hashed directly; renderer changes deliberately increment the checkpoint version. Exact v1 matches are upgraded locally in one Room batch without Gmail access. The checkpoint also records the installation Device Profile ID; an account or Device Profile mismatch cannot use the local fast path. A null legacy checkpoint requires one safe comparison.

Archive checkpoints advance only after an already-identical local classification, a successful remote no-op comparison, or successful upload plus Room persistence. Authorization, lookup, parsing, ownership, merge, upload, persistence, and cancellation failures do not advance unfinished checkpoints. Phone deletions therefore trigger one comparison, remain preserved in Gmail, then become locally skippable after a successful no-op.

Routine `INCREMENTAL` Archive work classifies locally before remote access. An all-match run returns before Gmail label, snapshot, search, or index setup. Cached Gmail message IDs are tried first for changed conversations. A bounded shared Gmail archive index is initialized lazily, at most once per run, only when cached recovery is required. Explicit `FULL` reconciliation may build the complete index and remains separate because it can be expensive.

### Archive before mirror

Archive is the recommended default: preserve remote backups when device messages disappear. Mirror is optional and must never silently infer deletion intent. It requires an explicit mode choice, dry-run/preview, deletion thresholds, reauthentication/confirmation for large changes, retry safety, and recovery guidance.

### Restore gate

Restore is outside the backup-only v1 product boundary. The app does not request the Android SMS role, write to the SMS provider, or qualify as a default messaging application. Android and OEM migration tools remain the recommended phone-to-phone migration path without a guarantee of completeness on every device.

### Durable manual Gmail execution

Manual Gmail backup is unique WorkManager work named by immutable profile ID. The worker, not `HomeViewModel`, owns the long-running backup and foreground lifetime. WorkManager progress/output are the durable UI source; the notification and app cancel the exact work ID. Current UI permits one global active Gmail backup, while names/tags remain profile-scoped for future account automation.

Sprint 2B retries remain operation-local. The worker returns typed logical aborts with `Result.success`, infrastructure/input failures with `Result.failure`, and never uses `Result.retry`: restarting the entire worker could duplicate a Gmail insertion whose response was lost.

### Installation-scoped device namespaces

Every installation owns a random UUID Device Profile stored independently of Gmail accounts. Gmail labels are readable aliases; device headers and V2 conversation identity are authoritative. Legacy V1 archives may be continued only through a matching pre-upgrade Room cache. A foreign or unowned legacy snapshot is never automatically claimed, merged, replaced, or moved to Trash.

### Read-only backup verification

Gmail is the verification source of truth. Verification consumes V1/V2 message aliases within account/device-scoped V2/V3 archives; counts alone never establish health. Extra archived messages are reported without automatically calling them corruption, and unreadable or incomplete scope prevents `VERIFIED`. Verification performs no Gmail mutation and stores no SMS content in Room history.

## Current compromises

**Implemented:** A null legacy checkpoint normally requires remote comparison, with one installation-local exception. Bootstrap requires incremental Archive mode, matching account context, a cached Gmail ID, exact current ConversationSnapshotHashGenerator equality with persisted snapshotHash, and snapshot backupTime at or before lastBackupTime. That account timestamp advances only after successful non-aborted zero-failure Full reconciliation. Missing proof, device/profile uncertainty, deletions, additions, and render changes retain remote comparison.

- Android `threadId` is used as conversation identity.
- Snapshot account ID remains normalized Gmail email, while account selection and worker ownership use immutable profile IDs.
- Gmail backup mode is selected per account. Archive is the default for newly created settings; Mirror requires an explicit warning confirmation.
- Local JSON is format 2; Gmail conversation attachments are format 3. Compatibility is not yet unified.
- `gmail.modify` is required because labels are created and old snapshots are moved to Trash.

## Planned decisions requiring design review

- Stable identity canonicalization for phone numbers, short codes, group/edge cases, and thread splits/merges.
- Drive storage location, exported database envelope, encryption and key recovery.
- Archive/mirror reconciliation rules after reinstall or multi-device use.
- Large snapshot segmentation without breaking the one-conversation logical model.

## Full Mirror decision

**Implemented:** destructive Mirror reconciliation requires a read-only persisted preview, immutable profile/account/device/label binding, complete local-SMS proof, bounded owned-namespace indexing, typed Trash confirmation, and revalidation immediately before execution. Replacements use upload -> Room persist -> ownership validation -> recoverable Trash. Account A Archive and Account B Mirror data are never shared. Cross-account Gmail mutations are serialized intentionally.

## Mirror identity is installation-thread scoped

**Implemented:** Mirror identity deliberately differs from Archive identity. Android thread ID is authoritative for current-installation Mirror grouping. `mirror-thread-v1` binds the thread to profile, account, and device before hashing. Address normalization is diagnostic only and cannot make a valid sender unrepresentable. This prevents address-key collisions while ensuring reinstall/device-profile loss cannot claim older snapshots automatically.

**Implemented:** local and remote classification conservation is a safety invariant. Duplicate keys remain explicit conflicts, and exceeding 10,000 supported candidates produces `LIMIT_EXCEEDED` without truncation. A blocked preview exposes no confirmation control.
