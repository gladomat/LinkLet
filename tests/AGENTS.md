# Tests

## Purpose

All unit and Robolectric tests. This top-level `tests/` directory is wired into `:app`'s test source set in `app/build.gradle.kts` (alongside `tests/resources` for fixtures) — it is not a separate Gradle module.

## Local Contracts

- Mirror the production package under `tests/com/gladomat/linklet/`, file names end in `*Tests.kt`.
- Robolectric tests use `testing/Aarch64RobolectricTestRunner` (Apple Silicon SQLite/conscrypt workaround). Many are skipped on arm64 hosts — a green local run is not full coverage; CI (x86 Ubuntu, `.github/workflows/android.yml`) runs everything.
- Coroutines: `MainDispatcherRule` + `runTest`; Flows via Turbine; mocking via MockK. Room tests use `Room.inMemoryDatabaseBuilder`.
- No emulator or device dependence — everything must pass headless on CI.
- Timing-sensitive tests (worker budgets, sweeps) must be host-speed-robust: sleeps ≥2× the budget under test, no exact-count assertions on budget-truncated work.
- Shared fakes live next to their tests (e.g. `FakeStorage` in `IndexPass1ProcessorTests`); prefer extending an existing fake over writing a parallel one.

## Verification

- Local: `./gradlew :app:testDebugUnitTest` (build with Android Studio JBR: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`).
- CI-equivalent: `scripts/ci_equiv_test.sh`.
- Canonical feature/coverage record: `docs/QA_FEATURE_MATRIX.md` — update it when adding feature-level coverage.
