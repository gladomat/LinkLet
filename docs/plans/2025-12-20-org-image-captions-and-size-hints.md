# Org Image Captions and Size Hints Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Render captions and optional width hints for Org inline images (`[[file:...]]`) with minimal regressions.

**Architecture:** Keep metadata detection in the UI layer using `OrgInlineImageDetector` so we don’t refactor the parser/AST. Extend the detector to recognize described image links and parse caption/width metadata, then apply it in `OrgBlockRenderers`/`OrgInlineImageBlock`.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric unit tests (no emulator), existing inline image pipeline.

## Requirements (MVP)

- Caption sources:
  - `#+CAPTION:` line above the image link (within same paragraph) sets caption.
  - Otherwise, for described bracket links `[[file:img.png][caption]]`, use the bracket description as caption (trimmed; ignore if blank).
  - If both exist, `#+CAPTION:` wins.
- Size hints:
  - Support `#+ATTR_ORG: :width <N>` in dp (`200` or `200dp`) or percent (`80%`).
  - Invalid values are ignored (fallback to default fill-width).
- Defaults remain fit-to-width when no metadata.
- No regression for non-image described links.

## Task 1: Document + tests for described-image caption detection

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageDetector.kt`
- Test: `tests/com/gladomat/linklet/ui/components/OrgInlineImageDetectorTests.kt`

**Step 1: Write the failing test**

Add tests:

```kotlin
@Test
fun `detectInlineImageCandidate accepts described image link and uses bracket caption when no CAPTION`() {
    val candidate = detectInlineImageCandidate("[[file:img/cat.png][A cat]]")
    assertEquals("file", candidate?.scheme)
    assertEquals("img/cat.png", candidate?.target)
    assertEquals("A cat", candidate?.caption)
}

@Test
fun `detectInlineImageCandidate prefers CAPTION over bracket caption`() {
    val text = """
        #+CAPTION: Prefer me
        [[file:img/cat.png][Ignore me]]
    """.trimIndent()
    val candidate = detectInlineImageCandidate(text)
    assertEquals("Prefer me", candidate?.caption)
}

@Test
fun `detectInlineImageCandidate does not treat described non-image link as inline image`() {
    val candidate = detectInlineImageCandidate("[[file:note.org][A note]]")
    assertNull(candidate)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageDetectorTests*'`

Expected: FAIL on described image link (currently rejected).

**Step 3: Write minimal implementation**

- Extend detector regex to recognize described bracket links, but only accept as inline image when the target is an image path.
- Populate `caption` from bracket description when `#+CAPTION:` is absent.
- Keep existing behavior for non-image described links.

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageDetectorTests*'`

Expected: PASS.

**Step 5: Commit**

`git add app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageDetector.kt tests/com/gladomat/linklet/ui/components/OrgInlineImageDetectorTests.kt`

Commit: `feat(ui): support described inline image captions`

## Task 2: Width hint parsing (dp + percent)

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageDetector.kt`
- Test: `tests/com/gladomat/linklet/ui/components/OrgInlineImageDetectorTests.kt`

**Step 1: Write the failing tests**

```kotlin
@Test
fun `detectInlineImageCandidate parses width attr in dp`() {
    val text = """
        #+ATTR_ORG: :width 200
        [[file:img/cat.png]]
    """.trimIndent()
    val candidate = detectInlineImageCandidate(text)
    assertEquals("200", candidate?.attrs?.get("width"))
}

@Test
fun `detectInlineImageCandidate parses width attr in percent`() {
    val text = """
        #+ATTR_ORG: :width 80%
        [[file:img/cat.png]]
    """.trimIndent()
    val candidate = detectInlineImageCandidate(text)
    assertEquals("80%", candidate?.attrs?.get("width"))
}
```

**Step 2: Run tests to verify failure (if needed)**

Run: `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageDetectorTests*'`

Expected: If current attr parser trims incorrectly, FAIL; otherwise PASS (then proceed to Task 3 where width affects UI).

**Step 3: Minimal implementation (only if failing)**

- Ensure `parseAttrPairs` captures `80%` intact and strips nothing important.

**Step 4: Re-run tests**

Expected: PASS.

**Step 5: Commit (only if code changed)**

Commit: `fix(ui): parse ATTR_ORG width hints`

## Task 3: Apply width hint to image + caption layout

**Files:**
- Modify: `app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageBlock.kt`

**Step 1: Write failing Compose Robolectric test**

Add a new test file:

- Create: `tests/com/gladomat/linklet/ui/components/OrgInlineImageBlockWidthTests.kt`

Example skeleton:

```kotlin
@RunWith(RobolectricTestRunner::class)
class OrgInlineImageBlockWidthTests {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `width hint percent constrains image width`() {
        // Arrange: fixed parent width container; render OrgInlineImageBlock with widthFraction
        // Assert: bounds width is close to expected (e.g., 80% of parent)
    }
}
```

**Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageBlockWidthTests*'`

Expected: FAIL (feature not implemented).

**Step 3: Minimal implementation**

- Introduce a tiny width-hint model in `OrgInlineImageBlock`:
  - Parse `width` string (`"200"`, `"200dp"`, `"80%"`) into either dp or fraction.
- Apply width modifier on the image (and caption) container:
  - When no width: current behavior (`fillMaxWidth()`).
  - When dp: set a `width(...)` clamped to max.
  - When percent: compute dp from `maxWidth` in `BoxWithConstraints`.

**Step 4: Re-run test**

Expected: PASS.

**Step 5: Commit**

Commit: `feat(ui): apply ATTR_ORG width hints to inline images`

## Task 4: End-to-end caption render in NoteViewScreen

**Files:**
- Modify: `tests/com/gladomat/linklet/ui/screens/note/NoteViewScreenImageTests.kt`

**Step 1: Add failing assertion**

Extend the existing test content to include both caption forms:

```org
#+CAPTION: Inline image caption
[[file:inline-image-test.png][Bracket caption]]
```

Assert the caption under the image is `Inline image caption` (CAPTION wins).

**Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*NoteViewScreenImageTests*'`

Expected: FAIL until described links are accepted and caption selected.

**Step 3: Minimal implementation**

- Ensure the described-link candidate flows through `OrgBlockRenderers` into `OrgInlineImageBlock`.

**Step 4: Re-run**

Expected: PASS.

**Step 5: Commit**

Commit: `test(ui): cover captions precedence in NoteViewScreen`

## Task 5: Wrap-up

**Step 1: Run targeted suite**

Run:
- `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageDetectorTests*'`
- `./gradlew :app:testDebugUnitTest --tests '*OrgInlineImageBlock*'`
- `./gradlew :app:testDebugUnitTest --tests '*NoteViewScreenImageTests*'`

Expected: PASS.

**Step 2: Update bean + commit bean file**

- Mark `org_roam_mobile-5ck6` completed: `beans update org_roam_mobile-5ck6 --status completed`
- Ensure bean markdown file is staged with code changes if committing.

