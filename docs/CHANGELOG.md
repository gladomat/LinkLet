# Changelog

## [Unreleased]
- `NoteEditViewModel` records edit history, exposes `undo()`, and includes tests ensuring toolbar interactions revert gracefully.
- Note editor toolbar now uses icon-only buttons (heading shortcuts, inline formatting, list controls, indent toggles, source block, plus the new undo action) with ripple feedback and a floating band that sits atop the keyboard while keeping the text field visible.
- Keyboard-aware styling now respects IME insets so editing remains usable even when the toolbar is active.
 - Note list screen now supports global search: a persistent search bar filters notes live by title, body text, and `#+filetags:` values, and matching items display a short snippet of the surrounding content in the list.
