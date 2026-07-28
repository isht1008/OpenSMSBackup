# Roadmap

## Incremental Archive checkpoint

**Implemented:** Room v7 local-source checkpoints, bulk local classification, cached-message-first comparison, lazy bounded shared-index recovery, separate incremental/reconciliation UI actions, and count/timing-only diagnostics.

**Planned:** Durable remote index persistence and exact-resume/run journals.

Status is relative to the conversation-snapshot branch. Each stage depends on the stages above it.

## Stage 0 — Current foundation (implemented/partial)

- **Implemented:** SMS/contact reads, local JSON export/history, Gmail auth foundation, conversation MIME snapshots, Room v2 hash tracking, safe upload-before-Trash ordering.
- **Implemented:** Multi-account management, bounded Gmail error handling, cancellable WorkManager foreground execution, notification progress/cancel, and lifecycle-safe progress restoration.
- **Partial:** Gmail remains limited to a three-change manual action; exact resume, remote deduplication, and independent account configuration are absent.

## Stage 1 — Identity, policy, and account foundation

Depends on Stage 0.

- Define a stable conversation identity independent of Android `threadId` and migrate Room safely.
- Add multiple Google account profiles with independent destination, contact-name, mode, schedule, and encryption settings.
- Separate disconnect (local removal) from confirmed OAuth revocation.
- Implement archive mode as the recommended default; design guarded mirror mode.

## Stage 2 — Reliable complete Gmail backup

Depends on Stage 1.

- **Implemented:** Full Backup Now processes every local conversation for Archive accounts.
- **Partially implemented:** Full Archive uses a run-scoped Gmail metadata index; physical scale validation and durable resume/index persistence remain pending.
- **Planned:** Full Mirror remains blocked until deletion preview, thresholds, confirmation, and recovery safeguards are implemented.
- Add durable per-conversation retry/resume, idempotency, cancellation, and large-conversation batching/size handling.
- Rebuild the local Gmail index after reinstall by querying labels and OpenSMSBackup headers/attachments.
- Detect remote/local divergence according to archive or mirror policy.

Sprint 3A will add per-account manual backup configuration. Sprint 3B will add per-account periodic WorkManager requests and constraints using the same profile-bound worker contract; neither is implemented in Sprint 2C.

## Stage 3 — Portable app state

Depends on stable schemas and multi-account semantics.

- Export the Room database/application state safely and upload it to Google Drive app data or an agreed private location.
- Add versioning, integrity checks, encryption, account binding, and recovery validation.
- Never treat Android Auto Backup’s current sample rules as the product backup design.

## Stage 4 — Automation and observability

Depends on reliable, resumable backup.

- Scheduled WorkManager backup with network/battery constraints.
- Near-real-time incoming SMS trigger that queues the same durable pipeline.
- Backup health dashboard: last success, backlog, failures, account health, index/state integrity, and recoverable actions.

## Stage 5 — Export surfaces

Depends on stable identity and rendering.

- Standalone HTML conversation export.
- Android Sharesheet export and email-to-recipient workflow with explicit privacy confirmation.
- Validate output and attachment limits on large conversations.

## Stage 6 — Restore (deferred beyond v1)

OpenSMSBackup v1 is backup-only and does not act as the default messaging application or write to the Android SMS provider. Any future restore proposal requires a separate product and safety review after the backup platform is stable.

## Full Mirror synchronization

**Implemented:** production-safe preview, confirmation, immutable Account B binding, bounded recovery, Room journal, recoverable Trash ordering, cancellation/resume, progress UI, and automated regression coverage. **Deferred:** permanent deletion, automatic conflict resolution, and concurrent multi-account Gmail mutations.

## Full Mirror identity compatibility

**Implemented:** Mirror-thread identity, sender-type support, classification conservation, bounded 10,000-item plans, aggregate reason diagnostics, blocked-preview controls, and controlled cached-legacy replacement. **Planned:** second physical read-only preview and aggregate reason review before any Full Mirror execution.
