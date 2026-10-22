package com.saper.game.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Одна законченная партия. */
@Entity(tableName = "results")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val difficulty: String,
    val won: Boolean,
    /** Финальное время: чистое + 30 сек за каждую подсказку. */
    val timeSeconds: Int,
    val hintsUsed: Int,
    val finishedAt: Long
)

/** Настройка — пара ключ/значение; каждое поле используется своими ключами. */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val intValue: Int = 0,
    val boolValue: Boolean = false,
    val longValue: Long = 0
)

/** Сводка по одной сложности для экранов меню и рекордов. */
data class DifficultySummary(
    val difficulty: String,
    val best: Int?,
    val played: Int,
    val wins: Int?
)

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(result: GameResultEntity)

    @Query(
        "SELECT difficulty, MIN(CASE WHEN won = 1 THEN timeSeconds END) AS best, " +
            "COUNT(*) AS played, SUM(CASE WHEN won = 1 THEN 1 ELSE 0 END) AS wins " +
            "FROM results GROUP BY difficulty"
    )
    suspend fun summary(): List<DifficultySummary>

    @Query("SELECT * FROM results ORDER BY finishedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<GameResultEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings WHERE `key` LIKE :prefix || '%'")
    suspend fun getAllWithPrefix(prefix: String): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}

@Database(entities = [GameResultEntity::class, SettingEntity::class], version = 2, exportSchema = false)
abstract class SaperDatabase : RoomDatabase() {
    abstract fun results(): GameResultDao
    abstract fun settings(): SettingsDao
}

/** v1 → v2: время получения званий хранится отдельным полем longValue. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN longValue INTEGER NOT NULL DEFAULT 0")
    }
}

/** Единственная точка доступа к базе. init() вызывается один раз из MainActivity. */
object SaperStore {
    private lateinit var db: SaperDatabase

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(context.applicationContext, SaperDatabase::class.java, "saper.db")
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }

    suspend fun saveResult(difficulty: String, won: Boolean, timeSeconds: Int, hintsUsed: Int) {
        db.results().insert(
            GameResultEntity(
                difficulty = difficulty,
                won = won,
                timeSeconds = timeSeconds,
                hintsUsed = hintsUsed,
                finishedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun summary(): List<DifficultySummary> = db.results().summary()

    suspend fun recent(limit: Int): List<GameResultEntity> = db.results().recent(limit)

    suspend fun intSetting(key: String, default: Int): Int = db.settings().get(key)?.intValue ?: default

    suspend fun boolSetting(key: String, default: Boolean): Boolean = db.settings().get(key)?.boolValue ?: default

    suspend fun putIntSetting(key: String, value: Int) {
        db.settings().put(SettingEntity(key, intValue = value))
    }

    suspend fun putBoolSetting(key: String, value: Boolean) {
        db.settings().put(SettingEntity(key, boolValue = value))
    }

    suspend fun putLongSetting(key: String, value: Long) {
        db.settings().put(SettingEntity(key, longValue = value))
    }

    suspend fun allWithPrefix(prefix: String): List<SettingEntity> =
        db.settings().getAllWithPrefix(prefix)
}
