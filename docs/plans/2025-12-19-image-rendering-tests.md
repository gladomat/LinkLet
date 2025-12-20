# Image Rendering Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Robolectric-friendly Compose tests for inline image rendering and fullscreen viewer open/close, using a tiny PNG test resource and a stable viewer test tag.

**Architecture:** Tests run as unit tests (`:app:testDebugUnitTest`) from the `tests/` source set. A tiny PNG is placed in `tests/resources/` and added to the test resources classpath. Compose UI tests use `createComposeRule()` and assert UI state transitions; the fullscreen viewer gains a `testTag` for stable assertions.

**Tech Stack:** Kotlin, JUnit4, Robolectric, Jetpack Compose UI test (`androidx.compose.ui:ui-test-junit4`).

### Task 1: Test resources + Gradle test setup

**Files:**
- Create: `tests/resources/inline-image-test.png`
- Modify: `app/build.gradle.kts`

**Step 1: Add the PNG test resource**

Create a 1x1 PNG in `tests/resources/inline-image-test.png`:

```bash
mkdir -p tests/resources
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/ax3l2cAAAAASUVORK5CYII=' | base64 -d > tests/resources/inline-image-test.png
```

**Step 2: Write the failing test import scaffold (will fail to compile)**

Add a minimal Compose test class to confirm the test dependency is required.

```kotlin
import androidx.compose.ui.test.junit4.createComposeRule
```

**Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.components.OrgInlineImageBlockTests`

Expected: FAIL due to missing Compose UI test dependency or resource classpath.

**Step 4: Add test dependencies + test resources source dir**

In `app/build.gradle.kts`, update `dependencies` and test `sourceSets`:

```kotlin
sourceSets {
    getByName("test") {
        java.srcDirs("src/test/java", "../tests")
        resources.srcDirs("src/test/resources", "../tests/resources")
    }
}
```

```kotlin
testImplementation("androidx.compose.ui:ui-test-junit4")
```

**Step 5: Run test to verify it now compiles**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.components.OrgInlineImageBlockTests`

Expected: FAIL (test body not implemented yet).

**Step 6: Commit**

```bash
git add tests/resources/inline-image-test.png app/build.gradle.kts tests/com/gladomat/linklet/ui/components/OrgInlineImageBlockTests.kt
git commit -m "test(ui): add compose test setup and resources"
```

### Task 2: OrgInlineImageBlock renders image

**Files:**
- Create: `tests/com/gladomat/linklet/ui/components/OrgInlineImageBlockTests.kt`

**Step 1: Write the failing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrgInlineImageBlockTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inline image block loads and hides loading text`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.parse(resource.toURI().toString())

        composeRule.setContent {
            OrgInlineImageBlock(
                uri = uri,
                caption = null,
                align = null,
                onOpen = {},
            )
        }

        composeRule.onNodeWithText("Loading image…").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("Failed to decode image").assertCountEquals(0)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.components.OrgInlineImageBlockTests`

Expected: FAIL until dependencies and resource setup are correct.

**Step 3: Write minimal implementation**

No production changes expected if the loader works. If failures indicate missing dependencies or resource path, fix `app/build.gradle.kts` test setup.

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.components.OrgInlineImageBlockTests`

Expected: PASS

**Step 5: Commit**

```bash
git add tests/com/gladomat/linklet/ui/components/OrgInlineImageBlockTests.kt
if git diff --name-only | rg -q 'app/build.gradle.kts|tests/resources/inline-image-test.png'; then
  git add app/build.gradle.kts tests/resources/inline-image-test.png
fi
git commit -m "test(ui): cover inline image block rendering"
```

### Task 3: NoteViewScreen viewer open/close + viewer test tag

**Files:**
- Create: `tests/com/gladomat/linklet/ui/screens/note/NoteViewScreenImageTests.kt`
- Modify: `app/src/main/java/com/gladomat/linklet/ui/components/FullscreenImageViewer.kt`

**Step 1: Write the failing test (expects test tag)**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteViewScreenImageTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tap inline image opens and closes fullscreen viewer`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val uri = Uri.parse(resource.toURI().toString())

        val note = Note(
            id = NoteId("notes/inline-image.org"),
            title = "Inline Image",
            content = "[[file:inline-image-test.png]]",
            links = emptyList(),
        )

        val state = NoteViewUiState.Success(
            note = note,
            backlinks = emptyList(),
            lastModified = null,
            isFavorite = false,
        )

        composeRule.setContent {
            NoteViewScreen(
                state = state,
                searchState = NoteViewViewModel.NoteSearchState(),
                onOpenLink = {},
                onOpenExternalLink = {},
                resolveStorageUri = { Result.success(uri) },
                onEditNote = {},
                onNavigateHome = {},
                onBack = {},
                onShare = {},
                onFavorite = {},
                onCreateNote = {},
                onRetry = {},
                onDelete = {},
                onDuplicate = {},
                onRename = {},
                onOpenSearch = {},
                onCloseSearch = {},
                onSearchQueryChange = {},
                onClearSearch = {},
                onSearchOptionsChange = {},
                onPrevMatch = {},
                onNextMatch = {},
                onCopyToClipboard = {},
                onCopyIdLink = {},
                onCopyFileLink = {},
                onUpdateProperties = {},
                onUpdateTags = {},
                allTags = emptyList(),
            )
        }

        composeRule.onNodeWithText("Loading image…").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty()
        }

        // Click the inline image area to open viewer.
        composeRule.onNodeWithText("Inline Image").performClick()
        composeRule.onNodeWithTag("fullscreen-image-viewer").assertExists()

        // Close viewer by clicking anywhere in the viewer.
        composeRule.onNodeWithTag("fullscreen-image-viewer").performClick()
        composeRule.onAllNodesWithTag("fullscreen-image-viewer").assertCountEquals(0)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.screens.note.NoteViewScreenImageTests`

Expected: FAIL because `fullscreen-image-viewer` tag is missing.

**Step 3: Add minimal production change (test tag)**

In `FullscreenImageViewer`, add a test tag to the root `Box`:

```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .testTag("fullscreen-image-viewer")
        .background(Color.Black)
        // ...
)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.ui.screens.note.NoteViewScreenImageTests`

Expected: PASS

**Step 5: Commit**

```bash
git add tests/com/gladomat/linklet/ui/screens/note/NoteViewScreenImageTests.kt \
  app/src/main/java/com/gladomat/linklet/ui/components/FullscreenImageViewer.kt
git commit -m "test(ui): cover inline image viewer open close"
```
