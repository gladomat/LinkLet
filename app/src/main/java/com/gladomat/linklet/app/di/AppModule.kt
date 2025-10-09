package com.gladomat.linklet.app.di

import android.content.Context
import androidx.room.Room
import com.gladomat.linklet.data.index.NoteDatabase
import com.gladomat.linklet.data.parser.IParser
import com.gladomat.linklet.data.parser.RegexParser
import com.gladomat.linklet.data.storage.FileStorageImpl
import com.gladomat.linklet.data.storage.IStorage
import com.gladomat.linklet.domain.repository.INoteRepository
import com.gladomat.linklet.domain.repository.NoteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

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
    ): IStorage {
        val baseDir = File(context.filesDir, "notes")
        return FileStorageImpl(baseDir = baseDir)
    }

    @Provides
    @Singleton
    fun provideRepository(
        storage: IStorage,
        parser: IParser,
        database: NoteDatabase,
    ): INoteRepository = NoteRepositoryImpl(storage, parser, database.noteDao())

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
