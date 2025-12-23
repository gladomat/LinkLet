---
# org_roam_mobile-e2bf
title: Fix DocumentTreeStorageImplTests InaccessibleObjectException
status: completed
type: bug
priority: normal
created_at: 2025-12-23T17:22:21Z
updated_at: 2025-12-23T18:55:35Z
---

Unit test failure: DocumentTreeStorageImplTests.writeNote persists content and readNote retrieves it fails with java.lang.reflect.InaccessibleObjectException (Robolectric FileDescriptor access) under JDK 21. Repro: ./gradlew test (fails in :app:testDebugUnitTest). Investigate safer file scheme handling or test JVM args.