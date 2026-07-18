# TODO

Priority order follows dependencies; check items only after code and tests exist.

## Reliability and foundations

- [ ] Remove the three-changed-conversation test limit and provide guarded full Backup Now.
- [ ] Define and migrate to stable conversation identity.
- [ ] Implement durable per-account retry, resume, cancellation, and idempotency.
- [ ] Reconstruct the Room/Gmail snapshot index after reinstall.
- [ ] Add real tests for snapshot hashes, MIME, JSON, Room migration, and replacement failure ordering.
- [ ] Redact failures/status so addresses, SMS content, account data, PAN data, and OAuth information cannot leak.

## Accounts, policy, and cloud state

- [ ] Add multiple account profiles and independent configuration.
- [ ] Implement explicit disconnect and separate confirmed OAuth revocation.
- [ ] Implement archive mode as default.
- [ ] Implement optional mirror mode with preview, thresholds, confirmation, and recovery safeguards.
- [ ] Design and implement encrypted Drive application-state/database export and recovery validation.

## Automation and user features

- [ ] Add WorkManager scheduled backup.
- [ ] Add near-real-time incoming SMS queueing.
- [ ] Handle Gmail/API limits and very large conversations.
- [ ] Add standalone HTML export, Android sharing, and email-to-recipient flow.
- [ ] Add encryption with documented key ownership and loss recovery.
- [ ] Add backup health dashboard.

## Deferred

- [ ] Design and implement SMS restore only after the backup roadmap’s stability gate is met.
