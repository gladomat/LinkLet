package com.gladomat.linklet.data.index

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gladomat.linklet.data.sync.SyncStateEntity
import com.gladomat.linklet.data.sync.SyncStateTypeConverters

@Database(
    entities = [NoteEntity::class, LinkEntity::class, SyncStateEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(SyncStateTypeConverters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun syncStateDao(): SyncStateDao
}
