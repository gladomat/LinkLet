Plan: Note graph view (Obsidian-style)

Context

Users want a visual graph of how notes connect — nodes are notes, edges are links between
them — with click-to-open navigation, similar to Obsidian's graph view. This is a greenfield
feature: no Canvas/custom-drawing, WebView, or OpenGL usage exists anywhere in the app today
(confirmed by repo-wide grep). Everything below builds on what's already in place rather than
introducing a rendering framework the app doesn't otherwise use.

Real vault size is 500–2000 notes (confirmed with the user) — this is not a "hundreds of
notes, naive approach is fine" scale; it directly shapes the layout-engine and caching
decisions below.

What already exists to build on

- `notes` table (`data/index/NoteEntity.kt:18`) — path is the primary key, doubles as node id.
  `orgId` is a per-note org-roam `:ID:` property (used for `id:`-style links) — confirmed this
  is *not* a multi-vault/org grouping construct, so the graph never needs to reason about org
  boundaries; one vault, one graph.
- `links` table (`data/index/NoteEntity.kt:32`) — `source`/`target` are note paths, already a
  directed edge list. `alias` holds the link's display label. Directionality exists in the data
  but is not rendered (see Design decisions).
- `NoteEntity.linksReady` (`data/index/NoteEntity.kt:18`) flips true only after pass-2 indexing
  parses a note's body and populates its outgoing `links` rows (`IndexPass2Processor`). A note
  with `linksReady = false` may have real links that just haven't been parsed yet — its absence
  of edges is not the same claim as "this note is a confirmed orphan" (Design decision 9).
  `IndexPass2Processor`'s `id:` link resolution is documented as N+1 (`data/index/AGENTS.md`) —
  after a bulk sync/import, a batch of notes can sit at `linksReady = false` for a while, so the
  graph can be substantially edge-incomplete right after heavy sync activity, not just slightly
  stale.
- Links are derived data: link picker inserts inline `[[file:...][Title]]` /
  `[[id:...][Title]]` markup into note body text (`OrgFileUtils.buildNoteLink`,
  `data/utils/OrgFileUtils.kt:213`); `IndexPass2Processor` parses note bodies and rebuilds the
  `links` table as a cache. The graph feature reads this cache — it never derives links itself.
- Only existing relationship query is `NoteDao.getBacklinks(path)` (single-target join). No
  "all edges" query exists — this is the one query-layer gap to fill.
- Note navigation: `navController.navigate("note_view/${Uri.encode(path)}")`
  (`ui/MainActivity.kt:62`), route arg `NoteViewViewModel.NoteArgs.NOTE_PATH`. A graph node
  click reuses this verbatim — no new route type needed.
- Existing entry-point precedent, `ui/screens/NoteListScreen.kt:198-266`: a `MoreVert` overflow
  `DropdownMenu` already holds one secondary top-level screen ("Show Deleted Notes" → Trash),
  separate from the always-visible Settings icon. Global graph entry follows this exact
  precedent.
- Existing per-note action precedent, `ui/screens/note/NoteFooterBar.kt` (Share, Favorite,
  Backlinks, More) and `NoteViewScreen.kt:381-410` (opens `BacklinksBottomSheet` on tap). Local
  graph entry adds a footer action here, alongside Backlinks.
- Existing search-over-notes infra: `LinkPickerDialog` + `NoteEditViewModel.linkPickerState`
  search `NoteIndexEntry` by title. The graph screen's search reuses this, not a new search
  implementation.
- Package convention: one screen package under `ui/screens/<feature>/` + mirrored
  `viewmodel/<feature>/`, DI wired in `app/di/AppModule.kt`. Graph feature follows the same
  shape: `ui/screens/graph/`, `viewmodel/graph/`, plus a small `data/graph/` (or a couple of
  new methods on `NoteDao`/`INoteRepository`) for the edge query and position cache.

Design decisions (grilled and confirmed)

1. **2D only for v1, Compose Canvas, no WebView/3D library.** No 3D/WebGL precedent exists in
   the app; a 3D engine (SceneView/Filament) or WebView+three.js bridge is real added weight —
   new dependency surface, new rendering paradigm — for a feature that doesn't need three
   dimensions to deliver "see connections, tap to open." 3D is an explicit, deferred stretch
   goal (below), reusing the same layout data with one more axis if it's ever built.

2. **Force-directed layout: Fruchterman-Reingold with Barnes-Hut quadtree approximation, built
   in from v1 — not deferred.** At the confirmed 500–2000 note scale, naive O(n²) repulsion is
   up to ~4M pairwise calcs per tick; on the JVM (object overhead, GC pressure, not raw C) that
   risks visible stutter during layout settling, not "trivially fast." The quadtree
   approximation (what D3-force uses beyond a few hundred nodes) needs to be part of
   `ForceLayoutEngine`'s first version, not a "later if it turns out we need it" upgrade.

3. **Layout runs off the main thread, ticks published as Compose state, freezes on
   convergence.** Stop once node movement drops below a threshold or a max iteration count is
   hit — don't animate forever.

4. **Layout positions are cached to disk, not recomputed from scratch every open.** At this
   node count, watching the graph unscramble from a random seed on every single open is a real
   cost, not a one-time nicety. A new small Room table (`graph_positions(path TEXT PRIMARY KEY,
   x REAL, y REAL, updatedAt INTEGER)`, consistent with the rest of the app's Room-first
   persistence — no new persistence mechanism like DataStore needed for this) stores the last
   settled position per note. On open: load cached positions, feed them as the simulation's
   initial state, and only new/changed nodes (new notes, new/removed edges) get nudged —
   everything else starts already-settled and the simulation converges near-instantly. Renames
   update the cache row's key the same way `NoteDao.renameNotePath` already updates `links`
   rows transactionally.

5. **One shared `GraphScreen` + `GraphViewModel` + `ForceLayoutEngine` for both global and local
   graph**, parameterized by `center: NodeId? = null` and `hopDepth: Int = 2`. Global mode is
   `center = null` (whole vault); local mode filters `GraphSnapshot` to the N-hop neighborhood
   of `center` before feeding the same layout/render/gesture/tap pipeline. Rendering, gestures,
   hit-testing, and the layout engine are identical between the two modes — only the input node/
   edge set differs, so this is one filter step in the data layer, not two screens.

6. **Both global and local graph ship in v1.** Global: entry point #1 above (overflow menu item
   on `NoteListScreen`, next to "Show Deleted Notes"). Local: a new "Graph" action in
   `NoteFooterBar`, alongside Backlinks, opening the same screen centered on the currently-open
   note. Hop depth is a fixed default of 2 (matches Obsidian's local-graph default) — not
   adjustable in v1; a slider is a documented later addition, not built now.

7. **Node tap interaction is two-step, and node/label size is the affordance — no floating
   card.** Tap 1 on a node: highlight it + its direct neighbors (dim the rest), and *increase
   that node's radius and label size* — this both signals selection and gives tap 2 a
   meaningfully bigger hit target on the same spot, which is what actually solves the
   tap-precision problem on a dense 500–2000 node graph (a same-size re-tap would not). Tap 2 on
   the now-enlarged node: navigate via the existing `note_view/{path}` route. Labels are not
   hidden behind an arbitrary always-zoomed-out threshold — they render whenever the current
   zoom level is already legible (matches the user's actual workflow of zooming in before
   tapping; the view should already show enough of a title to know what's about to be tapped).

8. **Edges render as plain undirected lines, no arrowheads**, even though `LinkEntity` is
   directionally stored (`source`/`target`). Matches Obsidian's default; at this edge count,
   arrowheads on every edge add clutter without much reading value given bidirectional linking
   is common in note-taking. Direction is still in the data if a toggle is wanted later.

9. **Orphan notes (zero links) are shown**, not filtered out — scattered, unconnected dots.
   Matches Obsidian's default: orphans are useful signal ("this note isn't linked to anything
   yet"), and silently hiding them would understate the graph's actual coverage of the vault.

10. **Search box ships in v1**, inside the graph screen: type a title, camera pans/zooms to and
    selects that node (same selected-state as a tap-1 — enlarged node/label, ready for a second
    action to open). Reuses the `NoteIndexEntry` title-search already built for
    `LinkPickerDialog` rather than writing a new search path. At 500–2000 nodes, "just look for
    it visually" is not a reliable way to find one specific note.

11. **Nodes whose note isn't fully link-indexed yet (`linksReady = false`) render as pending,
    not as confirmed orphans.** They still appear (per Design decision 9 — never hide a note),
    but at reduced opacity / a distinct "pending" tint until pass 2 finishes and flips
    `linksReady`; at that point they animate into their normal appearance with real edges. This
    keeps "note not yet indexed" visually distinct from "note genuinely has no links" —
    otherwise a note refreshed by sync would misleadingly look like a confirmed orphan for
    however long pass 2 takes (worse right after a bulk sync, given the documented N+1 in
    `id:` resolution above).

12. **`observeGraph()`'s underlying flows are debounced before reaching the layout engine.**
    `data/sync/AGENTS.md` describes bulk sync operations (initial full sync, large remote diffs)
    that can enqueue many index rows in a burst; each one is a potential emission on the
    notes/links flows. Without debouncing, a big sync would cause `GraphSnapshot` to recombine
    and re-feed the layout engine many times in quick succession — thrashing the simulation
    instead of settling once. Apply a short debounce (e.g. 300–500ms) on the combined flow
    before it reaches `ForceLayoutEngine`, so a burst of writes collapses into one re-layout.

13. **Local graph's hop-1 neighborhood is intentionally a superset of `BacklinksBottomSheet`,
    not required to match it.** `getBacklinks()` (existing, `NoteDao.kt:94`) is incoming-links-
    only by design ("what links to this note"). The graph's N-hop traversal is bidirectional
    (incoming and outgoing) by design ("what is this note connected to, in either direction") —
    a graph that only showed backlinks would silently drop every note this note links out to.
    This is a deliberate definitional difference, not a bug to reconcile; call it out in code
    comments on the BFS filter so a future reader doesn't try to make it match `getBacklinks()`.

14. **Edges are drawn as a single batched `Path` (one `drawPath` call), not one `drawLine` call
    per edge**, regardless of vault size — this is a baseline perf fix, not a scale-triggered
    one. Barnes-Hut only bounds the layout engine's node-repulsion cost; rendering 2,500–20,000
    edges (plausible at 500–2000 notes with ~5–10 links/note) every pan/zoom frame is a
    separate cost the layout fix doesn't touch. If profiling still shows frame drops at the
    most zoomed-out global view, add a count-based simplification (e.g. skip edges below a
    zoom threshold, mirroring the label-legibility rule in Design decision 7) as a follow-up,
    not built pre-emptively.

Data layer

- Add to `NoteDao` (`data/index/NoteDao.kt`):
  - `fun observeAllLinks(): Flow<List<LinkEntity>>` — `SELECT * FROM links` (excluding links
    whose source/target is a soft-deleted note — join against `notes.deletedAt IS NULL` on
    both ends, same filter `getBacklinks` already applies one-sided).
  - `fun observeGraphNotes(): Flow<List<NoteEntity>>` — active notes only (`deletedAt IS NULL`),
    or reuse `observeNotes()` if it already applies that filter (verify before adding a
    duplicate).
- New Room entity + DAO for the position cache: `GraphPositionEntity(path: String @PrimaryKey,
  x: Float, y: Float, updatedAt: Long)`, `GraphPositionDao` with `observeAll()`,
  `upsertAll(List<GraphPositionEntity>)`, and a rename-following update alongside
  `NoteDao.renameNotePath`.
- New repository method on `INoteRepository`/`NoteRepositoryImpl`:
  `fun observeGraph(center: NodeId? = null, hopDepth: Int = 2): Flow<GraphSnapshot>` —
  `combine` of notes + links + cached positions, `.debounce(300)` (Design decision 12) before
  mapping into a `GraphSnapshot(nodes: List<GraphNode>, edges: List<LinkEntityDto>,
  cachedPositions: Map<NodeId, Offset>)` value type, where `GraphNode` wraps `NoteIndexEntry`
  plus its `linksReady` flag (Design decision 11) (mirror the existing `LinkEntityDto` pattern
  in `INoteRepository.kt:75`). When `center` is set, filters nodes/edges to the N-hop
  neighborhood before returning — BFS from `center` up to `hopDepth`, traversing edges in
  **both** directions (Design decision 13; deliberately not the same filter as
  `getBacklinks()`), over the full edge list — cheap at this scale, no need for a recursive
  SQL CTE.
- Unresolved `id:` links (target not yet indexed, or note deleted) are dropped from the edge
  list at the query/mapping layer, not rendered as dangling nodes.
- Known landmine to avoid touching: `NoteRepositoryImpl.reindex()` uses
  `OnConflictStrategy.REPLACE` and silently drops `orgId`/`fileTags`/`linksReady`/fingerprints
  (flagged in `data/AGENTS.md`). Graph feature must not call `reindex()` as a side effect of
  opening the graph view.

Layout engine (`data/graph/ForceLayoutEngine.kt`, plain Kotlin, no Android deps — testable)

- Input: `nodes: List<NodeId>`, `edges: List<Pair<NodeId, NodeId>>`, initial positions
  `Map<NodeId, Offset>` — populated from `GraphSnapshot.cachedPositions` where available, random
  seed placement (in a seed circle) for any node with no cached entry (new notes).
- Per tick: repulsive force between node pairs computed via a Barnes-Hut quadtree
  approximation (not naive O(n²) — see Design decision 2), spring attraction along edges
  (Hooke's law toward an ideal edge length), mild centering force, "temperature" cooling
  schedule (Fruchterman-Reingold standard) so displacement shrinks and the layout settles.
- Stop condition: total displacement this tick < epsilon, or `maxIterations` reached — emit
  final positions once, persist them via `GraphPositionDao.upsertAll(...)`, and stop
  recomposing on layout ticks after that.
- Runs on `Dispatchers.Default`, ticks throttled to a fixed-step coroutine loop — publish
  position snapshots as a `StateFlow<Map<NodeId, Offset>>` the Composable collects.

Rendering & interaction (`ui/screens/graph/GraphScreen.kt`)

- Single `Canvas` composable: draw edges first as one batched `Path` / single `drawPath` call
  (Design decision 14 — not a `drawLine` per edge), plain undirected lines (Design decision 8),
  then nodes (circles sized by degree — e.g. `radius = base + k * log(1 + backlinkCount)`, and
  at reduced opacity for any node whose `linksReady = false`, per Design decision 11), then
  labels wherever the current zoom level is legible (see Design decision 7 — not gated behind a
  fixed hidden-until-threshold rule).
- Search box (Design decision 10): a text field over the canvas (or a top bar in the graph
  screen), reusing `NoteIndexEntry` title search; selecting a result pans/zooms the camera to
  that node and puts it in the same selected/enlarged state a tap-1 would.
- Pan/zoom: `Modifier.pointerInput { detectTransformGestures { ... } }` updating a
  `graphicsLayer` scale/translation (avoids recomposition per frame, only the layer transform
  changes).
- Node tap (Design decision 7): on tap-up, transform the tap point back into graph-space (invert
  current pan/zoom), hit-test against node positions (nearest node within `radius +
  touch-slop`):
  - tap on empty space → clear selection.
  - tap 1 on a node → select it: highlight + dim non-neighbors, enlarge that node's radius and
    label.
  - tap 2 on the same (now-enlarged) node → navigate via `note_view/{path}`.
- Empty/trivial states: 0 or 1 note → don't run layout, show a plain empty-state message
  instead of an empty canvas.

Entry points / navigation

- Global: new `DropdownMenuItem` "Note Graph" in `NoteListScreen.kt`'s existing `MoreVert` menu,
  next to "Show Deleted Notes" (`NoteListScreen.kt:259`). Navigates to `"graph"` (no center
  arg).
- Local: new footer action in `NoteFooterBar.kt`, alongside Backlinks. Navigates to
  `"graph?center=${Uri.encode(path)}"` (or a nav-args pattern matching how `note_view` passes
  its path arg) — same route, `center` present.
- Route registered in `ui/MainActivity.kt` alongside existing routes:
  `"graph?center={center}"` with an optional string nav arg.
- `GraphViewModel` (`viewmodel/graph/GraphViewModel.kt`) reads the optional `center` arg from
  `savedStateHandle`, calls `repository.observeGraph(center, hopDepth = 2)`, feeds
  `ForceLayoutEngine`, exposes node positions + selection state + a one-shot navigation event
  (match whatever existing VM→UI nav-event pattern another screen already uses, e.g.
  `NoteEditViewModel`) carrying the target note path for `MainActivity`'s nav controller.

Phasing

1. Data layer: `observeAllLinks()`, `GraphPositionEntity`/DAO, `GraphSnapshot` (incl. N-hop
   filter for local mode), unresolved-link filtering. Unit/instrumented-testable without any UI.
2. Layout engine: `ForceLayoutEngine` with Barnes-Hut from the start, as a standalone Kotlin
   class with its own unit tests (deterministic given a fixed seed).
3. Rendering: static (non-interactive) Canvas draw of a fixed snapshot, including label
   legibility-based rendering and degree-based sizing — verify draw correctness before wiring
   live layout ticks.
4. Interaction: pan/zoom, then the two-tap select→open flow, then the search box.
5. Both entry points: global (`NoteListScreen` overflow item) and local (`NoteFooterBar`
   action) — both are v1 scope, wire once the shared screen from steps 1–4 works standalone.
6. Position persistence wired end-to-end: load cache on open, upsert on convergence, verify
   reopen keeps prior node positions stable (no re-scramble) other than genuinely new/changed
   ones.
7. Polish (post-v1, not blocking): cluster coloring by tag, adjustable hop-depth slider for
   local mode.

Stretch: 3D mode

If 2D graph view ships and users want it, revisit 3D via `SceneView`
(github.com/SceneView/sceneview-android) — it's the one actively-maintained Compose-native
Filament wrapper, avoiding a WebView bridge. Reuse the same `ForceLayoutEngine` data (swap the
2D spring-embedder for a 3D variant — same forces, one more axis) and the same
`GraphSnapshot`/tap-to-note-path plumbing; only the renderer changes. Do not start this before
2D ships — it's a materially bigger dependency and testing surface for a visual upgrade, not a
new capability.

Testing

- `ForceLayoutEngine`: pure unit tests — converges (displacement → 0) on a small fixed graph,
  connected nodes end up closer than disconnected ones, deterministic given seeded initial
  positions, Barnes-Hut approximation gives layouts consistent with naive calculation on a small
  reference graph (cross-check test).
- Data layer: instrumented Room test — `observeAllLinks()` excludes edges touching
  soft-deleted notes; unresolved `id:` targets excluded from `GraphSnapshot.edges`; N-hop
  filter returns exactly the expected neighborhood on a fixed fixture graph (bidirectional,
  confirmed as a superset of `getBacklinks()` on the same fixture — Design decision 13);
  `GraphPositionDao` upsert/load round-trips correctly and follows note renames; a burst of
  rapid note/link writes collapses into one debounced `GraphSnapshot` emission, not one per
  write (Design decision 12); a note with `linksReady = false` is present in `GraphSnapshot`
  with that flag set, not silently excluded (Design decision 11).
- Nav: tap-hit-test math unit-testable in isolation (pure function: tap point + pan/zoom state
  + node positions → nearest node id); two-tap state machine (select → enlarge → open on same
  node vs. tap elsewhere clears selection) unit-testable without Compose.
