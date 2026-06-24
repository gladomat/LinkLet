# LinkLet — Phase 2 Test Scenario Inventory

> Phase 2 companion to [`QA_FEATURE_MATRIX.md`](./QA_FEATURE_MATRIX.md). For every feature ID in the canonical matrix, this file enumerates explicit test scenarios across seven dimensions — **Happy · Error · Boundary · Invalid · Security · Performance · Mobile** — grounded in each feature's documented Expected Behaviour and Edge Cases. The matrix's *Test Cases* column is the one-line summary of these scenarios; this file is the long form. Cells marked `n/a — <reason>` are genuinely inapplicable. Where an automated test already exercises a scenario, the covering class is named in parentheses.

App: Android (Kotlin · Jetpack Compose · MVVM + Hilt · Room · WorkManager). Org-roam note viewer/editor with a 2-pass SQLite index and WebDAV/Nextcloud sync, 8 navigation routes.

---

## 1. NoteListScreen (route `note_list`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| LST-01 | List renders title/filename/snippet from repo flow (NoteListViewModelTests) | repo flow emits error → list stays last-known/empty, no crash | empty vault → "No notes yet"; single note; thousands of notes | note with no title → filename shown as title | only `.org` under sync root listed; no path traversal leaks foreign files | 5k-note vault scrolls without jank; LazyColumn recycles rows | portrait/landscape relayout; long titles ellipsize; conflict badge fits narrow width |
| LST-02 | Type query → real-time case-insensitive title/tag filter (NoteListViewModelTests) | filter over malformed metadata → no crash, row skipped | empty query → all notes; query matching 0 → "no matches"; whitespace-only trimmed to all | unicode/emoji query handled; leading/trailing spaces trimmed | n/a — local read-only filter | filtering 5k notes per keystroke stays responsive (debounced/derived) | soft-keyboard search field; query bar fits small screens |
| LST-03 | Each of 6 sort options reorders with checkmark; persisted in StateFlow (NoteListViewModelTests) | corrupt timestamp doesn't throw during compare | null timestamp → sorted to extreme; identical keys stable; 14-digit filename ts parsed | non-numeric filename ts → fallback ordering | n/a | sorting 5k notes is O(n log n), no UI freeze | sort menu reachable one-handed; checkmark visible |
| LST-04 | FAB → editor with NEW_NOTE_PATH (partial coverage) | nav with no graph → no crash | rapid double-tap launches single editor (launchSingleTop) | n/a — no user input at nav | new note absent from list until first save (no premature write) | nav transition smooth | FAB above keyboard/insets; reachable bottom-right |
| LST-05 | Pass1/pass2 bars show completed/total + % (NoteListViewModelTests) | progress source errors → bars hidden, no crash | total=0 → bars hidden; completed>total → coerced ≤100% | negative/garbage counts coerced | n/a | progress flow updates throttled, no recomposition storm | bars fit narrow width; indeterminate spinner on small screens |
| LST-06 | Pass2 failure count red + Retry; combined pass1+2 snackbar (partial) | retry enqueue fails → message, no crash | pass2 total=0 hides failure row; 1 failure vs many | n/a | retry re-enqueues only failed entries, no privilege change | retry doesn't block UI thread | Retry button tap target ≥48dp |
| LST-07 | Regex `(conflicted copy YYYY-MM-DD HH-MM)` flags copy + shows ts (NoteListViewModelTests) | malformed match → treated as non-conflict, no crash | multiple conflict markers; case-insensitive match | non-matching filename → no badge | n/a | regex run per row is cheap | badge + timestamp fit row width |
| LST-08 | Icons/menu route to Settings, Trash, SyncStatus (partial) | nav target missing → no crash | n/a | n/a — fixed routes | routes internal only | instant nav | overflow menu reachable; icons ≥48dp |
| LST-09 | Snackbar with optional "Review" → SyncStatus; auto-dismiss (partial) | null message → no snackbar shown | rapid successive messages queue/replace | empty/blank message suppressed | Review nav internal | snackbar lifecycle not leaked across config change | swipe-to-dismiss; action fits above keyboard |

## 2. NoteViewScreen (route `note_view/{path}`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| VIEW-01 | Parses text/links/images/tables/code blocks (NoteViewViewModelTests) | load error → error state + Retry button | empty note renders nothing gracefully; very large note | unknown block → rendered raw, not dropped | stub note → "Not downloaded" + button, no remote auto-fetch | huge note renders incrementally, no ANR | blocks reflow to width; tables horizontally scroll |
| VIEW-02 | `[[id:]]`/`[[path]]` navigate; back pops history (partial) | broken/missing target → error state, no crash | history cap 32 → oldest dropped; empty history → exit; dedup same note | malformed link ref → no nav, no crash | path links resolved within root only (no traversal) | deep nav chain doesn't leak; cap bounds memory | back gesture pops; nav transitions smooth |
| VIEW-03 | Toggle search bar; highlight; prev/next cycles activeMatch (NoteViewViewModelTests) | invalid regex → inline message, no crash | empty query → clears highlights; single match cycles to self; non-ASCII `\b` | invalid regex pattern rejected with message | n/a — in-memory | 150ms debounce caps recompute on large note | search bar above keyboard; prev/next tap targets |
| VIEW-04 | Star toggles filled/outlined in Success state (NoteViewViewModelTests) | toggle outside Success → no-op | rapid toggle settles | n/a | n/a | trivial | star ≥48dp tap target |
| VIEW-05 | Share intent carries title+content in Success state (NoteActionSheetsTests) | non-Success → no intent fired | very large content payload | n/a | intent grants temp read URI only (FileProvider); no raw path leak | large payload doesn't block UI | share sheet renders; sheet dismissable |
| VIEW-06 | Copy to clipboard; toast "clears in 60s" (partial) | clipboard unavailable → no crash | timer ~60s approximate; clears only if unchanged | n/a | clipboard cleared after 60s to limit exposure; skip if user replaced it | timer doesn't leak across destroy | toast visible; works with keyboard up |
| VIEW-07 | DeleteNoteDialog shows filename + backlink count → soft delete (NoteViewViewModelTests) | not-loaded → dialog guarded | backlink count 0/1/many pluralization | n/a | soft delete moves to trash, recoverable (no hard loss) | backlink count query cheap | dialog fits small screen; buttons reachable |
| VIEW-08 | Duplicate → new timestamp ID, navigate to dup with history (partial) | repo error → toast, no nav | duplicate of large note | n/a | new ID generated, no ID collision/overwrite | duplicate write off main thread | nav transition; toast visible |
| VIEW-09 | Rename dialog edits name w/o `.org`; backlinks survive (ID-based) (RenameNoteDialog/NoteViewViewModelTests) | rename fails → inline error, no rename | empty name; 255-char name; name == existing → collision | names with `/ \ :` or leading `.` rejected with inline error | rename stays within root; ID-based backlinks unaffected | rename + reindex off main thread | dialog field above keyboard |
| VIEW-10 | Stub → "Download requested"; triggers sync (partial) | repo error → error state | already-downloaded → no-op | n/a | download via configured sync only | request non-blocking | button reachable; toast visible |
| VIEW-11 | Navigate to editor with path; REFRESH_NOTE_KEY reloads on return (partial) | reload fails → error state | n/a | n/a | path internal | reload only when flag set | nav transition smooth |
| VIEW-12 | Dialog: ID read-only, ROAM_REFS/ROAM_ALIASES editable; empty removed on save (NoteViewViewModelTests) | save error → message, dialog stays | empty value → property removed; many props | ID edit attempt blocked (read-only) | ID immutable — cannot be overwritten | save write cheap | dialog scrolls; fields above keyboard |
| VIEW-13 | TagPickerDialog autocomplete; lowercase + strip; ≤5 suggestions (NoteViewViewModelTests) | save error → message | blank tag rejected; dup tag deduped; >5 suggestions truncated | `[^a-z0-9_-]` stripped; normalize collision deduped | n/a | suggestion query bounded to ≤5 | chips wrap; field above keyboard |
| VIEW-14 | MoreActions Expand/Collapse All; per-section (NoteActionSheetsTests) | state outside Success → no-op | all collapsed; all expanded; deeply nested sections | n/a | n/a | toggle is local UI state, cheap | sheet dismissable; toggles reachable |
| VIEW-15 | Link icon enabled when backlinks exist; sheet lists deduped (NoteViewViewModelTests) | query error → empty sheet, no crash | none → "No backlinks" + icon disabled; dedup duplicates | n/a | only non-deleted sources listed | dedup/list bounded | sheet scrolls; rows ≥48dp |

## 3. NoteEditScreen (route `note_edit/{path}`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| EDIT-01 | Scrollable field; cursor preserved; display name from first heading/title (NoteEditViewModelTests) | load fail → error state | new note → "New note"; empty content; very large content | n/a — free text | content stays local until save | large doc edit stays responsive | field scrolls above keyboard; cursor visible |
| EDIT-02 | First save generates UUID org-id, inserts to drawer, timestamp+slug filename (NoteEditViewModelTests) | insert fail → error, stays in editor | missing drawer → plain insert; slug from empty title | invalid filename chars sanitized into slug | UUID unique, no collision; write within root | save off main thread | save spinner; keyboard handled |
| EDIT-03 | Reuses path; ID not overwritten; dirty-check vs initialContent (NoteEditViewModelTests) | save fail → error | loaded missing ID → generated on save; no-change save | n/a | existing ID never overwritten | dirty-check avoids needless writes | n/a — same surface |
| EDIT-04 | Undo pops history (~50) with apply suppression (NoteEditViewModelTests) | undo apply re-entry guarded (loop guard) | undo at start → noop; cap at ~50 entries | n/a | n/a | bounded history → bounded memory | undo button reachable |
| EDIT-05 | Heading 1-6, bold/italic/code/lists/indent insert with cursor preserve (NoteEditViewModelTests) | n/a — pure insert | cursor at start/end/empty selection | no markup validation (by design) | n/a | each insert is local string op | toolbar above keyboard; buttons ≥48dp |
| EDIT-06 | ±2 spaces per level on any line (partial) | n/a | unindent at min (0) → no negative; multi-line selection | no structure validation | n/a | cheap line op | indent buttons reachable |
| EDIT-07 | Dialog discard/continue only if dirty (NoteEditViewModelTests) | n/a | clean → no dialog; saving → no dialog | n/a | discard loses unsaved only after confirm | n/a | dialog fits; buttons reachable |
| EDIT-08 | Spinner; isSaving disables back; Saved(path) → onDone → view (NoteEditViewModelTests) | save fail → error, re-enable back | long save shows spinner; completion clears dialog | n/a | back disabled mid-save prevents partial-state exit | save async, UI not blocked | spinner visible; back gesture gated |

## 4. SettingsScreen (route `settings`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| SET-01 | SAF folder picker; persistable R/W perms taken; path shown (partial) | SecurityException on take-perms caught → handled | perms already granted → no re-take needed | non-tree URI → rejected | only persistable R/W URI perms taken; no broad storage access | path resolution cached | picker is system UI; path text ellipsizes |
| SET-02 | Enqueues sync; result message (NoteList/SettingsViewModelTests) | no folder → error message | disabled while syncing/no folder | n/a | sync uses configured creds only | enqueue non-blocking | button disabled state visible |
| SET-03 | Navigate to WebDavSettings (partial) | nav target missing → no crash | n/a | n/a | internal route | instant | nav smooth |
| SET-04 | Switch → SyncSettingsRepository, persists (SettingsViewModelTests) | persist fail → no crash | rapid toggle settles | n/a | n/a | trivial | switch ≥48dp |
| SET-05 | Set minutes (Long), default 60 (partial) | persist fail → no crash | 0/negative rejected; very large interval | non-numeric input rejected | n/a | trivial | numeric keyboard; field reachable |
| SET-06 | SyncDirectoryChangeDialog "Clear & Continue" → clearAllStates + resetAndReindex atomically (partial) | step failure stops flow, surfaces error | dismiss → no change; mid-index change | n/a | atomic clear+reindex prevents stale cross-folder data leak | reindex off main thread | dialog fits; buttons reachable |
| SET-07 | Success/error snackbar; auto-dismiss via clearMessage (SettingsViewModelTests) | n/a | null message suppressed; rapid messages | n/a | n/a | not leaked across config change | swipe dismiss |

## 5. WebDavSettingsScreen (route `webdav_settings`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| WDV-01 | Stores baseUrl, trimmed on save (WebDavSettingsViewModelTests) | persist fail → error snackbar | empty; trailing slash; very long URL | no UI format validation (by design) | URL stored in EncryptedSharedPreferences | trivial | URL field above keyboard |
| WDV-02 | Sets rootPath; defaults `/` if blank on save (WebDavSettingsViewModelTests) | persist fail → error | blank → `/`; trailing slash; deep path | n/a | path normalized, no traversal upward | trivial | field reachable |
| WDV-03 | Username field; password masked with Show/Hide toggle (WebDavSettingsViewModelTests) | persist fail → error | empty password → no Show/Hide toggle | n/a | password encrypted at rest; masked by default | trivial | toggle ≥48dp; field above keyboard |
| WDV-04 | Switch → setEnabled, persists (WebDavSettingsViewModelTests) | persist fail → no crash | rapid toggle settles | n/a | n/a | trivial | switch reachable |
| WDV-05 | Contacts server; "Testing…" + spinner; result snackbar (WebDavSettingsViewModelTests) | timeout/auth fail → error snackbar | concurrent tests blocked | malformed URL → error result | creds sent over configured endpoint only; no logging of password | test off main thread, cancelable | spinner visible; works with keyboard up |
| WDV-06 | Persists; sanitizes rootPath; rebuilds URL; triggers sync if enabled+folder (WebDavSettingsViewModelTests) | save fail → error | baseUrl/username required → blocked if missing | invalid rootPath sanitized | encrypted persist; sync only when fully configured | save + sync trigger async | save button reachable |
| WDV-07 | Connection/save errors in snackbar; reset-on-error flag on init (partial) | n/a — is the error surface | rapid errors queue | n/a | errors don't leak credentials | not leaked across config change | snackbar swipe dismiss |

## 6. TrashScreen (route `trash`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| TRH-01 | Lazy list title+filename+restore/delete actions (partial) | list query fail → no crash | empty trash → empty list | malformed metadata row skipped | only trash-bin contents listed | large trash scrolls without jank | rows + actions fit narrow width |
| TRH-02 | restore → toast "Restored: {path}" + refresh (TrashViewModelTests) | failure → error toast | missing metadata; collision → `(n)` suffix | n/a | restore stays within root | restore off main thread | toast visible |
| TRH-03 | Confirm dialog → deleteForever + refresh (TrashViewModelTests) | failure → error toast | not-recoverable item | n/a | permanent delete gated behind confirm | delete async | dialog fits; confirm reachable |
| TRH-04 | Iterates restore; "{n} restored, {x} failed" (partial) | partial failures reported in summary | empty → "Trash is empty" | n/a | each restore within root | bulk restore off main thread; bounded | summary toast visible |
| TRH-05 | Confirm → delete all; result toast (partial) | partial failures reported | empty trash; large trash | n/a | bulk purge gated behind confirm | bulk delete async, bounded | confirm dialog fits |
| TRH-06 | Single + all confirm dialogs; Cancel dismisses (partial) | n/a | required before destructive op | n/a | destructive ops require explicit confirm | n/a | dialogs fit; buttons reachable |

## 7. SyncStatusScreen (route `sync_status`)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| SYN-01 | Each status type renders; DIRECTORY_CHANGED old/new path, REQUIRES_CONFIRMATION newPath (SyncStatusViewModelTests) | status flow error → no crash | null → "No sync issues" | unknown status type → safe default | paths shown but ops gated | trivial | long paths ellipsize/wrap |
| SYN-02 | clearAllStates + resetAndReindex + scheduleManual (SyncStatusViewModelTests) | step failure stops flow, surfaces error | mid-index trigger | n/a | atomic resolve prevents stale data adoption | reindex off main thread | buttons reachable |
| SYN-03 | dismiss → clearStatus only, no resync (partial) | n/a | repeated dismiss idempotent | n/a | non-destructive (no data change) | trivial | dismiss reachable |

## 8. UI Components

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| CMP-01 | Tables/code/quotes/paragraphs/lists/verse render (OrgBlockRendererTests) | render error contained per block | empty block; deeply nested list | unknown block → raw fallback | rendered content sandboxed (no exec) | large table virtualized/scrolls | tables horizontally scroll on narrow width |
| CMP-02 | `[[image:]]`/`[[file:img]]` detect; fullscreen on tap; async load + downsample (InlineImageTests) | broken URI → placeholder | very large image downsampled; 0-byte file | non-image URI → placeholder | image URI resolved via FileProvider grant only | downsample bounds memory; async load off UI | fullscreen fits screen; pinch/tap dismiss |
| CMP-03 | Kotlin/Java/Python/Shell/Elisp keyword/string/comment highlight (partial) | malformed code → plain render, no crash | empty code; very long lines | unknown lang → plain text | no code execution | highlighting bounded per block | code block scrolls horizontally |
| CMP-04 | Dedup by source; title priority sourceTitle>alias>source; click-nav (BacklinkListTests) | nav target missing → no crash | empty → hidden; many backlinks | n/a | deleted sources excluded | dedup/list bounded | rows ≥48dp; list scrolls |
| CMP-05 | Bold/italic/code/underline inline spans (OrgTextFormatterTests) | malformed markup → literal text | nested markup; unmatched markers → literal | n/a | n/a | linear scan, cheap | spans reflow to width |
| CMP-06 | Metadata/preface/nested sections via block state machine (OrgDocumentParserTests) | unclosed block → Unknown, no crash | CRLF handling; empty doc; deeply nested | malformed drawer → empty | parser pure, no side effects | large doc parsed in bounded time | n/a — parser, no UI |

## 9. Sync Engine (data/sync)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| SNC-01 | Parallel probe search/trashbin/fileId; cache 24h pos/6h neg (CapabilitiesProbeTests) | timeout → capability false | cache hit vs expiry boundary | username regex fail → handled | creds sent to configured server only | parallel probe minimizes latency | probe runs background, no UI block |
| SNC-02 | PROPFIND depth 0/1; DAV+oc props; streaming XML (PropfindParserTests) | non-2xx → handled; malformed XML caught | missing href → skip row; empty tree | malformed etag normalized | server response parsed, no injection | streaming XML bounds memory for huge trees | background; no UI block |
| SNC-03 | Watermark; SEARCH since wm-5s; trashbin deletions; dedup (DeltaSyncTests) | 5xx → SOFT_INVALID; auth → HARD_INVALID | no watermark → HARD; servertime skew | malformed delta entry skipped | scoped to configured root | delta avoids full crawl, bounded | background |
| SNC-04 | BFS crawl; etag-bubble skip; stub creation; batch upsert; resume (SnapshotBuilderTests) | propfind fail → skip subtree | dir limit → resume; %2F encoding | malformed href skipped | scoped to root; no traversal | etag-bubble skips unchanged subtrees; batch upsert | resumable across interruptions |
| SNC-05 | Fresh adopt/conflict/download; steady 3-way; orphan cleanup (ReconcilePlannerTests) | adoptMatch null → download+backup | both-changed → conflict; empty local | n/a | backup before overwrite prevents data loss | plan computed in memory, bounded | background |
| SNC-06 | Catastrophic/RequiresConfirmation/Misconfigured/DirectoryChanged guards fire (GuardRailsTests) | guard trips → abort sync safely | 100%/>50/>50% thresholds; >20% confirm | totalRemote≤0 → abort | guards prevent mass deletion/data loss | threshold checks cheap | confirmation surfaced to user |
| SNC-07 | Adopt/Upload/Download/SoftDelete/Conflict state updates (SyncStatePersistenceTests) | upload fail → no state update | ACTIVE/DELETED_LOCALLY/DELETED_REMOTELY transitions | n/a | pending op lost on crash → recovered, no silent loss | state writes batched | background |
| SNC-08 | Conflicted-copy path + download remote (ConflictHandlingTests) | local bytes missing → warn + download | overlapping edits; 3-way merge (unused) | n/a | conflict preserves both copies, no overwrite | merge bounded | background |
| SNC-09 | Allow org/img/pdf/bib; block dotfiles/`_`/system/ltximg (PathFilterTests) | n/a | empty ext → reject; leading `.`/`_` blocked | unexpected ext rejected | trash + system files excluded from sync | filter is cheap predicate | background |
| SNC-10 | SHA-256 content; multi-algo parse; adoptMatch via checksum/size (HashChecksumTests) | algo unavailable → null, handled | identical content same hash; empty file | malformed checksum header → null | hash integrity used for change detection | hashing streamed, bounded | background |
| SNC-11 | HTTP counters; stage timing; snapshot+reset; thread-safe (MetricsTests) | NoOp metrics path safe | concurrent increment correct | n/a | metrics carry no credentials | atomic counters, low overhead | background |
| SNC-12 | WorkManager; INITIAL→foreground notif; progress; retry map (SyncWorkerTests/SyncSchedulerTest) | no network → skip; auth → fail | retry backoff; progress 0→100 | n/a | foreground service typed dataSync; scoped creds | network check avoids wasted work | foreground notification on small screens |
| SNC-13 | Persist PLANNED; claim→RUNNING→COMPLETED/FAILED; semaphore concurrency (JournalExecutorTests) | execute fail → FAILED recorded | claim race → null; max concurrency | n/a | durable journal survives crash | semaphore caps concurrency | background |
| SNC-14 | Delete snapshot rows deletedAt < now-30d (TombstoneGcTests) | none → 0 deleted, no crash | exactly 30d boundary; large tombstone set | n/a | GC only removes own tombstones | bulk delete bounded | background |
| SNC-15 | Select STUB/PINNED within budget (50 files/5MB/512KB); priority+recency (PrefetchPolicyTests) | out-of-space → stop | too-large file → skip; exactly at budget | n/a | prefetch within configured scope | budget caps bandwidth/storage | respects mobile data/storage limits |
| SNC-16 | hash(baseUrl:rootPath:user); normalize path trim→`/` (RootIdTests) | n/a | multi-user/multi-dir distinct ids | malformed path normalized | per-account isolation via root id | trivial | background |

## 10. Indexing (data/index)

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| IDX-01 | Scan, fingerprint mtime+size, upsert NoteEntity linksReady=false, enqueue pass2 (IndexPass1ProcessorTests) | file vanishes mid-scan → tombstone | fingerprint race; 0-byte file; huge file | unreadable file → skip/requeue | scan scoped to sync root | 20s budget caps work; fingerprint avoids re-parse | background; respects budget |
| IDX-02 | Parse links, resolve Path/Id, insert LinkEntity, linksReady=true when all resolved (IndexPass2ProcessorTests) | unresolved id → not ready, retried | dup links deduped; note with 0 links | malformed link skipped | resolution stays within root | dedup bounds writes | background |
| IDX-03 | PK(path,pass); PENDING→RUNNING→DONE/FAILED; claimNext atomic; 20s budget (IndexQueueDaoTests) | claim race → null, no double-process | lease 10min boundary; budget exhausted mid-run | n/a | atomic claim prevents duplicate work | budget bounds per-run time | background |
| IDX-04 | Persist scan/enqueue/run times; pass2 waits pending=0 & idle>10s (IndexingStateTests) | state write fail → no crash | thundering herd → gated; idle exactly 10s | n/a | n/a | gate prevents pass2 churn | background |
| IDX-05 | Skip listNotes if PENDING exist; else full scan+fingerprint (partial) | scan fail → retry | external invalidation forces rescan | n/a | scoped to root | resume avoids full re-scan | background |
| IDX-06 | Stale RUNNING>1h→FAILED; >2min→requeue; 3min delayed continuation (OrphanRecoveryTests) | recovery itself fails → no loop | overlapping thresholds (2min/1h) | n/a | n/a | recovery prevents infinite index loops | background; no battery drain loop |
| IDX-07 | MAX_ATTEMPTS=5; FAILED preserves attempts; pass2 skips terminal (AttemptCapTests) | n/a | exactly 5 attempts → terminal | n/a | resurrect FAILED keeps count (no infinite retry) | cap bounds retry work | background |
| IDX-08 | Missing path → markDeleted(now); pass2 DELETE cleans links by orgId/paths (IndexPass2ProcessorTests) | NoteNotFound handled | reappear → needs reset; orphan links | n/a | delete scoped to orgId/paths | link cleanup bounded | background |
| IDX-09 | resetAndReindex clears queue/notes/links/state in txn + schedulePass1 (partial) | txn fail → rolled back, no partial state | mid-index reset | n/a | atomic reset prevents stale cross-folder data | reset off main thread | background |
| IDX-10 | ALTER w/ defaults; v2→3 destructive sync_state; new tables per version (NoteDaoTests/IndexQueueDaoTests indirect; migration tests `@Ignore` DEF-009) | corrupt DB → handled | downgrade path; each v1→v9 step | n/a | migrations preserve user data (except documented v2→3) | migration runs once at open | one-time on upgrade |
| IDX-11 | WorkManager KEEP/APPEND/REPLACE; pass1 continuation; pass2 gated (IndexingSchedulerTests) | enqueue fail → no crash | concurrent workers deduped via KEEP | n/a | n/a | KEEP prevents redundant scans (index-loop fix) | background; battery-friendly |

## 11. Parser / Storage / Domain

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| PRS-01 | `#+title:` regex extracts title; fallback to path (TitleExtractionTests) | n/a — pure | empty title → path; whitespace trimmed | malformed line → fallback | n/a | linear scan | n/a — parser |
| PRS-02 | Parse `:PROPERTIES:…:END:`; keys upper; ID drawer + legacy fallback (PropertiesDrawerTests) | malformed drawer → empty | dup key → last-wins; missing :END: | malformed property line skipped | n/a | bounded scan | n/a |
| PRS-03 | `#+filetags:` split `:`/ws; lowercase+normalize+dedup (FiletagsTests) | n/a | blank → empty; special chars stripped | invalid tag chars normalized | n/a | linear | n/a |
| PRS-04 | `[[file:/id:…]]` + alias; external URL detect (LinkParsingTests) | n/a | case-insensitive; plain `[[path]]`→Unknown | malformed link → Unknown | external URLs flagged, not auto-opened | linear scan | n/a |
| PRS-05 | Relative/abs resolution; attachment `data/xx/rest` (PathResolutionTests) | unresolvable → handled | short id; `..` at root boundary | traversal `..` blocked from escaping root | path traversal blocked — cannot escape root | bounded resolution | n/a |
| STG-01 | walkTopDown `.org`; resolvePath canonical within root; NoteNotFound (FileStorageTests) | missing file → NoteNotFound | symlink loop guarded | traversal path rejected | canonical resolution confined to root | walk bounded to `.org` | n/a — dev FS storage |
| STG-02 | DocumentFile root cache; pathUriCache; traverse; invalidate (DocumentTreeStorageTests) | slow SAF → handled; stale cache invalidated | deep tree; cache miss vs hit | invalid URI rejected | SAF perms scoped to picked tree | cache avoids repeated SAF traversal (slow) | mobile SAF latency mitigated by cache |
| DOM-01 | Write+upsert metadata+enqueue pass1+schedule sync; rename/dup (NoteRepositoryTests) | write fail → surfaced, no partial | collision → handled; invalid filename | invalid filename rejected/sanitized | writes within root; dup gets new ID | metadata upsert + enqueue cheap | background save |
| DOM-02 | Move to `_trash_bin/<ts>_<slug>.org` + `.trashinfo`; restore `(n)`; purge (NoteRepositoryTests) | missing metadata handled | legacy `_trash` path; restore collision `(n)` | n/a | soft delete recoverable; purge gated upstream | trash ops bounded | background |
| DOM-03 | Scan active notes, parse, resolve file+id links, rebuild tables (ReindexTests) | corrupt file → skip; missing target → unresolved | empty vault; huge vault | malformed file skipped | scoped to root | full reindex off main thread, bounded | background |
| DOM-04 | LinkEntityDto (source/target/alias/title) from DAO (BacklinkQueryTests) | query error → empty | none → empty; many backlinks | n/a | deleted source filtered out | indexed query | n/a — data |
| DOM-05 | Literal (case/word), regex; block-local ranges; empty→empty (SearchEngineTests) | invalid regex → handled | empty query → empty; `\b` non-ASCII | invalid regex rejected | n/a — in-memory | block-local ranges bound work | n/a — engine |
| DOM-06 | AVAILABLE/STUB; stub → requestNoteDownload (partial) | uninitialized → handled | partial sync state | n/a | download via configured sync | trivial | background |
| DOM-07 | Observe webdav+sync settings; schedule periodic if configured; SupervisorJob (partial) | invalid creds → no schedule | not-initialized → no-op | n/a | creds read from encrypted store | SupervisorJob isolates failures | background wiring |

## 12. Workers · Navigation · Configuration

| ID | Happy | Error | Boundary | Invalid | Security | Performance | Mobile |
|----|-------|-------|----------|---------|----------|-------------|--------|
| SNC-17 | `SyncEnqueueWorker.doWork` enqueues one-time SyncWorker (PERIODIC) with KEEP (partial) | enqueue fail → worker retries | already-queued → KEEP no-ops (dedup) | n/a | indirection prevents concurrent sync runs | KEEP prevents redundant workers | background; battery-friendly |
| NAV-01 | `handleIntent` reads EXTRA_NAV_TARGET; LaunchedEffect → sync_status (launchSingleTop) (UNTESTED) | null/unknown target → no nav, no crash | cold start vs onNewIntent; target cleared after nav | malformed extra ignored | only known nav targets honored | single dispatch, cleared | deep link from notification tap |
| CFG-01 | `${applicationId}.fileprovider` + `@xml/file_paths` grants temp read URIs (partial) | path not in file_paths → SecurityException handled | URI permission lifetime expiry | non-shared path rejected | temporary read-only URI grant; no raw path exposure | grant is per-intent, cheap | share + inline-image fullscreen on device |
| CFG-02 | Manifest declares INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE(+DATA_SYNC), POST_NOTIFICATIONS; service typed dataSync (manifest test) | POST_NOTIFICATIONS denied (API 33+) → sync still runs, no notif | API 33+ runtime grant boundary | n/a | declares only needed perms (least privilege) | n/a | runtime perm prompt on Android 13+ |
| CFG-03 | InitializationProvider removes default WorkManagerInitializer; LinkLetApp provides Configuration with HiltWorkerFactory (partial) | factory not injected → worker create fails (caught) | n/a | n/a | DI-scoped worker creation | one-time init | n/a — app config |

---

**Coverage: 85 / 85 feature IDs.** (LST 9 · VIEW 15 · EDIT 8 · SET 7 · WDV 7 · TRH 6 · SYN 3 · CMP 6 · SNC 17 · IDX 11 · PRS 5 · STG 2 · DOM 7 · NAV 1 · CFG 3.)
