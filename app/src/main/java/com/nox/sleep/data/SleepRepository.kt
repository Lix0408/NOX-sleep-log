package com.nox.sleep.data

import kotlinx.coroutines.flow.Flow

class SleepRepository(
    private val dao: SleepDao,
    private val settings: SettingsStore
) {
    val all: Flow<List<SleepSession>> = dao.observeAll()
    val active: Flow<SleepSession?> = dao.observeActive()

    suspend fun activeNow(): SleepSession? = dao.active()
    suspend fun latest(): SleepSession? = dao.latest()
    suspend fun byId(id: Long): SleepSession? = dao.byId(id)
    fun observeById(id: Long): Flow<SleepSession?> = dao.observeById(id)

    suspend fun start(startAt: Long): Long =
        dao.insert(SleepSession(startAt = startAt, endAt = null))

    suspend fun finish(
        id: Long,
        endAt: Long,
        awakeMillis: Long,
        wakeCount: Int,
        confirmedBy: String,
        segments: List<LongRange>,
        integrity: Int = 1
    ) {
        val cur = dao.byId(id) ?: return
        dao.update(
            cur.copy(
                endAt = endAt,
                awakeMillis = awakeMillis,
                wakeCount = wakeCount,
                confirmedBy = confirmedBy,
                integrity = integrity,
                awakeSegments = SleepSession.encodeSegments(segments)
            )
        )
    }

    /** 服务运行期间定期落盘，进程被杀也不会丢太多 */
    suspend fun checkpoint(id: Long, awakeMillis: Long, wakeCount: Int, segments: List<LongRange>) {
        val cur = dao.byId(id) ?: return
        if (cur.endAt != null) return
        dao.update(
            cur.copy(
                awakeMillis = awakeMillis,
                wakeCount = wakeCount,
                awakeSegments = SleepSession.encodeSegments(segments)
            )
        )
    }

    suspend fun markIncomplete(id: Long) {
        val cur = dao.byId(id) ?: return
        dao.update(cur.copy(integrity = 0))
    }

    suspend fun saveMeta(id: Long, mood: String?, quality: Int?, note: String?) {
        val cur = dao.byId(id) ?: return
        dao.update(cur.copy(mood = mood, quality = quality, note = note))
    }

    /** 只改备注，不动心情与质量 —— 详情页单独编辑备注时用 */
    suspend fun updateNote(id: Long, note: String?) {
        val cur = dao.byId(id) ?: return
        dao.update(cur.copy(note = note?.trim()?.takeIf { it.isNotEmpty() }))
    }

    /** 只改心情与质量 —— 详情页补填时用 */
    suspend fun updateFeel(id: Long, mood: String?, quality: Int?) {
        val cur = dao.byId(id) ?: return
        dao.update(cur.copy(mood = mood, quality = quality))
    }

    suspend fun update(s: SleepSession) = dao.update(s)
    suspend fun delete(s: SleepSession) = dao.delete(s)
    suspend fun clearAll() = dao.clearAll()

    suspend fun settingsNow(): NoxSettings = settings.current()
    suspend fun updateSettings(transform: (NoxSettings) -> NoxSettings) =
        settings.update(transform)

    val settingsFlow: Flow<NoxSettings> = settings.flow
}
