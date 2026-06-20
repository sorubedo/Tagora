package com.tagora.app.ui.schedule

import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod

/**
 * 课程表周视图的 UI 状态。
 */
sealed interface ScheduleUiState {
    /** 初始加载中 */
    data object Loading : ScheduleUiState

    /** 加载失败（如无周次数据） */
    data class Error(val message: String) : ScheduleUiState

    /** 数据就绪，包含网格渲染所需的所有信息 */
    data class Ready(
        /** 课程节次（行标签），按 startMinute 排序 */
        val classPeriods: List<TimePeriod>,
        /** 星期几（列标签），按 dayOfWeeks[0] 排序（1=周一~7=周日） */
        val weekDays: List<TimePeriod>,
        /** 教学周（滑动目标），按 startDate 排序 */
        val weeks: List<TimePeriod>,
        /** 当前周索引，0-based，已 clamp 到 [0, weeks.size-1] */
        val currentWeekIndex: Int,
        /** (row, col) -> 该格子的 FIXED 任务列表 */
        val cellTaskMap: Map<Pair<Int, Int>, List<Task>>,
        /** 所有标签，keyed by id，用于显示颜色/名称 */
        val tagsMap: Map<String, Tag>,
    ) : ScheduleUiState
}
