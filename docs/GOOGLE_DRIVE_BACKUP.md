# Google Drive backup

## Status

**Implemented:** Nothing. The project has no Drive dependency, scope, API client, upload worker, encryption envelope, or restore flow. Room’s account entity contains `driveFileId` and `driveRevisionId`, but those unused columns are only preparatory. Android Auto Backup is enabled with sample/default rules; it is not the planned Drive product feature.

**Planned:** Back up application state and a database export per Google account so Gmail index state, configuration, job checkpoints, and schema metadata can be recovered after reinstall. Decide whether to use Drive `appDataFolder` or another private location before adding scopes.

## Required design

- Use a versioned, integrity-checked export rather than copying a live database unsafely.
- Quiesce/transactionally snapshot Room and document WAL handling.
- Encrypt sensitive content before upload; define key ownership, rotation, device loss, and recovery.
- Bind state to the intended Google profile and prevent cross-account merges.
- Retain revisions and verify a download before declaring backup healthy.
- Make disconnect local-only; OAuth revocation is a distinct confirmed action.
- Never delete Drive files/revisions without explicit confirmation and a clear recovery story.

Drive recovery remains planned independently of the backup-only v1 boundary.
