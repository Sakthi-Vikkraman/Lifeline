package com.lifeline.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class Tag { PRODUCTIVITY, ENTERTAINMENT }

/**
 * One logged piece of work. [date] is ISO yyyy-MM-dd in local time; [hour] is 0–23.
 * [keptPlan] is null when that hour had no plan.
 */
@Entity(tableName = "entries", indices = [Index("date")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val hour: Int,
    val createdAt: Long,
    val text: String,
    val tag: Tag,
    val photoPath: String?,
    val keptPlan: Boolean? = null,
)

/** The morning plan: what [hour] on [date] is supposed to be spent on. */
@Entity(tableName = "plans", primaryKeys = ["date", "hour"])
data class Plan(val date: String, val hour: Int, val goal: String)

/** The night review for [date]. */
@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey val date: String,
    val win: String,
    val fail: String,
    val fix: String,
    val createdAt: Long,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt")
    fun observeDay(date: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to ORDER BY createdAt")
    fun observeBetween(from: String, to: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to ORDER BY createdAt")
    suspend fun between(from: String, to: String): List<Entry>

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt")
    suspend fun entriesOn(date: String): List<Entry>

    @Query("SELECT COUNT(*) FROM entries WHERE date = :date AND hour = :hour")
    suspend fun countForHour(date: String, hour: Int): Int

    @Insert
    suspend fun insert(entry: Entry): Long

    @Delete
    suspend fun delete(entry: Entry)

    // ----- plans -----

    @Query("SELECT * FROM plans WHERE date = :date")
    fun observePlans(date: String): Flow<List<Plan>>

    @Query("SELECT * FROM plans WHERE date = :date")
    suspend fun plansOn(date: String): List<Plan>

    @Query("SELECT * FROM plans WHERE date BETWEEN :from AND :to")
    suspend fun plansBetween(from: String, to: String): List<Plan>

    @Query("SELECT * FROM plans WHERE date BETWEEN :from AND :to")
    fun observePlansBetween(from: String, to: String): Flow<List<Plan>>

    @Query("DELETE FROM plans WHERE date = :date")
    suspend fun clearPlans(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<Plan>)

    @Transaction
    suspend fun replacePlans(date: String, plans: List<Plan>) {
        clearPlans(date)
        insertPlans(plans)
    }

    // ----- reviews -----

    @Query("SELECT * FROM reviews WHERE date = :date")
    fun observeReview(date: String): Flow<Review?>

    @Query("SELECT * FROM reviews WHERE date = :date")
    suspend fun reviewOn(date: String): Review?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReview(review: Review)
}

/** v1 → v2: plan-vs-reality, morning plans and night reviews. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN keptPlan INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS plans (date TEXT NOT NULL, hour INTEGER NOT NULL, goal TEXT NOT NULL, PRIMARY KEY(date, hour))")
        db.execSQL("CREATE TABLE IF NOT EXISTS reviews (date TEXT NOT NULL, win TEXT NOT NULL, fail TEXT NOT NULL, fix TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(date))")
    }
}

@Database(entities = [Entry::class, Plan::class, Review::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lifeline.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
