package com.jawlinefit.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "progress")
data class ProgressEntry(
    @PrimaryKey val id: String,
    val photoPath: String,
    val createdAtEpochMillis: Long,
    val symmetryPercent: Double,
    @ColumnInfo(name = "jawDefinitionPercent") val jawAngleDegrees: Double,
    val note: String
)

@Entity(tableName = "content_cache")
data class CachedContent(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "routine_completions")
data class RoutineCompletion(
    @PrimaryKey val id: String,
    val routineId: Long,
    val completedEpochDay: Long,
    val completedAtEpochMillis: Long
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
interface CompletionDao {
    @Query("SELECT * FROM routine_completions ORDER BY completedEpochDay DESC")
    fun observeAll(): Flow<List<RoutineCompletion>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: RoutineCompletion)

    @Query("DELETE FROM routine_completions")
    suspend fun deleteAll()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM content_cache WHERE cacheKey = :key") suspend fun get(key: String): CachedContent?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(content: CachedContent)
    @Query("DELETE FROM content_cache") suspend fun clear()
}

@Database(entities = [ProgressEntry::class, CachedContent::class, RoutineCompletion::class], version = 2, exportSchema = true)
abstract class JawlineDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun completionDao(): CompletionDao
    abstract fun cacheDao(): CacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `routine_completions` (`id` TEXT NOT NULL, `routineId` INTEGER NOT NULL, `completedEpochDay` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
