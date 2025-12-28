package com.gladomat.linklet.app.di

import android.content.Context
import androidx.room.Room
import com.gladomat.linklet.data.index.IndexQueueDao
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.storage.DocumentTreeStorageImpl
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.NoteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideParser(): IParser = RegexParser()

    @Provides
    @Singleton
    fun provideStorage(
        @ApplicationContext context: Context,
        folderSettingsRepository: FolderSettingsRepository,
    ): IStorage = DocumentTreeStorageImpl(context, folderSettingsRepository)

    @Provides
    @Singleton
    fun provideRepository(
        storage: IStorage,
        parser: IParser,
        database: NoteDatabase,
        indexQueueDao: IndexQueueDao,
        syncScheduler: SyncScheduler,
    ): INoteRepository = NoteRepositoryImpl(
        storage,
        parser,
        database.noteDao(),
        indexQueueDao,
        syncScheduler,
    )
    @Provides
    fun provideSyncStateDao(
        database: NoteDatabase,
    ): SyncStateDao = database.syncStateDao()

    @Provides
    fun provideNoteDao(
        database: NoteDatabase,
    ): NoteDao = database.noteDao()

    @Provides
    fun provideIndexQueueDao(
        database: NoteDatabase,
    ): IndexQueueDao = database.indexQueueDao()

    @Provides
    @Singleton
    fun provideCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NoteDatabase = Room.databaseBuilder(
        context,
        NoteDatabase::class.java,
        "linklet-notes.db",
    )
        .addMigrations(NoteDatabase.MIGRATION_2_3)
        .addMigrations(NoteDatabase.MIGRATION_3_4)
        .addMigrations(NoteDatabase.MIGRATION_4_5)
        .fallbackToDestructiveMigration()
        .build()
}
