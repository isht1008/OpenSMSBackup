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
- Gmail scope: full Archive selection, deterministic Recent-10 selection, legacy and invalid WorkManager input, full/partial/limited completion, timestamp advancement, Full Mirror pre-enqueue and manager blocking, duplicate rejection, exact-ID cancellation, and omitted-conversation protection.
- Gmail Archive index: pagination, metadata-only reads, ownership filtering, deterministic V3/V2 selection, bounded memory/concurrency, shared backoff, cancellation, 404 skipping, zero per-conversation Full searches, and upload-Room-index ordering.
- Scale: empty inbox, null bodies/addresses, Unicode, duplicate timestamps, multiple SIMs, thousands of messages, Gmail size/rate limits.
- Physical Samsung S21 FE: runtime permissions, contact denial, account selection/consent, local MediaStore file/history, Gmail rendering/attachment, incremental rerun, replacement/Trash warning, rotation/background/process death, and in-place Room migration.
- Incremental performance: verify v1-to-v2 local checkpoint upgrade, stable hashes across Locale/TimeZone/input order, zero Gmail setup/reads/index/uploads for an all-match run, cached-ID-first changed reads, bounded retry/throttle behavior, phase-aware ETA, and unambiguous terminal counters.

Before handoff run `git status`, inspect `git diff`, run the relevant build/tests, list files changed, and identify remaining device tests. Never uninstall during migration testing and never use personal SMS/account data as fixtures.
## Legacy checkpoint bootstrap

**Implemented:** Unit coverage verifies exact merged-hash bootstrap, Full timestamp coverage, missing proof/ID rejection, snapshot-newer-than-proof rejection, account/installation rejection, deletion/addition/equal-count replacement rejection, and a 3,314-row all-local request budget. Physical testing must confirm aggregate gmail_legacy_bootstrap diagnostics and zero Gmail request counters without exposing identities.

## Incremental Archive physical validation

**Implemented:** Physical validation passed on the primary Samsung test device. Legacy initialization safely established 3,238 local checkpoints while uncertain rows continued through remote comparison. An unchanged 3,330-conversation / 34,950-message run then completed in approximately 17 seconds with all conversations locally unchanged and zero Gmail reads, searches, index builds, uploads, recoveries, retries, or failures. Two subsequent messages from two different numbers correctly changed two conversations: the run used two cached remote comparisons and two persisted uploads without search, recovery, index construction, retry, throttle delay, or failure, completing in approximately 14 seconds. Its immediate unchanged follow-up completed locally in approximately 10 seconds with zero Gmail operations. The compact terminal UI showed one masked, user-facing result with expandable details and no duplicate status or invalid terminal ETA.

## Full Mirror validation

**Implemented automated coverage:** preview classification, incomplete/empty scans, ambiguous identities, duplicate/foreign ownership rejection, Account A/B key isolation, expiry and typed confirmation, high-risk Trash threshold, remote fingerprint staleness, upload/persist/Trash ordering, ambiguous upload non-repeat, Trash-only resume, ownership-failure stop, scalar immutable WorkManager data, account masking, and Room 7-to-8 migration compilation.

**Planned physical dual-account test:**

1. Configure Account A as Archive and Account B as Mirror; record both masked identities and independent policies.
2. Select Account B and request Full Mirror Preview. Verify the preview names masked Account B once and says Archive account is not affected.
3. Before confirmation, switch selection to Account A. Confirm that execution remains bound to Account B or is rejected safely; never allow Account A credentials or labels to substitute.
4. Inspect preview counts. Resolve every conflict/failure. If Trash is proposed, enter exactly `MIRROR <count>`.
5. Confirm once. Observe progress/cancel without changing account policy, disconnecting, or starting another Gmail operation.
6. Verify Account B replacements are uploaded and persisted before prior owned Account B snapshots move to Trash; remote-only Account B snapshots move only after ownership validation.
7. Verify Account A Archive messages, device labels, checkpoints, history, policy, and connection remain unchanged.
8. Cancel a separate controlled run at a safe boundary, reopen the app, and resume. Verify completed uploads are not repeated and a prior Trash warning resumes Trash only.
9. Verify foreign, other-device, malformed, ambiguous-address, and unreadable snapshots are not changed.
10. Capture only aggregate `OpenSMSBackup` diagnostics; never record account identities, message contents, tokens, hashes, or full Gmail IDs.

Do not automate this test because confirmation is intentionally user initiated and Gmail-mutating.

## Mirror-thread preview regression coverage

**Implemented:** tests cover exact conservation at 3,330 conversations, a supported 10,000-conversation plan, explicit non-truncating 10,001 limit blocking, short-code/alphanumeric/non-normalizable senders, invalid and duplicate threads, duplicate remote identities, cross-account/device separation, Archive V3 preservation, thirteen cached legacy replacements, uncached legacy conflict, blocked confirmation hiding, safe typed confirmation, scalar WorkManager data, and upload/persist/Trash ordering.

For a physical preview, capture the aggregate `full_mirror_preview` diagnostic after the user manually creates the preview. Compare `local` with `local_classified` and `remote_candidates` with `remote_classified`; both pairs must match. Review nonzero reason categories before any confirmation.
