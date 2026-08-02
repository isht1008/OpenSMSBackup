# Durable Full Mirror Preview pause checkpoint

Checkpoint created: 2026-08-02 21:12:49 +05:30

## Repository state at checkpoint preparation

- Original branch: `feature/full-mirror-sync`
- Starting HEAD: `df30a6374841de1776aac76da26fbd93e1e1406a`
- Checkpoint branch: `wip/full-mirror-preview-recovery-checkpoint`
- Status: **Implemented with automated validation; physical task-removal recovery validation is still Planned.**

## Implemented work

- Full Mirror Preview runs as unique profile/device-scoped WorkManager work and remains Gmail read-only.
- Room v10 stores separate non-executable preview scan/checkpoint state with an explicit 9-to-10 migration and exported schema.
- Cache-first Gmail metadata validation avoids full snapshot downloads only when immutable ownership, identity, thread, label, message-ID, format, cached hash, and current local hash all agree.
- Metadata scheduling, full reads, decoded payload retention, retry/backoff, and network circuit behavior are bounded.
- Preview uses explicit durable stages and never reuses the local JSON backup label.
- Executable reconciliation PREVIEW publication remains atomic and occurs only after complete revalidation and conservation checks.
- Cold application startup reconciles resumable Room scans with active, terminal, or missing WorkSpecs and continues with the same scan ID at most once.
- Task/process interruption is distinguished from durable explicit cancellation. App and notification cancellation persist `CANCELLED` before cancelling WorkManager.
- Diagnostics use hashed work/scan scope plus bounded aggregate progress and safe error categories; they do not log identifiers or content.

Account A remains Archive-only and excluded from Full Mirror Preview. Account B remains Mirror-only and is the sole eligible Full Mirror Preview account.

## Automated validation

The checkpoint implementation passed:

- `:app:testDebugUnitTest`: 305 tests, zero failures, errors, or skips.
- `:app:compileDebugAndroidTestKotlin`.
- `:app:assembleDebug`.
- `:app:assembleRelease`.
- `git diff --check` and staged-diff integrity checks.

The recovery suite directly exercises the production recovery policy/engine for missing, cancelled, failed, active, and ambiguous WorkSpecs; idempotent continuation; original scan/checkpoint preservation; explicit cancellation; expiry/binding rejection; published-run reuse; recovery UI text; and privacy-safe diagnostic digests.

## Known limitations and pending physical validation

- The uninterrupted cache-first Preview baseline completed physically, but the first task-removal test failed because the old build lacked cold-start Room/WorkManager reconciliation.
- The startup-reconciliation correction has automated coverage but has not yet passed a physical task-removal/process-recovery retest. It must not be described as production-validated.
- Controlled network-pause/resume testing must wait until task-removal recovery passes physically.
- No corrected APK has been installed as part of this checkpoint preparation.

The exact next recommended test is an in-place debug upgrade followed by one read-only Account B Preview. During an advancing `Checking Gmail snapshot metadata` stage, swipe the app from Recents once without force-stop, leave the foreground notification untouched, wait 30 seconds, reopen from the launcher without tapping Preview again, and prove the same scan/checkpoints continue to exactly one atomically published confirmation dialog. Verify monotonic counters, no duplicate completed reads, zero Gmail uploads/Trash/permanent deletion, and zero Account A operations.

Do not automatically run any Gmail operation after resuming development. Do not enter a Full Mirror confirmation phrase or execute Full Mirror without a fresh complete Preview, a renewed safety review, and explicit user action.

## Safe resume notes

After fetching the checkpoint branch, confirm the commit and clean worktree before doing anything else:

```powershell
git switch wip/full-mirror-preview-recovery-checkpoint
git status --short --branch
git rev-parse HEAD
```

Then read `AGENTS.md`, `PROJECT_CONTEXT.md`, `DESIGN_DECISIONS.md`, `docs/FULL_MIRROR.md`, `docs/GMAIL_BACKUP.md`, and `docs/TESTING.md`. The next activity is physical task-removal recovery preparation and observation only; it is not new feature development and must not automatically start Preview or Gmail work.
