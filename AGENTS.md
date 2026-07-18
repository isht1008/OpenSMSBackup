# OpenSMSBackup Development Instructions

## Project

OpenSMSBackup is a privacy-first Android SMS backup application.

- Language: Kotlin
- UI: Jetpack Compose
- Minimum Android SDK: 31
- Package: io.github.isht1008.opensmsbackup
- Primary test device: Samsung S21 FE
- IDE: Android Studio
- Current branch: feature/conversation-snapshot

## Current Gmail Backup Design

- One Gmail email represents one Android SMS conversation.
- Sent and received messages are stored together.
- Gmail email contains readable HTML and plain-text fallback.
- Restore-ready JSON is attached.
- Previous conversation snapshot is moved to Trash only after the new snapshot uploads successfully.
- Incremental state is tracked using Room and snapshot hashes.
- Archive mode will be the default.
- Mirror mode will be optional and must include deletion safeguards.

## Planned Backup Features Before Restore

1. Multiple Google account profiles with independent settings.
2. Gmail backup per account.
3. Google Drive application-state/database backup.
4. Disconnect and actual Google access revocation.
5. Archive and mirror modes.
6. Stable conversation identity.
7. Gmail index reconstruction after app reinstall.
8. Retry and resume.
9. Backup Now for all changed conversations.
10. Scheduled WorkManager backup.
11. Near-real-time incoming SMS backup.
12. HTML conversation export.
13. Share/export through Android.
14. Email exported conversation to a recipient.
15. Large-conversation handling.
16. Backup health dashboard.

Do not begin SMS Restore until the backup features above are stable.

## Safety Rules

- Never delete or modify the .git directory.
- Never modify local.properties.
- Never commit or push unless explicitly requested.
- Never run git reset --hard, git clean -fd, force push, or destructive Git commands.
- Never uninstall the Android app during database migration testing.
- Never delete Gmail or Google Drive data without explicit confirmation.
- Do not expose OAuth secrets, API keys, SMS content, PAN data, or personal data in logs.
- Preserve existing functionality unless a requested design explicitly replaces it.
- Make small, reviewable changes.
- Inspect existing models, DAOs, migrations, and call sites before editing.
- Add Room migrations instead of destructive migration.
- Run the debug build after every coherent implementation stage.
- Stop after repeated failures and explain the root cause rather than making speculative large changes.

## Build

Use Android Studio build or:

.\gradlew :app:assembleDebug

JAVA_HOME may need to point to:

C:\Program Files\Android\Android Studio\jbr

## Verification

Before completing a task:

1. Run git status.
2. Review git diff.
3. Run the relevant build.
4. Report modified and added files.
5. Report tests performed and tests that still require a physical device.
6. Do not commit unless explicitly requested.