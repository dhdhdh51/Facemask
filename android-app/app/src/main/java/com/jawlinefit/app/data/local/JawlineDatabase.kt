package com.jawlinefit.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "progress")
data class ProgressEntry(
    @PrimaryKey val id: String,
    val photoPath: String,
    val createdAtEpochMillis: Long,
    val symmetryPercent: Double,
    val jawDefinitionPercent: Double,
    val note: String
)

@Entity(tableName = "content_cache")
data class CachedContent(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAtEpochMillis: Long
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<ProgressEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ProgressEntry)

    @Delete
    suspend fun delete(entry: ProgressEntry)

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM content_cache WHERE cacheKey = :key") suspend fun get(key: String): CachedContent?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(content: CachedContent)
    @Query("DELETE FROM content_cache") suspend fun clear()
}

@Database(entities = [ProgressEntry::class, CachedContent::class], version = 1, exportSchema = true)
abstract class JawlineDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun cacheDao(): CacheDao
}
