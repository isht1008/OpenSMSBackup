# OpenSMSBackup contributor instructions

OpenSMSBackup is a privacy-first Android SMS backup application written in Kotlin with Jetpack Compose. The package is `io.github.isht1008.opensmsbackup`, minimum SDK is 31, and the primary physical test device is a Samsung S21 FE.

## Non-negotiable safety

- Never modify or delete `.git` or `local.properties`.
- Never commit or push unless the user explicitly requests it.
- Never use destructive Git operations, including `git reset --hard`, `git clean -fd`, force-push, or destructive checkout commands.
- Never uninstall the app while testing Room migrations. Upgrade the installed app in place.
- Never delete Gmail or Google Drive data without explicit confirmation. Moving an obsolete Gmail snapshot to Trash is allowed only in the established, user-initiated snapshot replacement flow and only after the replacement upload succeeds.
- Never log SMS bodies, addresses/contact details, OAuth tokens or secrets, Google account data, PAN information, or other personal data. Avoid including these values in user-visible diagnostics as well.
- Preserve existing behavior unless an approved design replaces it. Use small, reviewable changes.
- Inspect models, DAOs, migrations, schemas, and all call sites before changing persistence. Add explicit Room migrations; never use destructive fallback.
- When requirements are ambiguous, ask for clarification instead of making architectural assumptions.

## Current architecture contract

- One Gmail email represents one Android SMS conversation; sent and received messages are combined.
- Every email has readable HTML, a plain-text fallback, and an attached restore-oriented JSON snapshot.
- Room stores account records and conversation snapshot hashes for incremental comparison.
- Upload the new snapshot first. Only after success may the previous Gmail snapshot be moved to Trash. A Trash failure must preserve the new Room state and surface a warning.
- Archive mode is the recommended default. Mirror mode is optional and must have deletion safeguards, previews, limits, confirmation, and recovery guidance.
- Restore is deferred until the backup platform and all prerequisite backup features are stable.

## Status vocabulary

Documentation and plans must label work as **Implemented**, **Partially implemented**, **Planned**, or **Deferred**. Do not infer completion from the presence of a model, DAO, placeholder UI, or dependency.

## AI workflow

Before making code changes:

1. Read `AGENTS.md`.
2. Read `PROJECT_CONTEXT.md`.
3. Read `DESIGN_DECISIONS.md`.
4. Read the relevant document under `docs/`.
5. Inspect the existing implementation.
6. Implement only the requested feature.
7. Build the project.
8. Show `git diff`.
9. Do not commit unless requested.

## Branches

- `main`: initial SMS reading/contact/conversation baseline.
- `develop`: local JSON backup, history, and UI development line.
- `feature/gmail-backup`: earlier per-message Gmail implementation.
- `feature/conversation-snapshot`: current conversation-snapshot implementation line.
- `docs/project-context`: documentation line, currently based on the conversation-snapshot tip.

## Build and verification

Preferred command from the repository root:

```powershell
.\gradlew :app:assembleDebug
```

If Java is not found, set `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr` for the shell, or build the `app` debug variant in Android Studio. Before finishing any change: run `git status`, review `git diff`, run the relevant tests and debug build, list changed files, and identify physical-device tests still required. Do not commit.
