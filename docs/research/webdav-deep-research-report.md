---
status: draft
owner: engineering
last_reviewed: 2026-02-24
---

# High‑Performance Three‑Way Sync over WebDAV for Nextcloud and Mobile Clients

## Method and scope

This research is grounded in the curated project list inside the provided GitHub repository (awesome‑webdav), then narrowed to implementations that either (a) actually perform bidirectional sync behaviour over WebDAV or (b) provide architectural patterns directly reusable in a mobile offline‑first sync client (e.g., local state journaling, conflict workflows, locking, batching).

The sync target is assumed to be a Nextcloud WebDAV endpoint, with Nextcloud as source of truth. The analysis relies on Nextcloud’s published WebDAV client APIs (PROPFIND basics and supported properties), WebDAV SEARCH support, and the WebDAV Trashbin endpoint because these features directly affect what “high‑performance delta sync” can realistically look like without non‑WebDAV side channels.

## Inventory of WebDAV client and sync implementations in awesome‑webdav

Within awesome‑webdav, the entries most relevant to “client + sync logic” fall into four practical buckets: general clients (interactive/CLI), “backup & sync” tools, mobile apps that sync or schedule transfers, and client libraries (especially those suitable for Kotlin/Java mobile stacks).

**General WebDAV clients and mounts (often used as building blocks, not full three‑way sync engines):** cadaver, cURL, davfs2, DaviX, GVFS mounting tools (e.g., gvfs‑mount), plus desktop clients like WinSCP and Cyberduck. These primarily offer file access and transfers; they typically do not implement three‑way sync semantics by themselves.

**Bidirectional / “sync‑like” tools where WebDAV is a supported backend:** rclone appears explicitly in awesome‑webdav and is notable because it has a dedicated bidirectional sync mode (“bisync”) and a WebDAV backend with explicit Nextcloud support as a vendor profile.

**Mobile apps in awesome‑webdav that matter for this problem statement:** the official Nextcloud mobile apps are listed, but the two third‑party entries in the list that are explicitly about WebDAV file operations and/or scheduled transfers are Round Sync (rclone‑powered, task‑capable) and EasySync (explicitly bidirectional sync to a WebDAV server).

**Libraries and implementation building blocks (mobile‑relevant):** dav4jvm is explicitly called out as a WebDAV/CalDAV/CardDAV library for Java/Kotlin, originally developed for DAVx⁵ and actively moving toward Kotlin Multiplatform via ktor. That makes it unusually relevant if you intend to implement a Kotlin‑first mobile sync engine while retaining performant HTTP features (connection pooling, etc.) via modern clients.

Finally, awesome‑webdav also contains performance/testing utilities such as Prestan (WebDAV performance benchmark suite/test server) and litmus (protocol compliance tests). These aren’t sync engines, but they’re useful for validating correctness and measuring latency/throughput characteristics of the WebDAV layer you build on.

## Comparative analysis of the strongest candidates

This section focuses on candidates that (a) truly implement “both sides change detection” plus reconciliation logic or (b) expose patterns directly reusable for a mobile, offline‑first three‑way synchronizer.

### rclone bisync over WebDAV

**Three‑way sync model**
Bisync’s key three‑way property is that it retains prior filesystem listings for both sides and compares the current state against that “base” on each run, classifying changes as New/Newer/Older/Deleted, then propagating changes bidirectionally. That retained “prior run listing” is effectively the third input in the classic three‑way merge model.

**Change detection signals (timestamps, etags, hashes)**
Bisync is designed to compare on modtime, size, and (optionally) checksums, and its documentation notes expanded support for comparing based on combinations of size/modtime/checksum depending on backend capabilities (important for WebDAV servers with imperfect timestamp fidelity).
For WebDAV specifically, rclone’s backend configuration explicitly supports “Nextcloud” as a vendor option when defining a WebDAV remote.

**Conflict detection and resolution**
Bisync supports both “keep both” conflict handling and winner‑picking strategies. The documentation describes conflict auto‑resolution flags (e.g., picking the newer file by modtime as winner) as well as behaviour where both files are renamed and preserved when no winner is selected.
Conceptually, this matters for your mobile client because it demonstrates a pragmatic conflict approach for opaque binary files: preserve both versions (with deterministic naming) unless policy dictates otherwise.

**Efficient recursion and partial sync**
Bisync itself operates on two “paths,” which maps cleanly to “sync a subtree” on a Nextcloud WebDAV hierarchy (e.g., a chosen folder). This aligns with a mobile constraint: do not attempt to sync the entire account by default; sync specific roots.

**Performance characteristics**
rclone’s approach is inherently “list → diff → transfer,” which can be high‑performance if listing is optimised (batching, depth usage, concurrency) and if you avoid slow hashes unless needed. Bisync’s documentation discusses checksum usage and options meant to avoid slow hashing except when required.
The primary performance risk for WebDAV backends is metadata enumeration cost (many PROPFINDs) rather than payload transfer. rclone’s ecosystem acknowledges this in WebDAV contexts (e.g., discussions around PROPFIND behaviour), underscoring that listing strategy is the dominant lever.

**Mobile applicability**
rclone is extremely mature as a sync engine, but it is not mobile‑native: integrating it into a Kotlin/Swift app usually means embedding a Go core (or running it out‑of‑process), which affects binary size, battery, and platform integration. The upside is that the algorithm is well documented and has robust conflict modes.

### EasySync

**Three‑way sync model**
EasySync’s README states it syncs “in both directions,” explicitly propagating uploads/deletes from phone to server and from server to phone.
From the documentation presentation, EasySync appears to behave like a scheduled bidirectional folder sync tool (periodic remote checks to preserve battery), which is consistent with a three‑way sync design where the app must maintain some remembered prior state (at minimum: what it last observed on each side) to decide what changed.

**Change detection signals**
EasySync explicitly discusses ETag support as a compatibility factor across WebDAV servers (it even notes some servers as “no etag support”), and it positions timestamp preservation (notably with Nextcloud) as a feature.
This strongly suggests a “metadata‑based” change detector rather than content hashing (ETag + timestamps), which is typical for high‑performance media sync.

**Conflict detection and resolution**
The publicly visible docs emphasise bidirectional propagation (upload/delete both ways) but do not document a detailed conflict strategy (e.g., last‑write‑wins vs. “keep both”). In practice, this means you would need to inspect code paths (not available via the sources captured here) or reproduce with tests.
What *is* documented is that server‑side checks are periodic (“checked periodically” to preserve battery), implying that conflict avoidance is partly achieved via scheduling and user‑initiated “Sync now.”

**Efficient recursion and partial sync**
EasySync’s scope is explicitly constrained to media and download directories and to a chosen folder on the DAV server, which is a practical “partial tree sync” model aligned with mobile storage constraints.

**Performance characteristics**
EasySync documents a concrete Nextcloud performance tip: using a device/app password instead of the user password can provide a large speedup, and it references reports of Nextcloud DAV slowness.
Nextcloud’s own user manual independently recommends app passwords for third‑party WebDAV clients and states this improves security and “increases performance significantly.”
From a mobile engineering perspective, EasySync’s own behaviour (“periodically” checking remote changes) is itself a performance/battery optimisation.

**Documentation and maturity**
EasySync is actively released (e.g., v1.22 dated Dec 7, 2025 in the repository UI) and has an issue tracker and several contributors, indicating ongoing maintenance.

### Round Sync

**What it is, relative to three‑way sync**
Round Sync is described as an Android “cloud file manager” powered by rclone, and it explicitly supports task automation for repeated operations.
This matters because Round Sync is less “a sync algorithm you copy” and more “a proven mobile integration pattern for embedding rclone‑class capabilities (including WebDAV backends) into Android,” including multi‑architecture builds and workflow hooks.

**Change detection and conflicts**
Round Sync delegates file operations to rclone (“Without rclone, there would not be Round Sync”), so the sync semantics depend on which rclone operation a task runs (copy/sync/bisync, etc.). The project description confirms the tight coupling but does not document a bespoke Round Sync change detector.

**Mobile applicability**
Round Sync demonstrates concrete Android integration constraints you will face: Storage Access Framework support for SD cards/USB, a task model, intents for triggering work, and broad CPU architecture support. These are directly applicable to designing a robust mobile sync client.

### Joplin and floccus as “sync‑engine pattern references”

Your target is file sync, but two “WebDAV as storage layer” applications in the ecosystem are valuable because they document (at the design level) how offline‑first apps structure sync state and conflict handling.

**Joplin’s patterns (offline‑first + journaling + conflict notebook)**
Joplin’s sync specification describes an offline‑first client that uploads changes quickly to reduce conflicts and polls periodically for remote changes. It also documents a layered architecture: a generic synchroniser, a sync‑target adapter, and a file‑like API driver abstraction.
For conflicts, Joplin explicitly creates a Conflict notebook, copies the local version there, and then replaces the local note with the remote version, leaving manual merge to the user (or plugins).
Even though Joplin syncs “items” (notes/resources) rather than arbitrary file trees, the core idea—local database + per‑object sync state + deterministic conflict artefacts—is directly transferable to a mobile file sync client that must survive offline edits.

**floccus patterns (server lock + failsafe + selectable sync strategies)**
floccus’ guides explain it maintains a cache and mapping between server and local bookmark structures, and it supports multiple “sync strategies,” including merge and one‑way modes.
Its FAQ and community guidance describe a lock mechanism to avoid concurrent server mutations and provide user‑visible recovery flows (override locks, failsafe preventing mass deletion, manual push/pull modes).
For file sync, these patterns are highly relevant: lock/lease design, failsafes for catastrophic deltas, and user‑selectable policies for which side wins.

## Ranked shortlist of best candidates from awesome‑webdav

The ranking below prioritises (1) actual three‑way/bidirectional sync semantics, (2) performance‑relevant design evidence, and (3) reusability for a mobile client syncing with Nextcloud.

**Top tier**

**rclone bisync**
Best documented bidirectional algorithm with explicit three‑way characteristics (retained prior listings), mature conflict handling knobs, and a WebDAV backend that explicitly supports Nextcloud as a target. Strongest choice if you want a battle‑tested algorithm to emulate (or to embed), especially for large hierarchies.

**EasySync**
Best “mobile‑native, WebDAV‑first, bidirectional folder sync” codebase in the curated list, with explicit attention to ETag/timestamp compatibility and mobile battery trade‑offs. Less evidence (from available docs) about large‑metadata scaling and nuanced conflict handling, but the scope alignment (Android + planned background behaviour) is excellent.

**Second tier**

**Round Sync**
Most relevant as an Android integration blueprint for rclone‑class capabilities (tasks, SAF/SD card support, intents, multi‑ABI distribution). Not itself the “sync algorithm,” but highly reusable architecture for a production‑grade Android client wrapper around a sync core.

**Pattern references and building blocks**

**dav4jvm**
Not a sync engine, but one of the most mobile‑relevant WebDAV libraries in the list: Java/Kotlin, with an explicit transition toward Kotlin Multiplatform (ktor). Useful if you implement your own sync core and need a modern WebDAV client stack.

**Joplin** and **floccus**
Both are valuable for conflict and safety patterns: local state tracking, conflict artefacts, lock/failsafe recovery. However, their sync domain is application‑specific data rather than arbitrary file trees.

## Recommended mobile‑friendly three‑way sync approach for Nextcloud via WebDAV

A robust, high‑performance sync client for Nextcloud over WebDAV needs two things simultaneously:

1) **A three‑way model** (base snapshot + local current + server current) with explicit conflict semantics.
2) **A high‑performance delta discovery strategy** that avoids full recursive listings on every cycle.

The strongest practical recommendation is:

**Use an rclone‑bisync‑style “retain prior listing and diff” algorithm, but tailor the WebDAV delta discovery to Nextcloud’s specific WebDAV capabilities (SEARCH + Trashbin + rich PROPFIND properties), then implement mobile‑native scheduling and offline journaling.**

### Core state model

Maintain a local SQLite database representing the last known server state for the synced subtree:

- `fileid` (Nextcloud’s stable per‑instance file id)
- `path` (server href)
- `etag` and `getlastmodified`
- `size` and optionally `{http://owncloud.org/ns}checksums` when available
Nextcloud explicitly documents support for `oc:fileid`, `d:getetag`, `d:getlastmodified`, folder sizing, and checksums as WebDAV properties retrievable via PROPFIND.

This database is your “third input” for three‑way reasoning, mirroring the “prior listing” concept in rclone bisync.

### Delta discovery on the server side

Use a tiered strategy:

**Fast path: WebDAV SEARCH for “modified after last sync”**
Nextcloud documents WebDAV SEARCH support (RFC 5323) and includes an explicit example: retrieving all files last modified after a given timestamp using `d:getlastmodified` and a `d:gt` predicate with an ISO timestamp literal.
Use this to discover *new or content‑modified* files without enumerating the whole tree.

**Deletion path: WebDAV Trashbin PROPFIND**
Nextcloud exposes a WebDAV trashbin endpoint and documents that a PROPFIND to `/remote.php/dav/trashbin/USER/trash` lists deleted items and returns extra properties including original location and deletion time.
This is a crucial optimisation: deletions are otherwise expensive to detect without a full listing compare.

**Structural change gap (renames/moves)**
SEARCH‑by‑modification‑time and trashbin‑listing will not always capture “pure moves/renames” if the server does not update `getlastmodified` for such operations. Nextcloud’s SEARCH documentation shows that file ids (`oc:fileid`) are searchable and that you can query “get a file by id,” which provides a tool to re‑locate known files.
A practical approach is:
- treat “rename/move detection” as a periodic consistency sweep for directories (not every delta tick), or
- on suspicious conditions (e.g., a locally known file disappears and is not in trashbin), run a targeted SEARCH by fileid to relocate it.

This is one of the key differences between a “generic WebDAV” sync and a Nextcloud‑optimised one: stable file ids make rename recovery feasible without full re‑enumeration.

### Uploads and conflict safety

For correctness under offline edits, you need **optimistic concurrency control**:

- Store the last seen server ETag for each file in the local database.
- When uploading a local modification back to the server, use conditional requests (If‑Match / If‑None‑Match) so the server rejects overwrites if the server version has changed.

At the protocol level, conditional requests are defined in HTTP conditional request specs (e.g., RFC 7232), and WebDAV also extends conditional request mechanics (RFC 4918).
In practice with Nextcloud, “412 Precondition Failed” errors appear when clients send If‑Match/If‑None‑Match conditions that fail, which is consistent with using ETags as the conflict guard.

This gives you a clean conflict signal:
- If upload succeeds: update local “server snapshot” row (etag, lastmodified, etc.).
- If server rejects with 412: conflict → resolve by policy:
  - default “keep both” (rename local copy as conflict artefact, then download server version), matching rclone’s default non‑destructive conflict model, or
  - “winner by modtime” for certain directories like camera roll, matching rclone’s configurable approach.

### Performance and bandwidth tactics

These tactics are consistently supported by Nextcloud’s documented WebDAV behaviour:

- **Ask only for needed PROPFIND properties** (etag, fileid, lastmodified, size, checksums) rather than defaulting to large property sets. Nextcloud explicitly documents default PROPFIND returns and how to request additional properties.
- **Use Depth deliberately**: Nextcloud documents `Depth: 0` to request properties for just the folder without listing contents, which can be used for lightweight “folder probes.”
- **Prefer SEARCH for time‑bounded deltas**: avoids full tree recursion for “what changed since T.”
- **App password for performance**: Nextcloud’s manual explicitly recommends application passwords for third‑party WebDAV clients and states it can increase performance significantly. EasySync echoes this with a concrete “almost 10x” speedup claim when using a device password.

For mobile‑specific performance, adopt patterns demonstrated by EasySync and Joplin:
- upload quickly after a local edit when feasible to reduce conflict probability, but
- schedule downloads/polls intelligently to preserve battery and avoid constant background churn.

### How to measure performance credibly

If you don’t have published benchmarks for a candidate project, you can still measure and infer performance in reproducible ways:

- Use a WebDAV benchmark suite like Prestan to characterise baseline server+network behaviour under specific request patterns (PROPFIND depth, GET/PUT sizes).
- Use litmus to validate protocol compliance of the specific WebDAV features you rely on (notably conditional requests and depth semantics), reducing “performance surprises” caused by server incompatibilities.
- On Nextcloud specifically, validate SEARCH query costs (latency and result sizes) for your real folder structure because SEARCH queries can be `depth=infinity` scoped and may return large multistatus payloads.

## Brief implementation plan for adapting the recommended approach into a mobile app

This plan assumes you implement a native sync engine (rather than embedding rclone), but you borrow rclone bisync’s *algorithmic structure* and Round Sync’s mobile integration lessons.

### Architecture choices

On Android, a Kotlin stack is appropriate because:
- you can use a mature WebDAV client library such as dav4jvm (noting its ongoing shift toward Kotlin Multiplatform via ktor), or you can implement your own WebDAV layer using ktor/OkHttp directly.
- Round Sync demonstrates real‑world constraints you must handle: multi‑ABI packaging, SAF integration, and a task model/invocable service for scheduled work.

### Step‑by‑step build plan

1) **Define the sync root(s)**
   Restrict sync to one or more explicit server folders, matching the practical “partial sync” models seen in Nextcloud’s official guidance and third‑party tools.

2) **Local metadata store + operation log**
   Implement:
   - `server_snapshot(fileid, path, etag, lastmodified, size, checksum?, is_dir, deleted_flag, last_seen_time)`
   - `local_ops(op_id, path, type=create/update/delete/move, base_etag, base_fileid, local_mtime, queued_time, retry_state)`
   This mirrors the “retained listing” concept of bisync and the “local state table” pattern described by Joplin’s sync spec.

3) **Initial sync flow**
   - Enumerate the subtree via iterative PROPFIND (Depth 1 BFS) requesting only a minimal property set including `oc:fileid`, `d:getetag`, and `d:getlastmodified`.
   - Download file payloads on demand (e.g., for “offline available” selections) rather than automatically mirroring everything, unless the user explicitly requests full mirroring.

4) **Delta sync flow (repeatable cycle)**
   - Upload local_ops eagerly when network is available, using conditional requests tied to stored ETags to detect conflicts. Treat 412 as conflict.
   - Pull server deltas using:
     - SEARCH “modified after last_sync_time” to discover changed/new files.
     - Trashbin PROPFIND to discover deletions (and original locations) since last_sync_time.
   - Reconcile against `server_snapshot` to determine new/updated/deleted/moved.

5) **Conflict handling policy**
   Implement two modes, borrowing from rclone and Joplin patterns:
   - Default: **keep both** (rename local file as conflict artefact, preserve server version).
   - Optional: **winner by timestamp** for selected folders (e.g., camera roll) where last‑writer‑wins is acceptable.

6) **Mobile robustness**
   - Use OS scheduling (e.g., Android WorkManager) for periodic server polling, aligning with EasySync’s “periodic remote checks to preserve battery” model.
   - Implement resumable upload for large files using Nextcloud’s chunked upload documentation if you go beyond pure WebDAV PUT semantics (Nextcloud documents chunked upload as part of its WebDAV client APIs).
   - Implement exponential backoff and “sync on Wi‑Fi/charging” policies (explicitly user‑controllable), consistent with mobile expectations noted in EasySync’s FAQ.

7) **Testing and performance validation**
   - Run litmus against a test endpoint to validate the WebDAV behaviours you rely on; run Prestan to benchmark latency and throughput under realistic request mixes.
   - For Nextcloud specifically, validate the practical performance impact of using application passwords (documented by Nextcloud and echoed by EasySync) as part of baseline setup guidance in your app.
