---
# org_roam_mobile-ypt4
title: Fix DocumentTreeStorageImplTests reflection failure
status: todo
type: bug
priority: normal
created_at: 2025-12-23T17:47:59Z
updated_at: 2025-12-23T17:48:06Z
---

Running ./gradlew test fails with java.lang.reflect.InaccessibleObjectException in DocumentTreeStorageImplTests (e.g. writeNote persists content and readNote retrieves it at DocumentTreeStorageImplTests.kt:61). Likely JDK17 module access; may require avoiding reflection or adding JVM args (--add-opens ...) for unit tests.