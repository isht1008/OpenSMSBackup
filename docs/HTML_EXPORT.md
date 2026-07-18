# HTML export and sharing

## Status

**Implemented:** Gmail snapshots contain an inline readable HTML rendition with escaped message/contact values and a plain-text alternative.

**Partially implemented:** The renderer demonstrates the intended conversation appearance, but it is private to Gmail MIME creation. There is no standalone `.html` file, Android Sharesheet, attachment URI, or recipient workflow.

**Planned:** Extract a tested renderer for standalone conversation HTML; export through MediaStore or a scoped `FileProvider`; share through Android; and optionally compose an email to a user-selected recipient. Preserve chronological sent/received messages and include clear provenance/format version.

## Privacy and scale requirements

- Preview the selected conversation and warn that exporting sends plaintext personal data outside the app.
- Never preselect or silently send to a recipient; use explicit user action.
- Escape all HTML, sanitize filenames/headers, grant temporary URI permissions, and avoid world-readable storage.
- Offer plain-text and restore JSON only when deliberately selected.
- Stream or paginate very large conversations and test email/provider attachment limits.
- Encryption for exported artifacts is planned; document recipient/key handling before shipping it.
