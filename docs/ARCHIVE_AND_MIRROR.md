# Archive and mirror modes

## Status

**Implemented:** Snapshot replacement moves only a superseded Gmail snapshot to Trash after its replacement uploads. This is version replacement, not device-deletion mirroring.

**Implemented:** Archive and Mirror strategies and a selected-account management card. Policy changes run through a confirmation wizard, affect only future backups, and retain the previous policy and change time locally. Archive is the default for newly created account settings; Mirror is presented as advanced.

**Partially implemented:** Mirror uses the established safe snapshot replacement behavior. Broader deletion previews, thresholds, and reconciliation safeguards remain planned.

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
