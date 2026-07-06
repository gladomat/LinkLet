# UI Layer (Jetpack Compose)

## Purpose

All visual code: `MainActivity` (single activity, NavHost with 9 routes), screens, reusable components, Material 3 theme. State comes from `viewmodel/` — no repository or storage access from composables.

## Ownership

- `screens/` — one top-level `@Composable` per file (`NoteListScreen`, `note/NoteViewScreen`, `noteedit/`, `settings/`, `sync/`, `trash/`).
- `components/` — org-mode renderers (`OrgBlockRenderers`, `OrgTextFormatter`, inline images via `UriBitmapLoader`), `BacklinkList`.
- `theme/` — colors, typography, `LinkLetAppTheme`.

## Local Contracts

- Screens that edit a local, device-only config file (e.g. `settings/SyncIgnoreEditorScreen`) must: intercept back navigation while dirty (`BackHandler` + discard-confirmation dialog, mirroring `noteedit/NoteEditScreen`'s pattern), and show a dry-run impact preview before an explicit Save commits — never autosave consequential text.
- `remember` keys for per-note UI state (expansion maps, dialogs) use `note.id.path` — never `note.content`, which changes every keystroke and resets state.
- Drawer blocks (`OrgBlock.Drawer`) are handled upstream in `NoteViewScreen` (rendered as expandable pills); `OrgBlockView` deliberately no-ops on them. Any new caller that renders blocks directly must handle drawers itself or route through the screen-level handling.
- The leading PROPERTIES drawer is metadata (parsed into `section.properties`), not content — it must not render as a visible block.
- Avoid per-recomposition allocations in list items (e.g. `joinToString` without `remember`).

## Verification

`tests/com/gladomat/linklet/ui/` (~20 Robolectric Compose tests). Screens with new interaction get a Compose UI test; renderers get block-level tests in `components/`.
