package com.nox.sleep.core

/**
 * WHO 推荐的成人睡眠时长基准 —— 阶段四「健康基准」接入的唯一数据源。
 *
 * 性质：一条可对照、但不考核用户的线。区间内不做再分级（7 小时和 9 小时同等健康）。
 *
 * 数值出处：中文媒体广泛称「WHO 建议成人 7–9 小时」，其循证基础为
 * NSF 2015 睡眠时长推荐（成人 26–64 岁 7–9h、65 岁以上 7–8h）
 * 与 AASM/SRS 2015 成人 ≥7 小时共识。来源页如实列出。
 */
object SleepGuideline {

    /** 成人每晚推荐区间（小时） */
    const val ADULT_MIN_HOURS = 7f
    const val ADULT_MAX_HOURS = 9f

    /** 65 岁以上（小时）—— 暂未接入，仅预留 */
    const val OLDER_MIN_HOURS = 7f
    const val OLDER_MAX_HOURS = 8f

    /** 来源标注（界面与来源页共用） */
    const val SOURCE_LABEL = "WHO 推荐 · NSF 2015 · AASM/SRS 2015"

    /** 成人区间的最小/最大（分钟） */
    const val ADULT_MIN_MINUTES = 7 * 60
    const val ADULT_MAX_MINUTES = 9 * 60

    /** 一段时长（分钟）是否落在成人推荐区间内 */
    fun inAdultRange(minutes: Long): Boolean =
        minutes >= ADULT_MIN_MINUTES && minutes <= ADULT_MAX_MINUTES

    /** 对照条横轴范围：5h..11h（小时），用于把"你的时长"映射到横向位置 */
    const val AXIS_MIN_HOURS = 5f
    const val AXIS_MAX_HOURS = 11f

    /** 把时长（分钟）映射到对照条上的相对位置 0..1 */
    fun positionFraction(minutes: Long): Float {
        val hours = minutes / 60f
        return ((hours - AXIS_MIN_HOURS) / (AXIS_MAX_HOURS - AXIS_MIN_HOURS)).coerceIn(0f, 1f)
    }

    /** 推荐区间在对照条上的左右边界（0..1） */
    val bandStartFraction: Float get() = (ADULT_MIN_HOURS - AXIS_MIN_HOURS) / (AXIS_MAX_HOURS - AXIS_MIN_HOURS)
    val bandEndFraction: Float get() = (ADULT_MAX_HOURS - AXIS_MIN_HOURS) / (AXIS_MAX_HOURS - AXIS_MIN_HOURS)
}
