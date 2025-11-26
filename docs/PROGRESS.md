# Project Progress Summary

## Project Setup (M0)
- Scaffolded Android project with Gradle, Kotlin 1.9, Compose, and Hilt.
- Added baseline CI workflow and supporting tooling scripts.

## File Listing & Viewing (M1-M2)
- Implemented filesystem storage, regex parser, note repository, and Compose list/view screens.
- Added navigation from list → view with link handling.

## Backlink Index & Reactive Foundations (M3-M5)
- Introduced Room database for notes/links with repository `reindex()` and backlink queries.
- Exposed Flow-based note stream; viewmodels now consume live updates.
- CI workflow now runs cached test/lint jobs separately.
- Note view now handles backlink/link taps inside the same screen so backlinks are fully clickable.
- Parser + repository now read `:ID:` properties, resolve `[[id:...]]` links, and show those links/backlinks as tappable entries.
- Org-style rendering landed: headings collapse/expand, metadata card toggles, inline Org links and plain URLs are clickable, the new theme matches Emacs colors, and a home button now appears so you can jump back to the list.
- Org-style rendering landed: headings collapse/expand, metadata card toggles, inline Org links and plain URLs are clickable, and the new theme matches Emacs colors.

## Editor & Save Flow (M4)
- Built NoteEdit screen with formatting helpers, save behavior, and reindex integration.
- Formatting toolbar operates on selections (bold/italic/src/heading), and the editor now offers a Cancel action beside Save.
- Keyboard-aware editor: IME insets now keep the note body visible while a floating toolbar shows inline Org formatting actions (headings, bold/italic, lists, indent/outdent, source block) plus a new undo button, all rendered as icon-only touch targets with ripple feedback instead of rounded text buttons.

## Global Search (M6)
- Note list now includes a persistent search bar with a clear (`×`) action; queries are applied live as you type.
- Search matches against note titles, full text bodies, and `#+filetags:` declarations, returning a filtered note list.
- Each matching note shows a short body snippet around the first hit so you can see the context of the match directly in the list.

## Sync Roadmap (Upcoming)
- Sync engine groundwork is in place (Room-backed hashes + pending actions) and the WebDAV/Nextcloud provider now issues PROPFIND/GET/PUT/DELETE requests with Basic/digest auth hand-offs, secure credentials, and post-upload ETag verification.
- Dedicated WebDAV settings screen lets users enter the base URL, folder path, credentials, enablement toggle, and run a dispatcher-backed “Test connection” probe; manual sync from Settings now orchestrates the provider via `SyncEngine` and reports standardized “Sync failed” messages with logs.
- Connectivity plumbing was hardened: INTERNET permission is declared, OkHttp + dispatcher bindings are provided through Hilt, and both manual sync and connection tests emit actionable snackbar/log output when something goes wrong.
- Dropbox integration remains outstanding (REST API + token storage).
- Future work: WorkManager jobs for background sync + conflict UI once Dropbox lands.


## Settings & Folder Picker (M7)
- Added SAF-based folder picker, DataStore persistence, and DocumentTree storage backing.
- Settings screen includes manual sync and feedback via snackbar.
- `LocalFolderSync` reuses saved folder and triggers repository reindex.

## Tests
- Added coverage for parser, storage (including DocumentFile), repository, and viewmodels.
- Robolectric-based tests verify settings sync and Room-backed indexing.
