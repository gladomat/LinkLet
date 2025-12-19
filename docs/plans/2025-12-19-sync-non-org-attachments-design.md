# Sync non-org attachments (design)

Created: 2025-12-19T17:09:31+01:00

## Context
We need to sync all non-Org files that live alongside Org notes under the notes root.
These files are referenced from .org notes (e.g., images, PDFs) and should be synced
along with notes. Sync must preserve directory structure, keep existing guardrails
(delete thresholds, conflict behavior), and avoid syncing transient/system folders.

## Decision
Unify sync discovery across all files under the notes root, apply a shared ignore
filter, and keep existing reconciliation and safety guardrails. This includes
non-org files in both local and remote discovery and uses the same sync engine
pipeline for uploads, downloads, deletes, and conflicts.

## Goals
- Sync all files under the notes root (org and non-org).
- Preserve directory structure.
- Exclude ignored paths by default (system files and any folder starting with
  '.' or '_').
- Keep guardrails and conflict behavior consistent with current sync.
- Add unit tests for discovery filtering and binary handling.

## Non-goals
- Remote HTTP(s) media fetching or link-only syncing.
- Content-type specific handling beyond treating non-org as binary.
- UI changes.

## Approach
### Discovery
- Add `IStorage.listFiles()` to return all file paths relative to root.
- In `SyncEngine`, replace `listNotes()` with `listFiles()` and filter paths
  through a shared `SyncPathFilter`:
  - Ignore any path segment that starts with '.' or '_'.
  - Ignore specific files: `.DS_Store`, `Thumbs.db`, `desktop.ini`, `__MACOSX`, `.git`, `.trash`.
  - Continue to exclude `_trash_bin` explicitly (already present).
- Build `localFiles` using hashes:
  - `.org` files: hash UTF-8 text.
  - Non-org: hash raw bytes.

### Remote listing (WebDAV)
- Update `WebDavRemoteSyncProvider.listRemoteNotes()` to return all files, not
  just `.org`.
- Implement recursive listing so files in subfolders are discovered.
- Apply the same `SyncPathFilter` to remote paths.

### Transfer
- Extend storage with binary IO:
  - `readFileBytes(path)` / `writeFileBytes(path, bytes)`
- In `SyncEngine.performUpload`:
  - Read bytes for non-org, text for `.org`.
  - Upload bytes as `application/octet-stream`.
- In `SyncEngine.performDownload`:
  - Write text for `.org`.
  - Write bytes for non-org.
- Conflict copies should preserve binary data for non-org paths.

## Error handling
- Keep existing misconfiguration guard (empty local files but existing sync state).
- Surface read/write errors explicitly via `Result` failures.

## Testing
- `SyncPathFilter` unit tests for ignore rules, including dot/underscore folders.
- `FileStorageImpl` unit tests for:
  - `listFiles()` includes non-org files.
  - `readFileBytes`/`writeFileBytes` round-trip.
- Sync discovery tests (lightweight) for filtering logic, using pure functions.

## Alternatives considered
1) Split note vs attachment discovery: more code paths and higher divergence risk.
2) Allowlist by extension: misses user requirement to sync all files.

## Open questions
- None; proceed with unified discovery and default ignore rules.
