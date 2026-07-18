# Restore

## Status: deferred

No SMS restore implementation exists. The home button only reports that restore is unavailable; the navigation route is unused. The Gmail JSON attachment is restore-oriented, but producing JSON does not establish a safe restore pipeline. The README’s restore/IMAP bullets describe direction, not current capability.

Restore must not begin until the backup platform is stable: multi-account profiles, complete Gmail Backup Now, Drive state/database export, disconnect/revocation, archive/mirror policy, stable identity, Gmail index reconstruction, retry/resume, scheduled and incoming-SMS backup, large-conversation support, encryption, export/share features, health reporting, migrations, and reinstall recovery.

## Future restore design gate

After prerequisites pass, design version compatibility, integrity/decryption checks, source/account selection, preview and counts, Android default-SMS-app constraints, duplicate detection, thread/participant mapping, subscription/type fidelity, partial failure checkpoints, cancellation, and rollback/recovery. Test on an expendable fixture dataset and the Samsung S21 FE without uninstalling during Room migration tests. Never overwrite or delete source cloud backups as a side effect of restore.
