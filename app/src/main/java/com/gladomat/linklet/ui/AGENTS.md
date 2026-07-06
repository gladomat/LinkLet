# UI Layer (Jetpack Compose)

## Purpose

All visual code: `MainActivity` (single activity, NavHost with 9 routes), screens, reusable components, Material 3 theme. State comes from `viewmodel/` — no repository or storage access from composables.

## Ownership

- `screens/` — one top-level `@Composable` per file (`NoteListScreen`, `note/NoteViewScreen`, `noteedit/`, `settings/`, `sync/`, `trash/`, `graph/`). `noteedit/LinkPickerDialog` is the search-and-insert picker for adding an org-mode link to another note from the edit screen's toolbar. `graph/GraphScreen` is the app's first custom `Canvas`/gesture-drawn screen (note graph view, `docs/plans/2026-07-06-note-graph-view.md`, physics owned by `data/graph/AGENTS.md`) — every other screen is standard Compose layout/Material3 components.
- `components/` — org-mode renderers (`OrgBlockRenderers`, `OrgTextFormatter`, inline images via `UriBitmapLoader`), `BacklinkList`.
- `theme/` — colors, typography, `LinkLetAppTheme`.

## Local Contracts

- Screens that edit a local, device-only config file (e.g. `settings/SyncIgnoreEditorScreen`) must: intercept back navigation while dirty (`BackHandler` + discard-confirmation dialog, mirroring `noteedit/NoteEditScreen`'s pattern), and show a dry-run impact preview before an explicit Save commits — never autosave consequential text.
- `remember` keys for per-note UI state (expansion maps, dialogs) use `note.id.path` — never `note.content`, which changes every keystroke and resets state.
- Drawer blocks (`OrgBlock.Drawer`) are handled upstream in `NoteViewScreen` (rendered as expandable pills); `OrgBlockView` deliberately no-ops on them. Any new caller that renders blocks directly must handle drawers itself or route through the screen-level handling.
- The leading PROPERTIES drawer is metadata (parsed into `section.properties`), not content — it must not render as a visible block.
- Avoid per-recomposition allocations in list items (e.g. `joinToString` without `remember`).
- Any screen building an org-mode link to another note must call `OrgFileUtils.buildNoteLink()` (data/utils) rather than hand-rolling `[[id:...][label]]` / `[[file:...][label]]` syntax.
- Screens must receive a pre-parsed `OrgDocument` from the ViewModel (e.g. `NoteViewUiState.Success.document`) rather than calling `parseOrgDocument(note.content)` themselves inside `remember` — parsing is expensive and must happen once, off the main thread, and be shared across every consumer of the same note.
- `graph/GraphScreen`'s `Canvas` draws every edge as one batched `Path` (single `drawPath` call), never one `drawLine` per edge — at this app's real vault size (500-2000 notes), a naive per-edge draw call is a separate perf cost from the layout engine's own Barnes-Hut fix and needs the same care.

## Verification

`tests/com/gladomat/linklet/ui/` (~20 Robolectric Compose tests). Screens with new interaction get a Compose UI test; renderers get block-level tests in `components/`.
