# Multiple Google accounts

## Status

**Implemented:** Gmail operations accept an email, Room rows are account-scoped, and `backup_accounts` can technically hold several records.

**Partially implemented:** Preferences DataStore stores only one `backup_gmail_account`; UI connects, displays, changes, or locally disconnects that single account. The backup manager normalizes email as account ID and marks newly observed accounts default without a complete default-selection policy. Independent configuration is absent.

**Planned:** A profile list where every Google account owns separate Gmail/Drive destinations, mode, label policy, contact-name preference, schedule, encryption settings, checkpoints, health, and retry queue. Switching profiles must not reuse credentials, labels, hashes, or remote IDs from another profile.

## Account removal semantics

- **Disconnect:** stop jobs, clear local active credentials/selection as appropriate, and preserve remote Gmail/Drive data. Current `clearAccount()` approximates only this local behavior.
- **Revoke Google access:** explicitly invoke Google authorization revocation after warning the user about all affected features. The current method/button is a stub.
- **Delete cloud backups:** a separate destructive workflow requiring explicit confirmation; never bundle it with disconnect or revocation.

Account email may be useful as a key today, but future identity should prefer an immutable Google account identifier while treating email as mutable display data.
