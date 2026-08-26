# Graph Layout Engine

## Purpose

Pure-Kotlin force-directed layout math for the note graph view (`docs/plans/2026-07-06-note-graph-view.md`). Turns a node/edge list into 2D positions. No UI, no Android dependency, no coroutines - the ViewModel owns the tick loop and threading; this package only owns the physics.

## Ownership

- `Vector2` — plain 2D vector. Deliberately not `androidx.compose.ui.geometry.Offset`: keeping this package Compose-free is what makes it testable on the plain-JVM tier without Robolectric. Convert to `Offset` only in `ui/screens/graph/GraphScreen.kt`.
- `QuadTree` — Barnes-Hut quadtree; approximates pairwise node repulsion in O(n log n) instead of O(n²). Needed because this app's real vault size (500-2000 notes) makes naive O(n²) repulsion risk visible per-tick stutter on the JVM - this was built in from the start, not added later as a scale fix. Masses are weighted floats, not point counts: `build(points, masses)` takes a per-point mass, and `build(points)` alone means unit masses.
- `ForceLayoutEngine` — force-directed simulation (`seed()` + repeated `step()` until `isConverged()`) using `QuadTree` for repulsion. Inverse-square repulsion + linear springs, deliberately *not* textbook Fruchterman-Reingold — see Local Contracts. Deterministic: `seed()`'s fresh-node placement is a fixed spiral formula, never `Math.random()` or `kotlin.random.Random` - required for reproducible unit tests and for two callers seeding the same node list to agree.

## Local Contracts

- Stay pure Kotlin. Do not import anything from `androidx.compose.*` or `android.*` into this package - that's the whole point of keeping it separate from `ui/screens/graph/`.
- `QuadTree` caps subdivision depth (`MAX_DEPTH`) instead of recursing indefinitely on exactly-coincident points - don't remove that guard; two nodes seeded or animated to the same coordinates would otherwise stack-overflow the insert.
- Repulsion must stay **inverse-square** (`QuadTree`) — short-range. An inverse-linear law (textbook Fruchterman-Reingold) decays slowly enough that the whole node set's combined push outweighs the springs, stretching every edge in proportion to the graph's overall size: measured at ~16x typical node spacing on a 300-node vault, i.e. edges spanning the entire canvas. `ForceLayoutEngineTests` pins this with a spacing-ratio assertion; treat a failure there as "linked notes drifted apart", not as a threshold to relax.
- `minTemperature` must stay strictly below `convergenceEpsilon`. Per-tick displacement is capped at the temperature, so a floor at or above the epsilon makes `isConverged()` unable to ever trip on displacement and every run burns all `maxIterations` ticks jittering in place at equilibrium.
- `seed()` reheats in proportion to the fraction of nodes with no cached position. A fully cached reopen starts effectively frozen and converges on the first tick (Design decision 4); seeding at full temperature regardless would fling a settled graph by up to `initialTemperature` px per node the moment the user reopens it.
- Edges get extra spring stiffness when their weaker end has few links (`leafSpringBoost`). A leaf is held by one spring but pushed out by every other node, and that repulsion grows with the vault while the spring does not — without the boost, degree-1 notes drift out towards the rim. Keep the boost decaying to none as degree rises: past roughly 25 at degree 0, every connected node lands at the same radius and the graph reads as one undifferentiated blob.
- Notes with **no** links are meant to settle on an outer ring, outside the connected core — that is the graph telling you they are unlinked, not a layout defect. Don't "fix" it by pulling orphans inward; both properties are pinned by tests.
- The simulation is connectivity-weighted, not uniform: repulsion mass, gravity and each edge's ideal spring length all scale with node degree (log-damped). Uniform forces settle every peripheral node at the same equilibrium radius, which renders as one dense ring around a crowded middle instead of separated clusters. Keep new forces degree-aware and keep the damping logarithmic - linear weighting lets a single high-degree hub blow the whole layout apart.
- `ForceLayoutEngine.seed()` must stay side-effect-free and depend only on its arguments (node list, cached positions) - callers (`GraphViewModel`) rely on re-seeding from a previous session's cached positions to resume a settled layout instead of re-scrambling it.

## Verification

`tests/com/gladomat/linklet/data/graph/` (`ForceLayoutEngineTests`, `QuadTreeTests`) - pure JUnit, no Robolectric, run on every host including Apple Silicon (not part of the arm64-skip set).
