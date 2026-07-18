# Automation and sync

## Status

**Implemented:** Manual local backup and a manual Gmail test backup with in-process progress.

**Partially implemented:** Incremental hashes skip unchanged conversations and Room stores last backup/sync timestamps, but there is no durable queue, checkpoint, retry policy, or reconciliation.

**Planned:**

- Backup Now for all changed conversations.
- WorkManager scheduled backup with unique per-account work, network constraints, backoff, cancellation, and persisted progress.
- Near-real-time incoming SMS handling that enqueues work; it must not run a parallel ad-hoc backup pipeline.
- Retry/resume at conversation/chunk granularity with idempotent uploads.
- Gmail index rebuild after reinstall and Drive state recovery.
- Health dashboard showing last success, pending/failed work, authorization, state/index integrity, and actionable recovery.

Automation depends on stable identity, multi-account configuration, archive/mirror policy, and large-conversation behavior. Work must serialize per account, tolerate process death, and avoid logging message/account content. Permission or auth failures should pause safely and request user action rather than spin.
