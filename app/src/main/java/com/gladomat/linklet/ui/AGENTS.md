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
- Compose's `KeyboardOptions` defaults to *no* capitalization, unlike Android Views. Prose fields (note editor content) must set `KeyboardCapitalization.Sentences` explicitly; search fields and the rename/filename field keep `None` on purpose. The editor also runs `autoCorrect = true`: org markup is inserted via toolbar buttons / paste, not typed out, so the IME has nothing to mangle.
- Avoid per-recomposition allocations in list items (e.g. `joinToString` without `remember`).
- Any screen building an org-mode link to another note must call `OrgFileUtils.buildNoteLink()` (data/utils) rather than hand-rolling `[[id:...][label]]` / `[[file:...][label]]` syntax.
- Screens must receive a pre-parsed `OrgDocument` from the ViewModel (e.g. `NoteViewUiState.Success.document`) rather than calling `parseOrgDocument(note.content)` themselves inside `remember` — parsing is expensive and must happen once, off the main thread, and be shared across every consumer of the same note.
- `graph/GraphScreen`'s `Canvas` draws every edge as one batched `Path` (single `drawPath` call), never one `drawLine` per edge — at this app's real vault size (500-2000 notes), a naive per-edge draw call is a separate perf cost from the layout engine's own Barnes-Hut fix and needs the same care.
- `graph/GraphLod.kt` owns the graph canvas's level-of-detail rules (what is drawn at which zoom, node/edge fade, viewport culling, label collision). It is pure math with no Android imports so it unit-tests on the plain-JVM tier — the `Canvas` block consumes it and must not reimplement any of it inline.
- Nodes are never hidden by zoom. Fully zoomed out the canvas shows the *whole* graph as fine dots — hiding the low-degree periphery removes exactly what gives the graph its silhouette, and the crowding that tempts you to cull is a layout-spacing problem owned by `data/graph/`. Zoom changes node size, edge opacity and which labels fit, nothing else. The only culling is viewport culling, which is invisible by construction.
- The graph camera auto-frames the whole graph until the user pans/zooms/picks a search result (`userControlsCamera`). Node positions are centred on the origin, so a stored pan of `Offset.Zero` would show only the bottom-right quadrant. The auto-fit camera is *derived* during composition, never assigned to `scale`/`panOffset` — writing those during composition feeds a state write back into the recomposition that produced it. The zoom floor is `min(MIN_SCALE, fitScale)`: a fixed floor alone can't guarantee "fully zoomed out shows everything", because the layout's extent grows with the vault.
- An edge draws only when at least one endpoint is on screen (`edgeIsWorthDrawing`), not when the segment merely crosses the viewport. A line whose both ends are off screen joins nothing you can see, and at a real vault's edge count hundreds of them bury the notes under a mesh of diagonals.
- Node labels claim space in a shared `LabelGrid` in importance (degree) order and are dropped, not overdrawn, when they collide. Never draw a label unconditionally per node.

## Verification

`tests/com/gladomat/linklet/ui/` (~20 Robolectric Compose tests). Screens with new interaction get a Compose UI test; renderers get block-level tests in `components/`. `screens/graph/GraphLodTests` is the exception — plain JUnit, no Robolectric, because `GraphLod.kt` is deliberately Android-free.
