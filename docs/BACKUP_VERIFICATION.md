# Backup verification

## Status

**Implemented:** Sprint 4C verifies that current local SMS messages are represented in the selected Gmail account and current device namespace. Gmail is the source of truth; Room stores history but is never accepted as proof of remote content.

Verification compares V1 legacy and V2 country-aware fingerprint aliases rather than trusting counts, Android row IDs, contact names, raw address formatting, or timestamps alone. Each remote record can be consumed once. Sender IDs remain conservative exact identities: `AX-HDFCBK`, `VM-HDFCBK`, and `HDFCBK` are separate.

Mirror and Archive Append-Only verification paginate the current device Conversations label and validate account, device header, label ownership, identity version, and conversation key. For each conversation identity, the newest valid cumulative snapshot is authoritative under the current protocol. V2 and V3 device identities are supported. Verification never repairs, relabels, deletes, or uploads Gmail data.

The engine builds an alias-to-record index and a V2 canonical fingerprint multiset. An archived duplicate is each record beyond the first with the same V2 canonical identity; multiple aliases for one record are not duplicates. Unconsumed remote records are unexpected, which can represent deleted local SMS and is not automatically corruption.

Comparison is approximately O(local + archived messages). Gmail uses page tokens. A configurable 100,000-message safety bound prevents unbounded work; reaching it can never return `VERIFIED`.

- `VERIFIED`: all local messages match with no extras, duplicates, unreadable archives, critical identity errors, or incomplete scan.
- `PARTIALLY_VERIFIED`: all local messages match, but differences or incomplete checks exist.
- `FAILED`: current local messages are missing.
- `NOT_AVAILABLE`: no valid verifiable archive scope is available.
- `CANCELLED`: cooperative cancellation stopped verification.

Results contain counts and a short summary, never SMS bodies. WorkManager data contains only scalar identifiers, stages, and counts. Verification is unique per profile/device, cancellable, and persisted in Room schema v5.

Verification confirms that current local SMS messages are represented in the selected Gmail archive scope at verification time. It does not guarantee future Gmail availability, future decryptability, or successful migration by another tool. Restore remains outside backup-only v1.

## Backup Health dashboard

**Implemented:** Backup History now opens a Material 3 Backup Health dashboard. It keeps local JSON backup completion separate from Gmail verification history, shows the latest archive assessment, aggregate statistics, an integrity trend, status badges, filtering, search, empty/loading states, and a dedicated verification-detail route. History is streamed newest-first from Room and rendered with `LazyColumn`; filtering and statistics use an immutable platform-independent calculation layer.

Local backup files do not currently persist start time, duration, Gmail account, mode, or device metadata, so the Latest Backup card labels those fields as unavailable or local rather than inferring them. Verification duration is presented explicitly as verification duration and is never described as backup runtime.
