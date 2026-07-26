# Security and privacy

## Current data handling

The app requests `READ_SMS`, optional `READ_CONTACTS`, and Internet. Local JSON is plaintext in `Documents/OpenSMSBackup`. Gmail emails and JSON attachments are readable plaintext in the user’s mailbox. Room stores account emails, addresses, metadata, hashes, and remote IDs without app-level encryption. Google access uses the broad `gmail.modify` scope needed for labels and Trash. Encryption is not implemented.

## Rules

- Never expose SMS bodies, phone/address/contact data, OAuth tokens/secrets, Google account data, PAN information, or other personal data in logs, analytics, crash text, screenshots, fixtures, or support output.
- Never modify/delete `.git` or `local.properties`; never commit secrets.
- Never delete Gmail or Drive data without explicit confirmation. Preserve upload-before-Trash ordering.
- Keep disconnect, OAuth revocation, and cloud-data deletion separate and clearly described.
- Request the least privilege needed and explain why. Contacts denial must still permit address-only backup.
- Sanitize MIME headers/filenames and escape HTML. Treat exception messages from providers as potentially sensitive before display/logging.

## Planned hardening

Versioned encryption for local exports, Drive state, and optional shared exports; secure key generation/storage and recovery; token/account lifecycle review; explicit Auto Backup include/exclude rules; redacted structured diagnostics; dependency/security review; threat modeling for multi-account and mirror deletion; and retention controls. Encryption design must address key loss and cross-device recovery before it is enabled.

## Google account exit controls

**Implemented:** Logical disconnect and OAuth revocation are separate confirmed actions. Logical disconnect clears Credential Manager's app session and local selection/state without withdrawing Google's OAuth grant. Revocation uses Google Identity Services for the selected account and the same `gmail.modify` scope used for authorization; after revocation succeeds the app clears credential-session state, clears selection, and leaves the retained profile authorization-required. Both actions are blocked while that profile owns active backup or verification work.

Neither action calls Gmail message or label mutation APIs. Existing Gmail backups remain user-owned and unchanged, and local profile/settings/history rows are retained. Deleting cloud backups is not implemented by these controls.

Credential Manager sign-in identifies the Google account; it is not proof of Gmail authorization. The app does not persist OAuth access tokens or authorization results. Gmail service creation requires a connected profile, and reconnect must complete account-specific `gmail.modify` authorization first. Google controls whether that authorization presents visible consent UI.
