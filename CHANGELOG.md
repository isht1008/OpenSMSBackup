# Changelog

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
