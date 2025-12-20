# Inline Image Detection Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add/extend unit tests for Org inline image detection to cover scheme/extension handling and keyword metadata.

**Architecture:** Extend the existing `OrgInlineImageDetectorTests` to exercise `detectInlineImageCandidate` directly with plain string inputs. Keep tests pure, no IO or Compose, focused on parsing/detection behavior.

**Tech Stack:** Kotlin, JUnit4 (`org.junit.Test`, `org.junit.Assert`) in `tests/`.

### Task 1: Expand inline image detection tests

**Files:**
- Modify: `tests/com/gladomat/linklet/ui/components/OrgInlineImageDetectorTests.kt`

**Step 1: Write the failing tests**

Add tests for:
- Mixed-case extension accepted (e.g., `[[file:img/CAT.PNG]]`)
- Query fragments rejected (e.g., `[[file:img/cat.jpg?raw=1]]`)
- Metadata case with CAPTION + NAME + ATTR_HTML align

```kotlin
@Test
fun `detectInlineImageCandidate accepts mixed-case extensions`() {
    val candidate = detectInlineImageCandidate("[[file:img/CAT.PNG]]")
    assertEquals("file", candidate?.scheme)
    assertEquals("img/CAT.PNG", candidate?.target)
}

@Test
fun `detectInlineImageCandidate rejects image links with query fragments`() {
    val candidate = detectInlineImageCandidate("[[file:img/cat.jpg?raw=1]]")
    assertNull(candidate)
}

@Test
fun `detectInlineImageCandidate supports CAPTION NAME and ATTR_HTML lines`() {
    val text = """
        #+CAPTION: A cat
        #+NAME: CatImage
        #+ATTR_HTML: :align center
        [[file:img/cat.jpg]]
    """.trimIndent()
    val candidate = detectInlineImageCandidate(text)
    assertEquals("A cat", candidate?.caption)
    assertEquals("CatImage", candidate?.name)
    assertEquals("center", candidate?.attrs?.get("align"))
    assertEquals("img/cat.jpg", candidate?.target)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.gladomat.linklet.ui.components.OrgInlineImageDetectorTests`

Expected: FAIL if any of the new behaviors are not currently supported. If all tests PASS immediately, note that behavior already exists and proceed (test-only change).

**Step 3: Write minimal implementation (if needed)**

If failures occur due to missing behavior:
- Modify: `app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageDetector.kt`

Example (only if needed):
```kotlin
private fun isImagePath(path: String): Boolean {
    val normalized = path.trim().substringBefore('?').lowercase()
    return normalized.endsWith(".png") ||
        normalized.endsWith(".jpg") ||
        normalized.endsWith(".jpeg") ||
        normalized.endsWith(".gif") ||
        normalized.endsWith(".webp") ||
        normalized.endsWith(".bmp") ||
        normalized.endsWith(".heic") ||
        normalized.endsWith(".heif") ||
        normalized.endsWith(".avif") ||
        normalized.endsWith(".svg")
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.gladomat.linklet.ui.components.OrgInlineImageDetectorTests`

Expected: PASS

**Step 5: Commit**

```bash
git add tests/com/gladomat/linklet/ui/components/OrgInlineImageDetectorTests.kt \
  app/src/main/java/com/gladomat/linklet/ui/components/OrgInlineImageDetector.kt
git commit -m "test(ui): extend inline image detection coverage"
```

