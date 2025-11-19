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


## Settings & Folder Picker (M7)
- Added SAF-based folder picker, DataStore persistence, and DocumentTree storage backing.
- Settings screen includes manual sync and feedback via snackbar.
- `LocalFolderSync` reuses saved folder and triggers repository reindex.

## Tests
- Added coverage for parser, storage (including DocumentFile), repository, and viewmodels.
- Robolectric-based tests verify settings sync and Room-backed indexing.
