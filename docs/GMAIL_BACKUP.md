# Gmail backup

## Status

**Implemented:** The app reads all device SMS, groups them by Android `threadId`, orders messages chronologically, and builds one Gmail email per conversation. Each format-v3 email contains readable HTML bubbles, a plain-text fallback, custom `X-OpenSMSBackup-*` headers, and `opensms-conversation-<threadId>.json` with restore-oriented message fields. It ensures `SMS` and child labels and uploads with `SMS` plus `SMS/Conversations`.

Room v2 stores `(account_id, android_thread_id)`, snapshot hash, Gmail message/thread IDs, range, count, and backup time. Unchanged hashes are skipped. For changes, the new snapshot uploads first, Room is updated second, and the previous Gmail message is moved to Trash last. Trash failure is a warning and never rolls back the valid new snapshot.

**Partially implemented:** The home-screen action runs durably as foreground WorkManager work, but exact checkpoint resume and recurring scheduling are absent. `GmailBackupTestConfig` temporarily limits the upload scope to the 10 local SMS conversations with the newest message activity. Selection sorts by each conversation's latest local message timestamp descending, then thread ID descending for deterministic ties. Set the maximum to `null` to restore full behavior.

**Implemented:** Before manual Gmail backup work is enqueued, the app performs a read-only Gmail `users.getProfile("me")` connectivity check for the selected account. A failed check blocks the backup and surfaces the existing authorization/error flow. The preflight does not list or fetch messages and does not mutate Gmail.

**Temporary test safety:** SMS reading and conversation construction still process the complete Android dataset. The recent-activity selector is applied once immediately before Gmail strategy execution. Progress and message totals use only the selected scope, while completion retains source totals and is classified `LIMITED_TEST_COMPLETED`. Limited runs do not update full-backup timestamps. Archive, Mirror, and verification receive the same 10-most-recent scope. Mirror performs replacement only for those explicitly supplied conversations; there is no global missing-conversation reconciliation, so omitted conversations are not inspected, uploaded, replaced, or moved to Trash. Archive remains append-only. Limited verification can return at most `PARTIALLY_VERIFIED`, never full-dataset `VERIFIED`.

**Planned:** Full Backup Now, stable identity, per-account settings, retry/resume, large-conversation handling, Gmail index reconstruction, archive/mirror reconciliation, health reporting, and encryption where appropriate.

## Current attachment shape

The root contains `formatVersion: 3`, `backupType`, account email, snapshot hash, creation time, and a conversation object. Messages include Android ID/thread ID, address/contact, body, timestamp/formatted date, raw type and enum name, subscription ID, read state, and service center. Treat it as sensitive personal data and version it compatibly.

## Known constraints

- Android thread IDs are not stable across devices/reinstalls.
- Contact matching is exact-number lookup; normalization is not implemented.
- Gmail API needs `gmail.modify` for label creation and Trash operations.
- Remote index search/recovery is absent; losing Room state causes re-upload rather than reconciliation.
- Do not permanently delete Gmail messages without explicit confirmation.

## Sprint 2B reliability behavior

**Implemented:** Gmail failures are classified as authorization, configuration, rate limit, network, server, client, local, or unknown failures. Classification retains the original exception internally plus available HTTP status, Google reason, retryability, abort, reauthorization, and bounded Retry-After information. Normal UI messages are concise and do not expose stack traces or message content.

**Implemented:** Account sign-in and Gmail authorization are independent. Gmail service creation and worker preflight require a connected profile. Revoked profiles remain authorization-required until an account-specific Google Identity authorization result includes `gmail.modify`; an unresolved authorization `PendingIntent` is launched rather than treated as success. Google decides whether user-visible consent is necessary.

Authorization failures such as HTTP 401 and missing Gmail permission stop immediately. The active profile is preserved and marked `AUTHORIZATION_REQUIRED`; temporary network, server, and throttling failures do not change its connected state. Ambiguous HTTP 403 responses stop conservatively, while explicit rate-limit reasons are treated as temporary.

Retries use at most three attempts with cancellable exponential delays of roughly one and two seconds plus modest jitter. A server Retry-After value is honored up to ten seconds. Read-only label listing and move-to-Trash are retried because those operations are safe to repeat. Label creation is not retried because a lost response could create a duplicate label. Gmail message insertion is also not retried: until remote snapshot lookup/index reconstruction exists, a response lost after Gmail accepts an insert could create a duplicate conversation email.

Fatal failures abort before the next conversation. Other matching failures trip an early-abort circuit after five consecutive conversations; a successful upload or unchanged conversation resets it. Checked/failed counts include only attempted conversations, while unattempted conversations are reported as remaining. Existing successful uploads and Room snapshots are retained.

Sprint 2A cancellation remains distinct from failure. Cancellation bypasses classification and counters, interrupts retry delay, and stops before the next operation after any synchronous Gmail request returns. Settings and Backup History remain accessible during requests and retries.

**Partially implemented:** Retries are operation-local and in-memory. The manual action remains safety-capped, and upload idempotency depends on Room state plus the existing upload-before-Trash ordering.

## Sprint 2C WorkManager execution

**Implemented:** The selected profile ID is captured in a small immutable work input with a request UUID, creation time, `MANUAL`/future `SCHEDULED` execution mode, contact-name flag, and current safety limit. The worker reloads that exact profile and never resolves the later selected account. Unique names use `gmail-backup-profile-<profileId>`; tags distinguish all Gmail work, manual work, and profile ownership. The current UI conservatively permits one global active Gmail backup.

`GmailBackupWorker` promotes itself to a `dataSync` foreground worker before lengthy processing when notifications are available. The low-importance `OpenSMSBackup – Gmail Backup` channel shows account, phase, conversation counts, result counts, progress, and an exact-work WorkManager Cancel action. Android 13+ notification permission is requested by the Home UI; denial is explained to the user, skips foreground notification initialization, and does not prevent or retry the Gmail backup.

Centralized WorkManager Data contracts carry only small scalar input/progress/output values—never tokens, SMS content, or MIME. Progress is conversation-level. Terminal output maps completed, classified abort, and failed-before-start outcomes. Logical classified aborts use `Result.success`; invalid input/missing profile or unexpected worker infrastructure failures use `Result.failure`; cancellation becomes WorkManager `CANCELLED`. `Result.retry` is intentionally unused because restarting the full backup could duplicate a Gmail insertion.

`HomeViewModel` observes tagged `WorkInfo`, choosing active work first and otherwise the most recent terminal item returned by WorkManager. This restores progress, owner email, cancellation availability, and terminal state after Activity/process recreation. Settings uses WorkManager as authoritative active-profile protection. Foreground notification removal is handled by WorkManager when execution ends; no extra terminal notification is emitted, avoiding notification spam.

Upload remains single-attempt because remote deduplication/index reconstruction is not implemented. WorkManager durability does not provide exact per-conversation resume if the worker is interrupted, and force-stop prevents Android background work until the app is started again. Reboot behavior follows WorkManager persistence, but recurring work is not configured.

**Planned:** Sprint 3A per-account manual configuration and Sprint 3B per-account periodic work/constraints will reuse this worker and distinguish manual from scheduled tags/input. No recurring schedule exists today.

### Sprint 2C device verification

On the primary device, verify background progress after minimizing, swiping recents, reopening, and Activity rotation; cancel separately from the notification and app, including during a Sprint 2B retry delay; confirm no next conversation starts after cancellation. Keep Settings/History usable, reject disconnect of the active profile, and verify selecting another profile cannot retarget work. Attempt duplicate starts and confirm one request. Exercise offline, mid-run network loss, revoked authorization, temporary failure, and a successful three-conversation run while checking profile state, bounded abort, snapshot safety, and duplicate Gmail messages. Verify progress/terminal notification cleanup with notification permission granted and denied. Record force-stop behavior (Android suppresses work until the app is started again) and reboot behavior without claiming recurring scheduling or exact resume.

## Sprint 3A backup strategy foundation

**Implemented:** Per-profile backup mode persistence and centralized strategy selection. Mirror mode owns the existing per-conversation upload, Room snapshot update, and previous-snapshot Trash sequence. Strategy instances are constructed once per backup run.

**Implemented:** `ARCHIVE_APPEND_ONLY` reads the latest Room-referenced Gmail format-v3 attachment, fingerprints archived and on-device messages using normalized address, direction, timestamp, and body, and uploads a chronologically merged snapshot only when new messages exist. Messages deleted from the phone remain archived, and prior archive snapshots are not moved to Trash. Gmail reads reuse the existing bounded retry and error-classification path.

**Implemented:** Archive discovery treats Room's Gmail message ID as a cache. It validates that message first, then falls back to a bounded `SMS/Conversations` label search and selects the newest valid format-v3 snapshot matching normalized conversation and account identity. Valid recovery repairs the Room cache. New snapshots include a deterministic `X-OpenSMSBackup-Conversation-Key`; older format-v3 snapshots remain discoverable through label-scoped attachment validation.

**Implemented:** Home exposes a selected-account management card with persisted policy, connection state, last backup, and verification health. Policy changes require a confirmation wizard, affect future backups only, and store the previous policy/change time locally. Archive is the default for newly created settings and Mirror is an advanced option. Existing stored modes are retained.

**Partially implemented:** Full remote index reconstruction remains planned.

## Sprint 3C device namespaces

**Implemented:** Each installation owns a UUID-backed `DeviceProfile` in a dedicated DataStore. The UUID is random, uses no hardware identifier, survives normal upgrades, and can change after app-data deletion or reinstall. New Gmail snapshots carry device ID/name plus archive identity version 2 headers. V2 conversation keys include normalized account, device ID, and normalized address.

Both mirror and archive uploads use `SMS/Devices/<sanitized device display>/Conversations`. The per-account Gmail label ID is cached locally and remains authoritative across friendly-name edits; the existing label is renamed in place after the new parent path is confirmed. Old empty parent labels are retained rather than deleted. Label cleanup and large migrations are deferred.

Archive search is restricted to the current device label and rejects another device ID. Legacy V1 snapshots are readable only through an existing matching Room cache; label search never allows a new device to claim an unowned V1 archive. Mirror replacement validates V2 account/device/conversation/label ownership before Trash, so legacy or foreign-device messages are preserved.

**Partially implemented:** Device marketing names use Android's available manufacturer/model metadata; no external marketing-name catalog is bundled. Reinstall/app-data deletion starts a new device identity. Archive History and cross-device merge remain deferred.

## Sprint 3D country-aware identity

**Implemented:** Installation Device Profiles now retain a validated ISO 3166-1 alpha-2 default region, initially derived from the device locale with a US fallback. Settings permits editing this value without changing the installation UUID. The app uses Google's official libphonenumber library to classify valid telephone numbers and canonicalize them to E.164. Alphanumeric sender IDs, short codes, and malformed/empty values remain distinct deterministic identities; no phone, SIM, location, or contacts permission is used for country selection.

Message fingerprints are explicitly versioned. V1 preserves the previous byte-for-byte algorithm. New comparisons also calculate V2 from the country-aware address, direction, timestamp, and body. Archive merging stores both aliases in a hash set, so messages represented by legacy V1 semantics are not appended again solely because the fingerprint version changed. New format-v3 attachments remain format-v3 and add optional `fingerprintVersion` and `defaultRegion` metadata plus equivalent MIME headers; older parsers can ignore these additive fields.

New snapshots use archive identity V3: normalized account, installation device ID, and country-aware address. Discovery searches V3 first, then the existing device-scoped V2 key in the same device label. Legacy V1 remains limited to the pre-existing Room-linked cached-message compatibility rule, so a second device cannot claim it. Existing V1/V2 identities and format-v3 attachments are not rewritten or invalidated.
