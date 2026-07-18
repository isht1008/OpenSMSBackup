# Gmail backup

## Status

**Implemented:** The app reads all device SMS, groups them by Android `threadId`, orders messages chronologically, and builds one Gmail email per conversation. Each format-v3 email contains readable HTML bubbles, a plain-text fallback, custom `X-OpenSMSBackup-*` headers, and `opensms-conversation-<threadId>.json` with restore-oriented message fields. It ensures `SMS` and child labels and uploads with `SMS` plus `SMS/Conversations`.

Room v2 stores `(account_id, android_thread_id)`, snapshot hash, Gmail message/thread IDs, range, count, and backup time. Unchanged hashes are skipped. For changes, the new snapshot uploads first, Room is updated second, and the previous Gmail message is moved to Trash last. Trash failure is a warning and never rolls back the valid new snapshot.

**Partially implemented:** The home-screen action is explicitly a test capped at three changed conversations. Progress and up to 20 failures/warnings are displayed, but jobs are not durable, resumable, or scheduled. Existing per-message MIME/fingerprint/database code remains from the earlier design but is not the active manager path. Labels for inbox/sent/drafts/failed are created but unused by conversation upload.

**Planned:** Full Backup Now, stable identity, per-account settings, retry/resume, large-conversation handling, Gmail index reconstruction, archive/mirror reconciliation, health reporting, and encryption where appropriate.

## Current attachment shape

The root contains `formatVersion: 3`, `backupType`, account email, snapshot hash, creation time, and a conversation object. Messages include Android ID/thread ID, address/contact, body, timestamp/formatted date, raw type and enum name, subscription ID, read state, and service center. Treat it as sensitive personal data and version it compatibly.

## Known constraints

- Android thread IDs are not stable across devices/reinstalls.
- Contact matching is exact-number lookup; normalization is not implemented.
- Gmail API needs `gmail.modify` for label creation and Trash operations.
- Remote index search/recovery is absent; losing Room state causes re-upload rather than reconciliation.
- Do not permanently delete Gmail messages without explicit confirmation.
