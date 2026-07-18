# Changelog

## Sprint 2A — Cancellable Gmail Backup and Responsive Home UI

- Added explicit, idempotent cancellation for an active Gmail conversation backup.
- Added cooperative cancellation checks between conversations and preserved `CancellationException` through Gmail result wrappers.
- Added a distinct cancellation summary containing only completed conversation counts.
- Kept Settings and Backup History available while Gmail backup is running, while blocking conflicting backup and restore actions.
- Prevented disconnecting the profile owned by an active Gmail backup; changing the selected profile does not retarget that backup.
- Cancellation can wait for an in-flight synchronous Gmail request to return.
- Process-death survival, foreground execution, retry, and resume remain unsupported until Sprint 2C.

## Sprint 1A — Multi-Account Data Layer

- Added the multi-account Room schema.
- Added `AccountProfileEntity` and `AccountSettingsEntity`.
- Added profile/settings DAO and repository operations.
- Added selected-profile persistence and migration from the legacy selected email.
- Added the explicit Room migration from version 2 to version 3.
- Preserved the existing Gmail account API during the data-layer transition.

## Sprint 1B — Multi-Account Authentication Layer

- Added `GmailServiceFactory` for profile-specific Gmail services.
- Added profile-aware Gmail authorization and credential creation.
- Bound authorization requests to the intended Android Google account.
- Added explicit connected, disconnected, and authorization-required profile states.
- Preserved the existing email-based Gmail backup API for backward compatibility.
