# `.syncignore` in-app editor — plan

## Why

`.syncignore` is currently edit-only-by-hand: user must locate the file on-device (or via desktop
Nextcloud client) and edit it outside the app. This session's real bug (a wanted file synced
because a rule was forgotten) is a config-authoring problem, not an architecture problem — the
fix is to make authoring the rule file easier and safer, in-app.

## Decisions (resolved via grill)

1. **New standalone screen**, not inlined into an existing settings list — a raw multi-line text
   editor needs its own scroll space/keyboard focus.
2. **Entry point**: a row in `WebDavSettingsScreen.kt` (sync-behavior concern, not general app
   settings). Row is **hidden entirely** (not disabled) when no vault/WebDAV is configured yet.
3. **Seed content for a vault with no `.syncignore` yet**: pre-fill the editor with the default
   template (`docs/templates/syncignore-default.txt`), not a blank field.
   - That template must be bundled into the app to be loadable at runtime. It is currently a
     docs-only file. Add a Gradle task that copies
     `docs/templates/syncignore-default.txt` → `app/src/main/assets/syncignore-default.txt` as a
     build step (wired ahead of asset merging) — single source of truth, no hand-duplicated copy
     to drift out of sync.
4. **Save is explicit** (a Save button) — no autosave-while-typing. Editing exclusion rules is
   consequential (a wrong rule silently drops a note from sync); autosave risks committing a
   broken mid-edit line.
5. **On Save**:
   - Parse the text with a new `SyncIgnoreRules.parseVerbose(text)` (see below) to detect
     no-op/dropped lines.
   - Show a **warning banner** listing dropped lines (line number + raw text) if any. This is
     **informational only — never blocks Save**. A dropped line is already inert whether or not
     we tell the user; blocking would add friction for zero safety benefit.
   - Write the raw text via `IStorage.writeFileBytes(".syncignore", bytes)` regardless of
     warnings.
   - On successful write, trigger an immediate sync via the existing fire-and-forget
     `syncScheduler.scheduleManual()` path (same as `SettingsViewModel.triggerSync()` /
     `requestManualSync()`) — otherwise Save appears to do nothing until the next periodic sync.
   - **Stay on the editor screen** after a successful save (don't auto pop back) — show a
     "Saved · sync scheduled" snackbar. Supports iterative rule-tuning (add a rule, save, add
     another) without round-tripping through navigation each time.
   - On write failure (SAF permission revoked, IO error, disk full): show an error snackbar,
     **keep the user's edited text in the field** so nothing is lost, let them retry.
6. **Read-only "Also always excluded" info card**, collapsible, above the text field — static
   text listing the built-in blocklist that applies regardless of `.syncignore` content: dot/
   underscore-prefixed path segments, `.DS_Store`, `Thumbs.db`, `desktop.ini`, `__MACOSX`, `.git`,
   `.trash`, `ltximg/` (from `SyncPathFilter.kt`). Prevents users writing redundant rules or
   thinking a rule "isn't working" when the blocklist is actually the thing excluding it.
7. **Unsaved-edit back-navigation**: intercept back press when dirty, show a discard-confirmation
   dialog ("Discard changes?" — Keep editing / Discard). Same reasoning as write-failure handling
   — don't silently destroy an in-progress edit.
8. **Single vault root** — confirmed the app has one WebDAV root per install (`RootId` is a
   derived key, not a multi-root list), so no root-picker is needed; one `.syncignore` at the one
   vault root.

## Decisions (resolved via blindspot pass)

9. **Impact preview before Save — the actual point of this feature.** Save must show a dry-run
   diff of old rules vs new rules against the current known file set (`server_snapshot`/index):
   "N files will stop syncing" / "M files will start syncing", with the path lists expandable.
   Without this, the editor is just a blind text box and you don't find out what a rule did until
   files vanish or appear after the triggered sync. Compute by running both `SyncIgnoreRules`
   instances (old vs new, via `parseVerbose`) over the current snapshot's file paths and diffing
   the `matches()` results. Surface this **before** the user commits, not just as a post-save
   banner.
10. **Guardrail against catastrophic rules.** If the new rule set would newly exclude more than
    some threshold (e.g. >50% of currently-tracked files, or an exact pattern like `*`/`**`),
    show a hard confirmation step ("This will stop syncing nearly your entire vault — are you
    sure?") distinct from the routine impact preview in #9. This is the difference between "here's
    what changed" (always shown) and "you're about to do something that looks like a mistake"
    (extra friction only past the threshold).
11. **Multi-device reality must be surfaced in the UI, not just in docs.** `.syncignore` is
    dot-prefixed → never syncs → purely local per device. The editor screen must say this
    explicitly (e.g. persistent caption: "These rules apply only to this device. Copy this file's
    contents manually to any other device running LinkLet on this vault."). Consider a "Copy to
    clipboard" action on the text content specifically to make manual replication easy, since the
    app has no other mechanism to push this file to other devices.
12. **Already-synced files matching new excludes aren't cleaned up automatically.** Saving new
    rules and triggering a sync does not delete local copies of files that already downloaded
    before the rule existed — hash/ETag-based change detection means an unchanged file is never
    revisited. The impact preview (#9) must say this plainly for the "N files will stop syncing"
    case: syncing stops, but any copy already on this device stays put; deleting it is a separate,
    explicit action (out of scope for this plan — flag as a possible future "prune now" button
    next to the preview, not building it here).
13. **SAF dotfile write path is unverified — spike before implementing.** Before writing
    `SyncIgnoreEditorViewModel.save()`, confirm whether any other dotfile is written today through
    `DocumentTreeStorageImpl.writeFileBytes(...)`. If none is, add a throwaway on-device
    verification (write `.syncignore` via `IStorage`, inspect via `adb shell` / device file
    browser) before trusting the write path — some SAF providers mangle dotfile names or append
    an extension based on MIME type on `createDocument`.
14. **Size/encoding guard on load.** Cap how much of `.syncignore` gets loaded into the
    `TextFieldValue` (e.g. a few hundred KB is already absurd for a rule file) and handle non-UTF8
    content without crashing — show an error state ("Could not read `.syncignore` — unexpected
    encoding") rather than feeding garbage bytes into the text field.
15. **Revert-to-last-saved while staying on screen.** Add a "Revert" action (distinct from the
    back-nav discard dialog in #7) that resets the text field to the last-saved content without
    leaving the screen — useful mid-iteration when a rule is going wrong and you want to back out
    without navigating away first.

## New/changed code

- **`SyncIgnoreRules.kt`**: add `parseVerbose(text: String): VerboseParseResult` as a *sibling*
  to the existing `parse()` — returns `(rules: SyncIgnoreRules, droppedLines: List<Pair<Int,
  String>>)`. Do **not** change `parse()` itself or its call site
  (`SyncEngine.kt:74`, reloaded every sync run) — that's a hot path and shouldn't pay for
  diagnostics it doesn't need.
- **Gradle**: asset-copy task, `docs/templates/syncignore-default.txt` →
  `app/src/main/assets/syncignore-default.txt`, run before asset merge.
- **`SyncIgnoreEditorViewModel`** (new, Hilt): on init, reads `.syncignore` via
  `IStorage.readFileBytes(".syncignore")`; if absent, seeds from the bundled asset. Holds
  `TextFieldValue`, dirty flag, dropped-lines diagnostic list, saving/loading state, snackbar
  message. `save()` does parse-diagnose → write → trigger `syncScheduler.scheduleManual()`.
- **`SyncIgnoreEditorScreen`** (new, Compose): `Scaffold` + `TopAppBar` (back press intercepted
  when dirty → discard dialog) + collapsible built-in-rules info card + multi-line
  `OutlinedTextField` (`TextFieldValue`, following the `NoteEditScreen` pattern) + dropped-lines
  warning banner + Save button + `SnackbarHost`.
- **Nav graph**: new route; entry row added to `WebDavSettingsScreen.kt`, hidden when vault not
  configured.
- **Impact diff**: `SyncIgnoreEditorViewModel` needs read access to the current tracked file list
  (`ServerSnapshotDao.getAllFiles(rootId)` or equivalent) to compute the old-rules-vs-new-rules
  diff for the impact preview (#9) and the catastrophic-rule threshold check (#10).
- **Clipboard action** on the editor screen (#11) — copy current `.syncignore` text, to support
  manual replication to other devices.
- **Load-time guards** (#14): size cap + UTF-8 decode error handling in
  `SyncIgnoreEditorViewModel`'s initial read.
- **Revert action** (#15): `SyncIgnoreEditorViewModel.revert()` resets `TextFieldValue` to
  last-saved content, clears dirty flag, no navigation.

## Out of scope / not decided here

- Any change to `SyncPathFilter`'s blocklist-vs-allowlist model (already settled earlier this
  session: blocklist stays, no hardcoded extension allowlist).
- Multi-root support (not present in the app; not designed for here).
- Live/inline syntax highlighting of the gitignore-lite grammar in the text field — plain text
  field only, diagnostics surface after Save, not as you type.

## DOX follow-up (when implemented)

Update the owning `AGENTS.md` for whichever `ui/` subtree hosts the new screen, and
`data/sync/AGENTS.md` if `SyncIgnoreRules`'s public surface changes (new `parseVerbose`).
