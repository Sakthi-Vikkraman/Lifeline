package com.lifeline.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class Tag { PRODUCTIVITY, ENTERTAINMENT }

/** One logged piece of work. [date] is ISO yyyy-MM-dd in local time; [hour] is 0–23. */
@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val hour: Int,
    val createdAt: Long,
    val text: String,
    val tag: Tag,
    val photoPath: String?,
)

/** Per-day totals used by the calendar and the win streak. */
data class DayCount(val date: String, val productive: Int, val entertainment: Int) {
    val total get() = productive + entertainment
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt")
    fun observeDay(date: String): Flow<List<Entry>>

    @Query(
        """SELECT date,
             SUM(CASE WHEN tag = 'PRODUCTIVITY' THEN 1 ELSE 0 END) AS productive,
             SUM(CASE WHEN tag = 'ENTERTAINMENT' THEN 1 ELSE 0 END) AS entertainment
           FROM entries WHERE date BETWEEN :from AND :to GROUP BY date"""
    )
    fun observeDayCounts(from: String, to: String): Flow<List<DayCount>>

    @Query("SELECT COUNT(*) FROM entries WHERE date = :date AND hour = :hour")
    suspend fun countForHour(date: String, hour: Int): Int

    @Insert
    suspend fun insert(entry: Entry): Long

    @Delete
    suspend fun delete(entry: Entry)
}

@Database(entities = [Entry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lifeline.db")
                .build().also { instance = it }
        }
    }
}
