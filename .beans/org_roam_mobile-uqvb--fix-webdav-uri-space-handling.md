---
# org_roam_mobile-uqvb
title: Fix WebDAV URI space handling
status: in-progress
type: bug
created_at: 2025-12-23T16:26:34Z
updated_at: 2025-12-23T16:26:34Z
---

Sync bug: URISyntaxException in WebDavRemoteSyncProvider.relativePathFromUrl when path contains space (.stfolder (1)). Happens during listAllRemoteResources. Log timestamp 2025-12-23 17:25:18.707. File: app/src/main/java/com/gladomat/linklet/data/sync/WebDavRemoteSyncProvider.kt around lines 303 and 345. Investigate URL encoding/decoding for WebDAV paths.