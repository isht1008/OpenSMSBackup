# Full Mirror synchronization

Status: **Implemented** and physically validated in production on `feature/full-mirror-sync`.

Full Mirror is a preview-confirm-execute workflow for one immutable Gmail profile. A preview captures `runId`, `profileId`, normalized account identity, installation/device ID, cached device-label ID, expected Mirror policy, local and remote fingerprints, expiry, counts, and per-conversation actions. Preview creation is read-only. Execution is permitted only after the persisted plan is confirmed and every binding is revalidated.

## Account and device isolation

Account A (Archive) and Account B (Mirror) remain independent. Full Mirror uses only Account B's captured `profileId`, credential, policy, device label, Room rows, work tags, and reconciliation journal. The currently selected account is never used as durable ownership after preview creation. Snapshot uniqueness and DAO access are profile/account scoped. Foreign, Account A, other-account, other-device, malformed, duplicate, ambiguous-address, unreadable, and ownership-invalid snapshots are ignored or block execution; they are never claimed or changed.

Only the exact Account B device-label namespace is indexed, with a 100,000-owned-snapshot bound. Gmail messages are moved to recoverable Trash only; permanent deletion is not implemented. Archive behavior and Archive checkpoints are unchanged.

## Safety flow

1. Read SMS completely and verify provider counts are stable.
2. Read and ownership-validate the bounded Account B/device namespace.
3. Classify unchanged, upload-new, replace-changed, remote-only Trash, cache recovery, conflict, and failure.
4. Persist a 15-minute preview and immutable per-item journal.
5. Show counts, estimates, warnings, masked Account B, and an explicit statement that Archive is unaffected.
6. Require typed `MIRROR <trash-count>` when Trash moves are proposed.
7. Revalidate profile, account, device, policy, label, local fingerprint, and remote state before mutation and binding before each item. Initial execution uses the approved pre-execution fingerprint; resume reconciles only changes proven by the same journal and rejects unexplained additions, removals, duplicates, or scoped errors.
8. Execute upload -> persist -> exact old-target re-fetch -> immutable ownership revalidation -> recoverable Trash for replacements. The persisted replacement must match the current profile-scoped Room row, while the superseded target is authorized independently by proof captured before upload. Remote-only items require ownership validation before Trash. Never Trash after upload or persistence failure.

A cancellation stops before the next mutation boundary. Completed journal items are skipped on resume. A replacement whose upload acceptance is ambiguous is not uploaded automatically again. A Trash failure after a persisted replacement is resumable as Trash-only and never re-uploads the replacement. An exact owned old target already in Trash completes idempotently without another Trash request; missing, ambiguous, foreign, or ownership-invalid targets remain blocked. Ownership failures stop later destructive actions.

## Persistence and recovery

Room version 8 added explicit 7-to-8 migration, profile-scoped snapshot ownership, reconciliation runs, and per-item journals. Room version 9 adds immutable superseded-target proof to each applicable journal item and explicitly migrates existing version-8 replacement warnings without rewriting the plan or completed work. Room version 10 adds separate read-only preview scan sessions plus local and remote scalar checkpoint items. No destructive migration fallback is used.

**Implemented (automated validation):** Preview scanning is unique profile/device-bound WorkManager dataSync work. On cold process startup, an application-scope reconciler queries eligible Room scans without relying on HomeViewModel or the old WorkSpec being observable. Matching active work is observed; terminal or missing old work receives one `KEEP` continuation using the original scan ID. Completed local indexing, metadata batches, page cursor, validated cached items, completed full reads, retry state, and aggregate counters remain intact.

Task removal/process death is treated as resumable interruption, not explicit cancellation. App and foreground-notification Cancel first persist durable `CANCELLED`, then cancel WorkManager. Expired, explicitly cancelled, failed, published, ambiguous, or immutable-binding-mismatched scans are not auto-resumed. Interruption before atomic publication leaves no executable plan; interruption after publication returns the one existing reconciliation PREVIEW run. The corrected task-removal behavior requires another physical test before it can be marked physically validated.

A scan checkpoint is never a reconciliation run and cannot be confirmed. After binding, local fingerprint, remote generation, required-read terminal state, and conservation checks succeed, one Room transaction creates a distinct existing-format reconciliation PREVIEW run/items and marks the scan published. Typed confirmation remains hidden until that transaction succeeds.

Scan tables and WorkManager data contain scalar hashes, counts, lifecycle state, app-private opaque Gmail IDs/cursor, and safe categories only. They contain no SMS/Gmail bodies, address/contact fields, account email copies, tokens, authorization codes, or credentials.

**Implemented:** Current Mirror metadata requests thread ID and snapshot hash. A full attachment is skipped only for an exact unique Room-cached unchanged candidate with complete profile/account/device/label/thread/key/hash/format proof. Legacy, missing, malformed, mismatched, duplicate, changed, remote-only, destructive, or ambiguous candidates require the existing full-document validation. Execution still re-fetches and revalidates every destructive old target.

Metadata scheduling is fixed at four active requests without page-sized queued coroutines. Full snapshot reads are sequential. After one Gmail operation exhausts its bounded transport retries, preview stops starting unrelated reads, checkpoints Waiting for network, and uses durable WorkManager retry/backoff. It does not convert the rest of the mailbox into unreadable items.

Changing a profile's policy invalidates only that profile's pending previews. Active Full Mirror work blocks other Gmail mutations and profile disconnect/revoke/policy changes for that same profile. The implementation deliberately serializes Gmail mutation work across profiles to prevent credential/context overlap.

## Limitations

- **Implemented:** preview, typed confirmation, bounded account/device indexing, execution journal, cancellation, safe resume, aggregate progress, and recoverable Trash.
- **Partially implemented:** duration estimates are action-based and become available after completed samples; network variability can make them approximate.
- **Deferred:** permanent deletion, foreign-snapshot adoption, conflict auto-resolution, and concurrent cross-account Gmail mutations.
- **Implemented:** production physical dual-account validation using the procedure in `docs/TESTING.md`.
- **Planned:** verification history should mask the account address currently shown in its historical result UI. This is a presentation/privacy issue and did not affect Full Mirror ownership or execution.
## Mirror thread identity and legacy compatibility

**Implemented:** `mirror-thread-v1` is the only identity written by new Full Mirror and Recent-10 Mirror uploads. The opaque key includes immutable profile/account/device ownership and Android thread ID. It does not expose raw thread ID, address, contact, or account data. Same threads across accounts/devices differ; different thread IDs differ.

Existing owned Mirror snapshots using identity version 2 or 3 are not claimed by address or hash. They become controlled replacement candidates only when the Account B profile-scoped Room cache points to that exact Gmail message and the document validates account, device, label, legacy identity, and thread correspondence. The replacement follows upload -> persist -> ownership revalidation -> recoverable Trash. Ambiguous or uncached legacy snapshots block execution.

**Implemented:** the preview separately conserves local and remote classifications and supports 10,000 candidates per side. Larger datasets receive an explicit `LIMIT_EXCEEDED` block with no truncation. Blocked previews hide typed confirmation and show only Close plus aggregate reasons.

## Durable preview physical validation

**Planned:** reinstall in place and repeat task-removal continuation with recovery diagnostics, proving the same scan/checkpoints and one continuation; only after that passes, run controlled network pause/resume. Room v10 migration and the uninterrupted cache-first baseline have physical evidence, but task-removal recovery does not yet have a passing physical result.

## Production physical validation

**Implemented:** The first production plan classified 3,332 local conversations as 9 unchanged, 3,311 new uploads, and 12 replacements, with no remote-only Trash candidates, conflicts, unreadable items, or scan errors. An initial foreground-service start failed before execution because the worker had not explicitly declared the `dataSync` foreground type. The failed plan remained terminal and non-executable and performed zero Gmail uploads, zero Trash moves, and zero Account A operations. The corrected build explicitly used `FOREGROUND_SERVICE_TYPE_DATA_SYNC` and passed in-place installation and startup checks.

The first complete execution uploaded and persisted all 3,311 new snapshots and all 12 replacement snapshots. One replacement then completed its recoverable Trash step. The other 11 produced deterministic post-persistence ownership warnings because the old predicate incorrectly required the mutable Room cache pointer to remain on the superseded Gmail message after persistence had correctly advanced it to the replacement. Audit proved that all 12 replacements matched Room, the remaining 11 old targets were intact and had not received a Trash request, there were no duplicate or ambiguous uploads, and Account A had no reads or mutations.

The correction separates the immutable proof for the superseded target from the current Room pointer for the replacement. Resume first reconciles journal-attributable remote changes, validates all persisted replacements, re-fetches each exact old target, revalidates its immutable account/device/label/thread/identity/hash proof, checks cancellation, and performs only recoverable Trash cleanup. The physical safe resume ran once as WorkManager attempt 1 with `DATA_SYNC`, attempted zero uploads, independently ownership-validated 11 old targets, completed 11 recoverable Trash moves, and required no reschedule.

Final production result: 3,332 of 3,332 journal items completed; 9 unchanged; 3,311 new uploads; 12 replacements; 3,323 distinct persisted Gmail IDs; 12 superseded snapshots in recoverable Trash; zero remote-only Trash moves; zero duplicates, warnings, failures, remaining items, Account A operations, or permanent deletions. The app process remained stable with no fatal, foreground-service, Room, SQLite, or schema errors. Aggregate diagnostic evidence is stored outside Git and intentionally contains no repository source or committed private data. Production Full Mirror physical execution is complete.
