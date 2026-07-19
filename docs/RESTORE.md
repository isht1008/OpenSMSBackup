# Restore

## Status

**Implemented:** Sprint 4A provides a preview-first Android 12+ SMS Restore MVP. A connected Gmail profile discovers bounded `SMS/Devices/*/Conversations` namespaces, validates format-v3 attachments, and selects the newest valid device-owned snapshot per conversation. Users select and search exact conversations before any Android SMS-role request occurs.

Restore insertion is separated behind `RestoreEngine` and `SmsRestoreWriter`. The engine orders messages chronologically, indexes the local provider once, recognizes V1 and country-aware V2 duplicate aliases, continues after individual insertion failures, and records structured partial progress. Restore plans are stored only in private app storage; WorkManager input/progress never contains SMS content. The worker verifies the SMS role again before reading or inserting and provides foreground progress and cancellation where notification permission is available.

**Partially implemented:** Gmail discovery is deliberately bounded to 500 messages per device namespace and probes ten messages per device label. Very large namespaces need paginated catalog indexing later. Cancellation preserves completed inserts and checkpoint counts but cannot roll them back. Users must manually return their preferred messaging app to the SMS role after restore. Physical-device validation remains required on the Samsung S21 FE.

**Deferred:** MMS, call-log restore, old-Android compatibility, cross-device merging, archive history, and rollback.

## Safety contract

Restore never modifies Gmail source data, never deletes or edits existing local SMS, never fabricates thread IDs, and does not insert unless the SMS role is held. Test on an expendable fixture dataset and the Samsung S21 FE. Do not uninstall while evaluating migration behavior.
