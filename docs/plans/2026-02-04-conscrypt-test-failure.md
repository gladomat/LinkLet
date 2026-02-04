# Conscrypt UnsatisfiedLinkError Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stop Robolectric unit tests from failing on Linux aarch64 due to Conscrypt native library loading.

**Architecture:** Detect the host architecture during unit test configuration and disable Robolectric Conscrypt integration on aarch64 so the provider is never loaded. Keep the change scoped to unit tests only.

**Tech Stack:** Gradle Kotlin DSL, Robolectric, JUnit

### Task 1: Reproduce the failing test (RED)

**Files:**
- Test: `tests/com/gladomat/linklet/domain/repository/NoteRepositoryImplTests.kt`

**Step 1: Run the failing test**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.domain.repository.NoteRepositoryImplTests`
Expected: FAIL with `java.lang.UnsatisfiedLinkError` from `org.conscrypt` in the report.

**Step 2: (Optional) Add a failing assertion for Conscrypt mode**

If needed, add a minimal test to assert the Conscrypt mode property is set (this should fail before the fix):

```kotlin
@Test
fun `conscrypt mode is off for aarch64`() {
    assertEquals("OFF", System.getProperty("robolectric.conscryptMode"))
}
```

### Task 2: Implement the minimal fix (GREEN)

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Set Robolectric Conscrypt mode to OFF on aarch64**

Add to `testOptions.unitTests.all { ... }`:

```kotlin
val osArch = System.getProperty("os.arch")?.lowercase()
if (osArch == "aarch64") {
    it.systemProperty("robolectric.conscryptMode", "OFF")
}
```

**Step 2: Re-run the failing test**

Run: `./gradlew :app:testDebugUnitTest --tests com.gladomat.linklet.domain.repository.NoteRepositoryImplTests`
Expected: PASS

**Step 3: Commit**

Run:
```bash
git add app/build.gradle.kts .beans/LinkLet-lkae--fix-conscrypt-unsatisfiedlinkerror-in-tests.md docs/plans/2026-02-04-conscrypt-test-failure.md

git commit -m "fix(test): disable robolectric conscrypt on aarch64"
```
