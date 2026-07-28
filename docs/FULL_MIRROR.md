# Full Mirror synchronization

Status: **Implemented** on `feature/full-mirror-sync`.

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
7. Revalidate profile, account, device, policy, label, local fingerprint, and remote fingerprint before mutation and binding before each item.
8. Execute upload -> persist -> ownership validation -> Trash for replacements. Remote-only items require ownership validation before Trash. Never Trash after upload or persistence failure.

A cancellation stops before the next mutation boundary. Completed journal items are skipped on resume. A replacement whose upload acceptance is ambiguous is not uploaded automatically again. A Trash failure after a persisted replacement is resumable as Trash-only. Ownership failures stop later destructive actions.

## Persistence and recovery

Room version 8 adds explicit 7-to-8 migration, profile-scoped snapshot ownership, reconciliation runs, and per-item journals. No destructive migration fallback is used. Preview/work state contains scalar metadata only; no SMS content, Gmail content, tokens, full Gmail IDs, or unmasked accounts are placed in progress/output.

Changing a profile's policy invalidates only that profile's pending previews. Active Full Mirror work blocks other Gmail mutations and profile disconnect/revoke/policy changes for that same profile. The implementation deliberately serializes Gmail mutation work across profiles to prevent credential/context overlap.

## Limitations

- **Implemented:** preview, typed confirmation, bounded account/device indexing, execution journal, cancellation, safe resume, aggregate progress, and recoverable Trash.
- **Partially implemented:** duration estimates are action-based and become available after completed samples; network variability can make them approximate.
- **Deferred:** permanent deletion, foreign-snapshot adoption, conflict auto-resolution, and concurrent cross-account Gmail mutations.
- **Planned:** physical dual-account validation using the procedure in `docs/TESTING.md`.
## Mirror thread identity and legacy compatibility

**Implemented:** `mirror-thread-v1` is the only identity written by new Full Mirror and Recent-10 Mirror uploads. The opaque key includes immutable profile/account/device ownership and Android thread ID. It does not expose raw thread ID, address, contact, or account data. Same threads across accounts/devices differ; different thread IDs differ.

Existing owned Mirror snapshots using identity version 2 or 3 are not claimed by address or hash. They become controlled replacement candidates only when the Account B profile-scoped Room cache points to that exact Gmail message and the document validates account, device, label, legacy identity, and thread correspondence. The replacement follows upload -> persist -> ownership revalidation -> recoverable Trash. Ambiguous or uncached legacy snapshots block execution.

**Implemented:** the preview separately conserves local and remote classifications and supports 10,000 candidates per side. Larger datasets receive an explicit `LIMIT_EXCEEDED` block with no truncation. Blocked previews hide typed confirmation and show only Close plus aggregate reasons.
