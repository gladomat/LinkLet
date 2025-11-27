# Changelog

## Epic 1: 3‑Way Merge Implementation

- Implemented a robust edit‑span model (`TextEdit`) to represent changes derived from DiffMatchPatch.
- Fixed bug where pure INSERT edits were ignored by flushing edits when `currentReplacement` is non‑empty.
- Added conflict detection for zero‑length inserts at the same position with differing replacements.
- Updated `applyNonConflictingEdits` to correctly deduplicate identical edits (same range and replacement).
- Added unit‑test‑friendly debug utilities and ensured all merge‑related tests now pass.

## Epic 3: WebDAV Protocol Hardening

- **Created** `RemoteChangedException.kt` extending `IOException` to represent safe‑delete precondition failures.
- **Updated** `WebDavRemoteSyncProvider.delete` to catch `SardineException` with HTTP 412 and throw `RemoteChangedException` instead of a generic `IOException`.
- **Ensured** `listRemoteNotes` uses a depth of `1` for PROPFIND requests to avoid recursive directory listings.
- **Adjusted** implementation plan and task artifacts to reflect completed work.
- **Verified** the project builds successfully after changes.


