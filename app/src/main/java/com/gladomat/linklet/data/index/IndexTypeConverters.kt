package com.gladomat.linklet.data.index

import androidx.room.TypeConverter

class IndexTypeConverters {

    @TypeConverter
    fun toIndexQueueStatus(value: String?): IndexQueueStatus =
        value?.let { runCatching { IndexQueueStatus.valueOf(it) }.getOrDefault(IndexQueueStatus.PENDING) }
            ?: IndexQueueStatus.PENDING

    @TypeConverter
    fun fromIndexQueueStatus(status: IndexQueueStatus?): String? = status?.name

    @TypeConverter
    fun toFileTags(value: String?): List<String> =
        value
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    @TypeConverter
    fun fromFileTags(tags: List<String>?): String =
        tags
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("|")
            ?: ""
}
