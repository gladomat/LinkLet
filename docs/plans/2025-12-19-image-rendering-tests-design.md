# Image Rendering Tests Design

**Goal:** Add Robolectric-friendly Compose tests that validate inline image rendering and the full-screen viewer open/close flow without emulator dependencies.

**Architecture:** Tests live under `tests/` and run via `:app:testDebugUnitTest`. We will keep production changes minimal by adding a stable `testTag` to the fullscreen viewer container. Test data uses a tiny PNG stored in `tests/resources/inline-image-test.png`, loaded via classpath and converted to a `file://` URI. This avoids hardcoded filesystem paths and keeps tests portable.

**Components:**
- `OrgInlineImageBlock` test: focuses on image load/render. It uses the real `loadImageBitmap` path with a `file://` URI to the test PNG. Assertions check that the loading text disappears and no error text appears.
- `NoteViewScreen` test: verifies integration. A minimal note/document state is created containing a paragraph with `[[file:inline-image-test.png]]`. The render context resolves storage URIs to the test PNG. The test taps the inline image and asserts the viewer container appears (by test tag), then closes it (tap or back press) and asserts the viewer is gone.
- `FullscreenImageViewer`: add a `Modifier.testTag("fullscreen-image-viewer")` to the root `Box` for stable UI assertions.

**Data Flow:**
- Test code loads `inline-image-test.png` via `classLoader.getResource`, converts to `Uri`, and injects it into the UI via the render context or screen state.
- Tap events drive the `viewerUri` state in `NoteViewScreen`, which conditionally renders `FullscreenImageViewer`.

**Error Handling:**
- Tests assert no error text is shown for valid input. If decoding fails, the tests should surface the error text and fail.

**Testing Strategy:**
- Use Robolectric Compose test rule with deterministic UI assertions via test tags.
- Keep scope small: one direct inline-image test plus one viewer open/close test.
- Avoid emulator/instrumentation; run under unit tests only.
