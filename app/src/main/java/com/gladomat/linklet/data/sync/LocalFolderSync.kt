package com.gladomat.linklet.data.sync

import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.domain.repository.INoteRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class LocalFolderSync @Inject constructor(
    private val folderSettingsRepository: FolderSettingsRepository,
    private val noteRepository: INoteRepository,
) : SyncProvider {

    override suspend fun pull(): Result<Unit> {
        val uri = folderSettingsRepository.currentFolderUri()
        return if (uri != null) {
            noteRepository.reindex()
        } else {
            Result.failure(IllegalStateException("Folder not selected"))
        }
    }

    override suspend fun push(): Result<Unit> = Result.success(Unit)

    override fun isOnline(): Boolean = runBlocking {
        folderSettingsRepository.currentFolderUri() != null
    }
}
