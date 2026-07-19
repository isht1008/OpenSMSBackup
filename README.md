# OpenSMS Backup

> A privacy-first Android SMS backup app with readable, incremental Gmail conversation snapshots.

## Features

- SMS Backup
- Gmail (OAuth)
- Incremental Backup
- Multiple Gmail account profiles
- WorkManager foreground backup with progress and cancellation
- Material 3 UI
- No Ads
- No Tracking
- Open Source

## Status

🚧 Under active development

Manual Gmail backup currently runs as unique, profile-bound WorkManager foreground work. Progress survives Activity recreation and is shown in the app and a foreground notification. The notification and Home screen can cancel the exact work request. The current manual safety action remains limited to three changed conversations.

Restore, recurring schedules, IMAP, Drive backup, remote deduplication, and exact per-conversation resume are not implemented yet.

## License

Apache License 2.0
