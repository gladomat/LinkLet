package com.gladomat.linklet.app.di

import android.content.Context
import androidx.room.Room
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.index.SyncStateDao
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.settings.FolderSettingsRepository
import com.gladomat.linklet.data.storage.DocumentTreeStorageImpl
import com.gladomat.linklet.data.storage.IStorage
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
    ): INoteRepository = NoteRepositoryImpl(storage, parser, database.noteDao())

    @Provides
    fun provideSyncStateDao(
        database: NoteDatabase,
    ): SyncStateDao = database.syncStateDao()

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
    ).fallbackToDestructiveMigration().build()
}
