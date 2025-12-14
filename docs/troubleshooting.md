# Troubleshooting & Debugging Guide

This document contains universal debugging tips and post-mortems for difficult bugs encountered in the project. Always check this file before asking for input on new bugs.

## Post-Mortems

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
