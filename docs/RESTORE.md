# Restore

## Status

**Deferred:** Restore is intentionally outside the backup-only v1 product boundary.

OpenSMSBackup is a backup and archival application. It does not restore messages into the Android SMS database and does not act as the default messaging application. It never requests the Android SMS role and does not write to the SMS provider.

Android and OEM migration tools remain the recommended path for phone-to-phone message migration. Their behavior and availability vary by platform, manufacturer, device, and software version, so OpenSMSBackup does not guarantee that any external tool will restore every message.

OpenSMSBackup instead focuses on readable, durable, user-owned local and Gmail archives. Telephone-number identity uses country-aware canonicalization when validity can be established. Alphanumeric sender IDs use conservative exact identity: prefixes are meaningful, so `AX-HDFCBK`, `VM-HDFCBK`, and `HDFCBK` remain separate.
