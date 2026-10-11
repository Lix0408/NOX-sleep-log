package com.nox.sleep.data

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
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 一条睡眠记录。记录归属「起床日」，即 endAt 所在那天。
 *
 * awakeSegments 存 "start,end;start,end" 形式的亮屏时间片，
 * 用于单夜详情页画清醒缺口，也用于回溯 T_end。
 */
@Entity(tableName = "sleep_session", indices = [Index("dayKey")])
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startAt: Long,
    val endAt: Long? = null,
    /**
     * 归属日 "yyyy-MM-dd" = endAt 所在本地日期（起床日）。
     * 未结束的记录为 null；结束时由 SleepRepository.finish 统一写入。
     * 按天汇总（同一天多段 → 当日累计）以它为分组键。
     */
    val dayKey: String? = null,
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

/**
 * 按天汇总的一行：某个归属日内所有分段睡眠的合计。
 *
 * 由 [SleepDao.observeDayAggregates] 以 GROUP BY dayKey 实时派生，不落库 ——
 * 单一事实源始终是 sleep_session 明细，汇总数字不可能与明细漂移。
 * 分钟换算口径与 domain/DayRollup.kt 的 sleptMinutesOf 逐字一致（先各自取整再相加），
 * 保证「当日合计 == 页面里各段之和」。
 */
data class DayAggregate(
    val dayKey: String,
    val segmentCount: Int,
    val inBedMinutes: Long,
    val sleptMinutes: Long,
    val firstStartAt: Long,
    val lastEndAt: Long
)

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

    /**
     * 按天汇总（最近 limit 个有记录的天，倒序）。
     *
     * 分钟口径：每段先各自 ⌊/60000⌋ 再相加（与 UI 段级换算完全一致）；
     * MAX(...,0) 兜底脏数据，COALESCE 兜底空集，保证结果恒为非负整数。
     */
    @Query(
        """
        SELECT dayKey AS dayKey,
               COUNT(*) AS segmentCount,
               COALESCE(SUM(MAX((endAt - startAt) / 60000, 0)), 0) AS inBedMinutes,
               COALESCE(SUM(MAX((endAt - startAt) / 60000 - awakeMillis / 60000, 0)), 0) AS sleptMinutes,
               COALESCE(MIN(startAt), 0) AS firstStartAt,
               COALESCE(MAX(endAt), 0) AS lastEndAt
        FROM sleep_session
        WHERE endAt IS NOT NULL AND dayKey IS NOT NULL
        GROUP BY dayKey
        ORDER BY dayKey DESC
        LIMIT :limit
        """
    )
    fun observeDayAggregates(limit: Int): Flow<List<DayAggregate>>

    @Query("DELETE FROM sleep_session")
    suspend fun clearAll()

    /** 仅调试包：删除示例数据（confirmedBy = sample），不碰真实记录 */
    @Query("DELETE FROM sleep_session WHERE confirmedBy = 'sample'")
    suspend fun deleteSamples()
}

@Database(entities = [SleepSession::class], version = 2, exportSchema = false)
abstract class NoxDatabase : RoomDatabase() {
    abstract fun dao(): SleepDao

    companion object {
        /**
         * 1 → 2：为分段记录增加归属日 dayKey（按天汇总的分组键）。
         *
         * 旧数据用 SQLite 的 localtime 回填 —— 与 dayKeyOf() 的
         * 「结束时刻的本地日期」口径一致；未结束的记录保持 NULL。
         * 索引名必须与 Room 由 @Entity(indices = [Index("dayKey")]) 生成的
         * 预期 schema 完全一致，否则打开数据库时校验会失败。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sleep_session ADD COLUMN dayKey TEXT")
                db.execSQL(
                    "UPDATE sleep_session " +
                        "SET dayKey = strftime('%Y-%m-%d', endAt / 1000, 'unixepoch', 'localtime') " +
                        "WHERE endAt IS NOT NULL"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sleep_session_dayKey " +
                        "ON sleep_session (dayKey)"
                )
            }
        }

        @Volatile
        private var INSTANCE: NoxDatabase? = null

        fun get(context: Context): NoxDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoxDatabase::class.java,
                    "nox.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
