package com.gladomat.linklet.app.di

import android.content.Context
import androidx.room.Room
import com.gladomat.linklet.data.index.GraphPositionDao
import com.gladomat.linklet.data.index.IndexQueueDao
import com.gladomat.linklet.data.index.IndexingScheduler
import com.gladomat.linklet.data.index.IndexingStateDao
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.index.NoteDao
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.storage.DocumentTreeStorageImpl
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.data.sync.SyncScheduler
import com.gladomat.linklet.data.sync.db.OperationJournalDao
import com.gladomat.linklet.data.sync.metrics.InMemorySyncMetrics
import com.gladomat.linklet.data.sync.metrics.SyncMetrics
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.NoteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** CPU-bound work dispatcher (e.g. parsing), as opposed to the unqualified IO dispatcher above. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

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
        indexingScheduler: IndexingScheduler,
    ): INoteRepository = NoteRepositoryImpl(
        storage,
        parser,
        database.noteDao(),
        database.graphPositionDao(),
        indexQueueDao,
        syncScheduler,
        indexingScheduler,
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
    fun provideGraphPositionDao(
        database: NoteDatabase,
    ): GraphPositionDao = database.graphPositionDao()

    @Provides
    fun provideIndexQueueDao(
        database: NoteDatabase,
    ): IndexQueueDao = database.indexQueueDao()

    @Provides
    fun provideIndexingStateDao(
        database: NoteDatabase,
    ): IndexingStateDao = database.indexingStateDao()

    @Provides
    fun provideOperationJournalDao(
        database: NoteDatabase,
    ): OperationJournalDao = database.operationJournalDao()

    @Provides
    @Singleton
    fun provideCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideSyncMetrics(): SyncMetrics = InMemorySyncMetrics()

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
        .addMigrations(NoteDatabase.MIGRATION_5_6)
        .addMigrations(NoteDatabase.MIGRATION_6_7)
        .addMigrations(NoteDatabase.MIGRATION_7_8)
        .addMigrations(NoteDatabase.MIGRATION_8_9)
        .addMigrations(NoteDatabase.MIGRATION_9_10)
        .fallbackToDestructiveMigration()
        .build()
}
