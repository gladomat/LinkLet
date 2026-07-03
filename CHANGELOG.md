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

## Epic 4: In‑Note Search Experience

- **Implemented** a stateful, roll‑out search panel under the note header with query field, clear/close controls, case‑sensitive/whole‑word/regex toggles, match counter, and next/previous navigation.
- **Added** a block‑based `NoteSearchEngine` service that searches over Org blocks using literal, whole‑word, or regex semantics while keeping indices local to each block.
- **Introduced** `NoteSearchState` in `NoteViewViewModel` with debounced query updates, wrap‑around navigation, regex error surfacing, and `SavedStateHandle` backing for state restoration.
- **Refactored** Org parsing/rendering into `data/parser/org` so both the renderer and search engine share the same `OrgDocument`/`OrgBlock` structures and “display text” representation.
- **Updated** Org renderers to apply non‑overlapping highlight spans for all matches and a distinct style for the active match, including headings, paragraphs, tables and block content.
- **Switched** the note body to a `LazyColumn` with measured top‑bar height–aware `animateScrollToItem` so the active match is scrolled into view below the header and search panel.
- **Added** focused unit tests for the search engine, Org formatter display text, and ViewModel search behavior (debounce, options, navigation wrap‑around, and error handling).

## Epic 5: Section-Heading Links & Sync Attachment Discovery

- **Fixed** links inside section headings (`* Heading with [[id:target][alias]] link`) rendering as raw, unclickable markup — `SectionHeaderRow` built its title from a bare `AnnotatedString` instead of the shared Org inline-link builder; it now routes through `buildOrgContentAnnotatedString` and dispatches taps via `ClickableText`, same as body text.
- **Fixed** the WebDAV remote crawler never descending into extensionless directories (`images/`, org-attach id folders) — directory traversal was gated through the same extension allowlist used for files, so any attachment one level below the vault root was invisible to sync. Split `SyncPathFilter` into `shouldInclude` (files) and `isDirectoryTraversable` (name-rules only, no extension gate).
- **Removed** the file-extension allowlist entirely — it contradicted the original attachment-sync design decision ("sync all files") and silently dropped any attachment type outside a hardcoded 9-extension list. All files under the vault root sync by default now.
- **Added** optional user-supplied `.syncignore` (gitignore-lite syntax) for vault-root excludes beyond the built-in junk blocklist, loaded fresh at the start of every sync run. Shipped a ready-to-copy default at `docs/templates/syncignore-default.txt`.
