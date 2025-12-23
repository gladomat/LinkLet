---
# org_roam_mobile-8eed
title: Implement App Versioning and About Dialog
status: todo
type: feature
priority: normal
created_at: 2025-12-23T19:45:16Z
updated_at: 2025-12-23T19:45:16Z
---

Implement automated versioning and an 'About' dialog to display app information.

## Goals
- Establish a semantic versioning scheme (e.g., in `build.gradle.kts`).
- Inject version information (version name, version code, build date) into the app at build time.
- Create an 'About' dialog/screen accessible from the Settings menu.
- Display relevant app details: App Name, Version, Build Date, Author/Copyright, and License info.

## Checklist
- [ ] Define versioning strategy in `build.gradle.kts` (or `version.properties`).
- [ ] Configure build config fields (or resource values) to expose version and build timestamp.
- [ ] Design and implement `AboutDialog` (or `AboutScreen`) using Jetpack Compose.
- [ ] Add navigation entry point in `SettingsScreen`.
- [ ] Populate dialog with:
    - [ ] App Icon & Name
    - [ ] Version Name & Code
    - [ ] Build Date (formatted)
    - [ ] Author/Credit (e.g., 'Developed by [Author Name]')
    - [ ] Link to source code / repository (if applicable).
- [ ] Verify information is correct in debug and release builds.
