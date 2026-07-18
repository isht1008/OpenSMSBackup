# Coding guidelines

## Scope and style

- Use Kotlin official style, Compose for UI, coroutines for blocking work, and clear package-local responsibilities.
- Prefer small changes that preserve current behavior. Remove obsolete paths only after every call site and migration consequence is understood.
- Keep UI state in ViewModels; perform provider, database, network, and file work off the main thread.
- Use structured results/errors. Do not use `println` or exception text when it may disclose SMS, account, OAuth, PAN, or personal data.

## Backup invariants

- Preserve one Gmail email per conversation, HTML plus plain text, and versioned restore JSON.
- Hash every field that materially changes rendered/restorable output; bump the render version for intentional formatting changes.
- New upload must succeed before Room replacement and old-message Trash. Never permanently delete remote data without explicit confirmation.
- Account-scope every incremental record and job. Make retries idempotent.

## Room

- Inspect entities, DAOs, exported schemas, migrations, and callers before edits.
- Increase the version, add an explicit migration, update exported schemas, and test an in-place upgrade. Never uninstall the app or enable destructive migration fallback during migration testing.
- Do not confuse the legacy `backup_messages` table with the active conversation-snapshot pipeline.

## Testing and completion

- Add focused unit tests for hashes, identity, MIME/JSON escaping, reconciliation, and policy logic; add Room migration and device integration tests where appropriate.
- Build with `.\gradlew :app:assembleDebug` (or Android Studio’s app/debug build). Use Android Studio’s bundled JBR if `JAVA_HOME` is missing.
- Before handoff run `git status`, inspect `git diff`, run relevant tests/build, list changed files, and call out Samsung S21 FE or other physical-device checks.
- Never modify `.git` or `local.properties`; never commit/push or use destructive Git commands unless explicitly authorized (destructive commands remain prohibited by project policy).
