# Archive and mirror modes

## Status

**Implemented:** Snapshot replacement moves only a superseded Gmail snapshot to Trash after its replacement uploads. This is version replacement, not device-deletion mirroring.

**Planned:** Archive and mirror policies. No mode setting or reconciliation engine exists today.

## Archive — recommended default

Device deletion does not delete an already backed-up Gmail/Drive conversation. Changed conversations get a new snapshot using the safe replacement sequence. Archive maximizes recovery and is the default for normal users.

## Mirror — optional and guarded

Mirror may reflect confirmed device deletions remotely, but only after stable identity and a trustworthy rebuilt index exist. Required safeguards:

- explicit opt-in with a plain-language data-loss warning;
- dry-run preview listing counts and scope without exposing content in logs;
- deletion-rate/count thresholds and automatic stop;
- fresh confirmation (and reauthentication for high-impact changes);
- no deletion when device SMS access, index health, account binding, or sync state is uncertain;
- Trash/recoverable operations before permanent deletion, with a retention/recovery explanation;
- idempotent resumable actions and a health/audit summary.

Never delete Gmail or Drive data merely because a local query returns zero rows or Room state is missing.
