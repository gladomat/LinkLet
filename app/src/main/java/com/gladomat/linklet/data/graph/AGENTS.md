# Graph Layout Engine

## Purpose

Pure-Kotlin force-directed layout math for the note graph view (`docs/plans/2026-07-06-note-graph-view.md`). Turns a node/edge list into 2D positions. No UI, no Android dependency, no coroutines - the ViewModel owns the tick loop and threading; this package only owns the physics.

## Ownership

- `Vector2` — plain 2D vector. Deliberately not `androidx.compose.ui.geometry.Offset`: keeping this package Compose-free is what makes it testable on the plain-JVM tier without Robolectric. Convert to `Offset` only in `ui/screens/graph/GraphScreen.kt`.
- `QuadTree` — Barnes-Hut quadtree; approximates pairwise node repulsion in O(n log n) instead of O(n²). Needed because this app's real vault size (500-2000 notes) makes naive O(n²) repulsion risk visible per-tick stutter on the JVM - this was built in from the start, not added later as a scale fix.
- `ForceLayoutEngine` — Fruchterman-Reingold simulation (`seed()` + repeated `step()` until `isConverged()`) using `QuadTree` for repulsion. Deterministic: `seed()`'s fresh-node placement is a fixed spiral formula, never `Math.random()` or `kotlin.random.Random` - required for reproducible unit tests and for two callers seeding the same node list to agree.

## Local Contracts

- Stay pure Kotlin. Do not import anything from `androidx.compose.*` or `android.*` into this package - that's the whole point of keeping it separate from `ui/screens/graph/`.
- `QuadTree` caps subdivision depth (`MAX_DEPTH`) instead of recursing indefinitely on exactly-coincident points - don't remove that guard; two nodes seeded or animated to the same coordinates would otherwise stack-overflow the insert.
- `ForceLayoutEngine.seed()` must stay side-effect-free and depend only on its arguments (node list, cached positions) - callers (`GraphViewModel`) rely on re-seeding from a previous session's cached positions to resume a settled layout instead of re-scrambling it.

## Verification

`tests/com/gladomat/linklet/data/graph/` (`ForceLayoutEngineTests`, `QuadTreeTests`) - pure JUnit, no Robolectric, run on every host including Apple Silicon (not part of the arm64-skip set).
