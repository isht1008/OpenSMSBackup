# Testing

## Current state

Only template tests exist: a `2 + 2` JVM test and an instrumentation package-name assertion. There are no tests for SMS ingestion, MIME/JSON, hashes, Room migration, Gmail ordering, account isolation, or UI workflows. A successful build is necessary but not sufficient.

## Commands

```powershell
.\gradlew :app:assembleDebug
.\gradlew :app:testDebugUnitTest
.\gradlew :app:connectedDebugAndroidTest
```

Use the first two locally; the connected test needs an emulator/device. If Java is unavailable, set `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr` for the shell, or use Android Studio: sync the project, choose the `app` debug variant, then Build > Make Project. Do not edit `local.properties` to solve environment setup.

## Required coverage

- Unit: stable identity, hash determinism/render-version changes, chronological grouping, HTML/header escaping, MIME boundaries/attachments, JSON nulls/types, archive/mirror decisions, and redaction.
- Room: schema 1-to-2 in-place migration with retained rows; future migrations must upgrade an installed app without uninstalling.
- Integration: upload failure keeps old state; successful upload updates Room before old Trash; Trash failure warns and preserves new state; unchanged snapshots skip; account isolation; consent recovery; retry/resume/process death.
- Scale: empty inbox, null bodies/addresses, Unicode, duplicate timestamps, multiple SIMs, thousands of messages, Gmail size/rate limits.
- Physical Samsung S21 FE: runtime permissions, contact denial, account selection/consent, local MediaStore file/history, Gmail rendering/attachment, incremental rerun, replacement/Trash warning, rotation/background/process death, and in-place Room migration.

Before handoff run `git status`, inspect `git diff`, run the relevant build/tests, list files changed, and identify remaining device tests. Never uninstall during migration testing and never use personal SMS/account data as fixtures.
