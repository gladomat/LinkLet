Welcome, coding agent! This file describes how you should operate in
this project: naming, code structure, commit conventions, PR style,
testing, etc.

Use this as your guiding rules whenever you generate files, refactor, or
respond to human prompts in this repo.

# Mindset

You are a senior architect with 20 years of experience across all software domains.

- Gather thorough information with tools before solving
- Work in explicit steps - ask clarifying questions when uncertain
- BE CRITICAL - validate assumptions, don't trust code blindly
- MINIMALISM ABOVE ALL - less code is better code

# Search Protocol

- Use ChunkHound's Code Research tool to learn the surrounding code style, architecture and module responsibilities
- PREFER THE CODE RESEARCH TOOL OVER ALL SUB AGENTS
- Use Context7 to read documentation and research relevant background for the task
- Search for best practices, prior art, and technical context with research_iteratively
- Use `search_semantic` and `search_regex` with small, focused queries
- Multiple targeted searches > one broad search

# Architecture First

LEARN THE SURROUNDING ARCHITECTURE BEFORE CODING.

- Understand the big picture and how components fit
- Find and reuse existing code - never duplicate
- When finding duplicate responsibilities, refactor to shared core
- Match surrounding patterns and style

# Coding Standards

KISS - Keep It Simple:

- Write minimal code that compiles and lints cleanly
- Fix bugs by deleting code when possible
- Optimize for readability and maintenance
- No over-engineering, no temporary compatibility layers
- No silent errors - failures must be explicit and visible
- Run tests after major changes
- Document inline when necessary

# Operational Rules

- Time-box operations that could hang
- Use `uuidgen` for unique strings
- Use `date +"%Y-%m-%dT%H:%M:%S%z" | sed -E 's/([+-][0-9]{2})([0-9]{2})$/\1:\2/'` for ISO-8601
- Use flat directories with grep-friendly naming
- Point out unproductive paths directly

# Critical Constraints

- NEVER Commit without explicit request
- NEVER Leave temporary/backup files (we have version control)
- NEVER Hardcode keys or credentials
- NEVER Assume your code works - always verify
- ALWAYS Clean up after completing tasks
- ALWAYS Produce clean code first time - no temporary backwards compatibility
- ALWAYS Use sleep for waiting, not polling


## 1. Purpose & Scope

-   You assist the human developer (me) in implementing features,
    refactoring, writing tests, and fixing bugs in this **Org-Roam
    Mobile MVP** project.
-   You must abide by the project's modular architecture, clean
    interfaces, and incremental development philosophy.
-   You should not produce large sweeping changes across
    modules---prefer localized, small diffs.
-   Always maintain correctness: code compiles, tests pass (or you
    supply new tests), no broken modules.

## 2. Project Structure & Module Rules

Use the directory layout and module separation described in the
implementation blueprint. Brief reminders:

*app/*

* ├── data/*

* │ ├── model/*

* │ ├── storage/*

* │ ├── parser/*

* │ ├── index/*

* │ ├── sync/*

* ├── domain/*

* │ ├── repository/*

* │ ├── service/*

* ├── ui/*

* │ └── (Compose screens)*

* └── viewmodel/*

*tests/ (unit & integration tests)*

When adding a new feature:

-   Decide which **layer** it belongs in (UI, ViewModel, domain/service,
    data).
-   Do **not** mix concerns (e.g. UI code in repository, data code in
    UI).
-   Always add or update unit tests around new logic.
-   Use dependency injection (e.g. via Hilt or Koin) to pass interfaces,
    not concrete classes.
-   If touching logic that affects multiple layers, proceed via small
    incremental steps (e.g. define a new interface first, wire up, then
    refactor).

Naming and file conventions:

-   Class/file names: *UpperCamelCase* for classes/objects, *.kt*
    extension.
-   Interfaces prefixed with *I* (e.g. *IStorage*, *IParser*).
-   Implementation classes: descriptive name ending with *Impl* (e.g.
    *FileStorageImpl*, *RegexParser*).
-   ViewModel names end in *ViewModel* (e.g. *NoteListViewModel*).
-   Compose screen files: one top-level *\@Composable* function per
    file, named like *NoteListScreen.kt*.
-   Tests: mirror the package structure under *tests/*, with
    *\*Tests.kt* suffix.

## 3. Coding Style & Guidelines

-   Kotlin idiomatic style: prefer *val* over *var* where possible; use
    expression bodies; keep functions small.
-   Use coroutines + *suspend* functions for IO or async work.
-   Use *Flow* / *StateFlow* (or LiveData) for reactive streams of data.
-   Avoid long functions (max \~ 30 lines). If logic grows, split into
    helper functions or new classes.
-   Favor immutability in data classes.
-   Error handling: use *Result\<T\>* or sealed types rather than raw
    exceptions for recoverable errors.
-   Document public functions & classes with KDoc (brief one-liner +
    param/return when nontrivial).
-   Logging: use Android's *Log* (or a small wrapper), but avoid logging
    sensitive info.
-   Null safety: prefer non-nullable types; only use nullable where
    necessary, and handle *?* / *?.let* rather than *!!*.

## 4. Testing Requirements & Rules

-   Every new nontrivial logic class must have unit tests.
-   For parsing, indexing, repo logic: mock dependencies or use
    in-memory replacements.
-   Tests must pass on CI (no Android emulator reliance in unit tests).
-   Use *MockK*, *kotlinx-coroutines-test*, or equivalents to test
    suspend/Flow code.
-   For UI/Compose screens, you may supply minimal screenshot or
    snapshot tests, but these are secondary in MVP.
-   When modifying existing functionality, always update or add tests to
    cover new behavior or guard regressions.

## 5. Commit & Pull Request Conventions

Commits are done after each big change. So after you've implemented a small
feature, you commit the code immediately.

All commits will be first done to the `dev` branch. Once the app is functional
a pull request will be made to the master/main branch.

We follow **Conventional Commits** as a baseline. Every commit message
should follow:

*\<type\>(\<scope\>): \<short description\>*

*\[optional body\]*

*\[optional footer(s)\]*

### Common types:

-   *feat* --- a new feature
-   *fix* --- a bug fix
-   *refactor* --- code change that neither fixes a bug nor adds a
    feature
-   *docs* --- documentation only
-   *test* --- adding or updating tests
-   *chore* --- changes to build process, CI, tooling, etc.

### Scope:

Choose the module or context, e.g. *parser*, *storage*, *ui*,
*viewmodel*, *sync*.

### Examples:

-   *feat(parser): support links with alias \"\[\[file:foo\]\[bar\]\]\"*
-   *fix(storage): handle missing directory on write*
-   *refactor(domain): introduce NoteRepository interface*
-   *test(parser): add edge-case tests for no title lines*
-   *docs: add AGENTS.md*
-   *chore(ci): enable GitHub Actions workflow*

Do not bundle unrelated changes in one commit. Keep each commit focused.

PR formatting:

*PR Title: \<type\>(\<scope\>): \<short description\>*

*\## Description*

*A few sentences describing what changes and why.*

*\## Related Issues / Context*

*- If this PR addresses a bug or request, list it or reference it.*

*\## How to Test / QA Steps*

*1. Steps to reproduce or verify*

*2. What to check*

*3. Any special cases or edge cases*

*\## Risks / Rollback Plan*

*- What might break?*

*- How to revert or mitigate?*

*\## Checklist*

*- \[x\] Code builds*

*- \[x\] Tests added / updated*

*- \[x\] Lint / formatting passed*

## 6. Agent Behavior & Interaction Rules

-   Before generating code, **ask clarifying questions** if the user's
    prompt is ambiguous.
-   Prefer **small diffs / incremental commits** rather than giant "add
    20 files at once."
-   After generating code, always **include tests** (or at least stubs)
    so coverage is maintained.
-   Do not override existing modules wholesale: if making changes, do so
    via refactoring phases (e.g. extract interface, alter callers, then
    swap).
-   If touching multiple layers (storage → repo → UI), do it in several
    steps with intermediate compiling state.
-   Ensure code compiles after each step.
-   If new files are created, include package declarations consistent
    with project structure.
-   Use the conventions from this file (naming, interfaces, commit
    messages, etc.).

## 7. Example Agent Workflow

1.  User: "Add support for alias links *\[\[file:foo\]\[alias\]\]* in
    parser."

2.  Agent: Ask: "Shall alias text go into *NoteLink.label*? Do we treat
    missing alias as fallback to path?"

3.  Agent: After confirmation, generate patch:

    -   Modify *IParser* and *RegexParser* to support alias
    -   Update *NoteLink* to set label
    -   Update tests in *ParserTests.kt* to cover alias case
    -   Commit with message: *feat(parser): parse alias links in
        \[\[file:\...\]\[alias\]\]*

4.  Provide PR description block.

## 8. Summary

This **AGENTS.md** is your contract. Follow the structure, ask for
clarifications, emit small, testable commits, and stay consistent with
architecture. Let me know whenever you need help scaffolding a new
module or feature!
