package com.nox.sleep.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 一条睡眠记录。记录归属「起床日」，即 endAt 所在那天。
 *
 * awakeSegments 存 "start,end;start,end" 形式的亮屏时间片，
 * 用于单夜详情页画清醒缺口，也用于回溯 T_end。
 */
@Entity(tableName = "sleep_session")
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startAt: Long,
    val endAt: Long? = null,
    val awakeMillis: Long = 0L,
    val wakeCount: Int = 0,
    /** manual = 用户手动结束；auto = 醒来检测结束 */
    val confirmedBy: String = "manual",
    val mood: String? = null,
    val quality: Int? = null,
    val note: String? = null,
    /** 1 = 完整；0 = 中途进程被杀，亮屏时长不完整 */
    val integrity: Int = 1,
    val awakeSegments: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isFinished: Boolean get() = endAt != null
    val isComplete: Boolean get() = integrity == 1

    fun segmentRanges(): List<LongRange> =
        awakeSegments.split(';')
            .mapNotNull { seg ->
                val p = seg.split(',')
                if (p.size != 2) null
                else p[0].toLongOrNull()?.let { a -> p[1].toLongOrNull()?.let { b -> a..b } }
            }

    companion object {
        fun encodeSegments(list: List<LongRange>): String =
            list.joinToString(";") { "${it.first},${it.last}" }
    }
}

@Dao
interface SleepDao {

    @Query("SELECT * FROM sleep_session WHERE endAt IS NOT NULL ORDER BY endAt DESC")
    fun observeAll(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_session WHERE endAt IS NULL LIMIT 1")
    fun observeActive(): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_session WHERE endAt IS NULL LIMIT 1")
    suspend fun active(): SleepSession?

    @Query("SELECT * FROM sleep_session WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SleepSession?

    @Query("SELECT * FROM sleep_session WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_session WHERE endAt IS NOT NULL ORDER BY endAt DESC LIMIT 1")
    suspend fun latest(): SleepSession?

    @Insert
    suspend fun insert(s: SleepSession): Long

    @Update
    suspend fun update(s: SleepSession)

    @Delete
    suspend fun delete(s: SleepSession)

    @Query("DELETE FROM sleep_session")
    suspend fun clearAll()
}

@Database(entities = [SleepSession::class], version = 1, exportSchema = false)
abstract class NoxDatabase : RoomDatabase() {
    abstract fun dao(): SleepDao

    companion object {
        @Volatile
        private var INSTANCE: NoxDatabase? = null

        fun get(context: Context): NoxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoxDatabase::class.java,
                    "nox.db"
                ).build().also { INSTANCE = it }
            }
    }
}
