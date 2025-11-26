# Sync / Storage TODO

- [x] Define sync data model: `SyncStateEntity`, DAO, and hashing utility for local notes.
- [x] Add `SyncEngine` core that compares local hashes vs remote fingerprints and updates the Room cache.
- [x] Implement WebDAV/Nextcloud provider (PROPFIND/GET/PUT/DELETE with ETag conflict checks) plus credential storage in settings.
- [ ] Implement Dropbox provider (list/download/upload/delete via v2 API) with token storage and request signing.
- [x] Extend Settings UI for provider configuration, status display, and manual sync trigger.
- [ ] Add WorkManager job/tests for scheduled sync and a test matrix covering conflict handling and error retries.
