# Troubleshooting & Debugging Guide

This document contains universal debugging tips and post-mortems for difficult bugs encountered in the project. Always check this file before asking for input on new bugs.

## Post-Mortems

### Storage Rename & Share Regression (2026-12-14)
**Issue:**  
We kept tweaking NoteViewScreen UI and repository consumers while rename/export failures and sync thrashing persisted, because that’s where the symptoms appeared.

**Root Cause:**  
The malfunction lived in the storage/repository layer: SAF-based rename copied whole files, deleted blocking directories, and duplicate note creation rewrote IDs with newline corruption. By assuming UI owned the problem, we ignored the actual side-effect layer for far too long.

**Resolution:**  
We wrote focused storage/repository tests, exercised rename/duplicate paths against fake storage, and then fixed validation plus atomic IO there. Once storage behaved, every UI symptom vanished.

### Merge Strategy & Sync Engine (2025-12-14)
**Issue:**  
We struggled to identify why non-overlapping edits were causing conflicts or incorrect merges in the `SyncEngine`. The debugging process was prolonged because we were running full integration tests (`SyncEngineTests`) which involve database state, file I/O, and mocked remote providers.

**Root Cause:**  
The issue originated in the `MergeStrategy` logic itself, specifically how it handled diffs and newlines. However, because we were observing it through the lens of the full `SyncEngine`, the signal was lost in the noise of system state setup/teardown.

**Resolution:**  
We created a standalone, isolated test (`MergeStrategyDebugTest.kt`) that tested *only* the `MergeStrategy` class with hardcoded string inputs. This stripped away all system complexity (Room DB, Coroutines, File System) and revealed the algorithmic flaw immediately.

## Universal Debugging Tips

### 🌟 Isolate Pure Logic from System State
**When to use:**  
When debugging complex algorithms (e.g., merging, parsing, state machines) that are embedded in larger stateful systems (e.g., SyncEngine, ViewModel, Repository).

**The Trap:**  
Debugging "in-place" within the full system is slow. You waste time setting up the environment (DB, mocks) for every run, and you can't easily reproduce edge cases (like specific newline combinations).

**The Fix:**  
1. **Extract**: Identify the "pure" component (e.g., `MergeStrategy`, `RegexParser`).
2. **Isolate**: Create a temporary or permanent unit test file (e.g., `MergeStrategyDebugTest.kt`).
3. **Hardcode**: Pass raw, hardcoded data (strings, numbers) directly into the component.
4. **Iterate**: Run this lightweight test repeatedly. It runs in milliseconds and gives immediate feedback.

> "If you can't test it in isolation with a simple main function or unit test, your architecture might be too coupled, or you're looking at the wrong layer."

### 🔍 Start at the Layer Owning the Side-Effect
**When to use:**  
Whenever a bug shows up in UI/ViewModel code but the operation mutates files, SAF documents, the network, or the DB.

**The Trap:**  
Patching the consumer closest to the crash (usually UI) hides the fact that the lower layer (storage, sync, repository) owns the side-effect. You end up shipping band-aids while the root issue keeps resurfacing.

**The Fix:**  
1. Identify which layer actually touches the external system.  
2. Inspect that implementation first and add logs or unit tests there.  
3. Reproduce using the narrowest harness (fake storage, in-memory DB) before touching UI.  
4. Only return upstream once the side-effect layer is verified.

> "Follow the side-effect, not the crash dialog. Bugs born in IO layers rarely die in UI code."

### Interface Fragility in Tests (2025-12-14)
**Issue:**
Adding `getAllTags` to `INoteRepository` caused a cascade of build failures across 4 different test files (`NoteViewViewModelTests`, `NoteListViewModelTests`, etc.), delaying verification.

**Root Cause:**
We used ad-hoc `object : INoteRepository` implementations in every test file instead of a single shared `FakeNoteRepository`. Changing the interface meant fixing 10+ anonymous objects manually.

**Resolution:**
We manually updated all stubs. The long-term architectural fix is to introduce a shared `FakeNoteRepository` module to centralize test doubles.

### 🏗️ Centralize Test Doubles
**When to use:**
When you find yourself updating multiple test files for a single interface change.

**The Trap:**
Defining `val repo = object : IRepo { ... }` inside every test method or class. It's quick to start but deadly to maintain.

**The Fix:**
Create a single `FakeRepo` class in your `test/` source set that implements the interface. Use this fake in all tests. When the interface changes, you update exactly one file (the Fake).

> "Don't repeat yourself (DRY) applies to test code too. If you copy-paste a mock setup 10 times, you'll pay the price 10 times when the API changes."
