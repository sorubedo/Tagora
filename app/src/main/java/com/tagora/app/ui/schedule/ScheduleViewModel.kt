package com.tagora.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.collectTagIds
import com.tagora.app.data.model.isDaily
import com.tagora.app.data.model.isDate
import com.tagora.app.data.model.isFixed
import com.tagora.app.data.model.isIncomplete
import com.tagora.app.data.model.isWeekly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 课程表周视图 ViewModel。
 *
 * 合并 6 个 Flow（5 个 Repository + 1 个周索引），
 * 为当前周计算每个 (行=课程节次, 列=星期几) 格子上激活的 FIXED 任务。
 *
 * 核心算法：对每个格子构造"合成激活标签集合"
 * = 日时段标签 ∪ 周时段标签 ∪ 日期时段标签，
 * 然后用它评估 FIXED 任务的 TaskCondition。
 */
class ScheduleViewModel(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository,
) : ViewModel() {

    /** 当前教学周索引（0-based），用户可通过左右按钮切换 */
    private val _currentWeekIndex = MutableStateFlow(0)

    /** 暴露只读版本给 UI（仅用于显示，不用于修改） */
    val currentWeekIndex: StateFlow<Int> = _currentWeekIndex

    // ── 中间数据类 ────────────────────────────────────────────────

    private data class GridData(
        val tags: List<Tag>,
        val dailyPeriods: List<TimePeriod>,
        val weeklyPeriods: List<TimePeriod>,
        val datePeriods: List<TimePeriod>,
        val tasks: List<Task>,
    )

    // ── 课程节次分类正则 ──────────────────────────────────────────

    private companion object {
        /** 匹配 "t-class-N" 格式的标签（N 为 1~12 的数字） */
        val CLASS_TAG_REGEX = Regex("^t-class-(\\d+)$")
        /** 匹配 "t-w-N" 格式的标签（N 为 1~20 的数字） */
        val WEEK_TAG_REGEX = Regex("^t-w(\\d+)$")

        /** 从标签 ID 中提取序号，用于排序 */
        fun extractClassNumber(tagIds: List<String>): Int =
            tagIds.firstNotNullOfOrNull { id ->
                CLASS_TAG_REGEX.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull()
            } ?: Int.MAX_VALUE

        fun extractWeekNumber(tagIds: List<String>): Int =
            tagIds.firstNotNullOfOrNull { id ->
                WEEK_TAG_REGEX.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull()
            } ?: Int.MAX_VALUE
    }

    // ── 自动检测当前教学周 ──────────────────────────────────────────

    init {
        viewModelScope.launch {
            val periods = repository.datePeriodsFlow.first()
            val weeks = periods
                .filter { it.isDate && it.tagIds.any { tag -> WEEK_TAG_REGEX.matches(tag) } }
                .sortedBy { it.startDate }
            if (weeks.isNotEmpty()) {
                val today = LocalDate.now()
                val detectedIndex = weeks.indexOfFirst { week ->
                    val start = week.startDate?.let { LocalDate.parse(it) } ?: return@indexOfFirst false
                    val end = week.endDate?.let { LocalDate.parse(it) } ?: return@indexOfFirst false
                    !today.isBefore(start) && !today.isAfter(end)
                }
                if (detectedIndex >= 0) {
                    _currentWeekIndex.value = detectedIndex
                }
                // 如果不在 1~20 周内，保持默认 0（第 1 周）
            }
        }
    }

    // ── UI 状态 ───────────────────────────────────────────────────

    val uiState: StateFlow<ScheduleUiState> = run {
        // 第一层：合并 5 个 Repository Flow
        val gridData = combine(
            repository.tagsFlow,
            repository.periodsFlow,
            repository.weeklyPeriodsFlow,
            repository.datePeriodsFlow,
            taskRepo.tasksFlow,
        ) { tags, daily, weekly, date, tasks ->
            GridData(tags, daily, weekly, date, tasks)
        }

        // 第二层：合并 GridData + 当前周索引
        combine(gridData, _currentWeekIndex) { data, weekIdx ->
            buildUiState(data, weekIdx)
        }.catch { e ->
            emit(ScheduleUiState.Error("数据加载失败: ${e.message}"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleUiState.Loading,
        )
    }

    // ── 核心计算逻辑 ──────────────────────────────────────────────

    private fun buildUiState(data: GridData, rawWeekIndex: Int): ScheduleUiState {
        // 1. 分类时间段
        val classPeriods = data.dailyPeriods
            .filter { it.isDaily && it.tagIds.any { tag -> CLASS_TAG_REGEX.matches(tag) } }
            .sortedBy { extractClassNumber(it.tagIds) }

        val weekDays = data.weeklyPeriods
            .filter { it.isWeekly && it.dayOfWeeks.size == 1 }
            .sortedBy { it.dayOfWeeks.first() }

        val weeks = data.datePeriods
            .filter { it.isDate && it.tagIds.any { tag -> WEEK_TAG_REGEX.matches(tag) } }
            .sortedBy { it.startDate }

        // 无周次数据 → 拒绝显示
        if (weeks.isEmpty()) {
            return ScheduleUiState.Error(
                "当前配置中没有周次数据，无法显示课程表视图。\n" +
                    "请先在设置中切换到「学生学期课程」预设，或手动添加教学周时间段。"
            )
        }

        // 2. 安全 clamp 周索引
        val weekIndex = rawWeekIndex.coerceIn(0, weeks.lastIndex)
        val weekPeriod = weeks[weekIndex]

        // 3. FIXED 任务（仅未完成 + 条件非空）
        val fixedTasks = data.tasks.filter {
            it.isFixed && it.isIncomplete && it.condition.collectTagIds().isNotEmpty()
        }

        // 4. 为每个 (row, col) 计算激活的任务
        val cellTaskMap = mutableMapOf<Pair<Int, Int>, List<Task>>()

        for ((rowIdx, classPeriod) in classPeriods.withIndex()) {
            for ((colIdx, dayPeriod) in weekDays.withIndex()) {
                val cellActiveTags = (
                    classPeriod.tagIds.toSet() +
                        dayPeriod.tagIds.toSet() +
                        weekPeriod.tagIds.toSet()
                    )
                val cellTasks = fixedTasks.filter { task ->
                    task.condition.evaluate(cellActiveTags)
                }
                if (cellTasks.isNotEmpty()) {
                    cellTaskMap[rowIdx to colIdx] = cellTasks
                }
            }
        }

        // 5. 计算相邻格子合并信息
        // 规则：对每一列，从上到下遍历行
        // 如果当前格子与上方格子的任务 ID 集合完全相同（且都非空），则标记当前格子为"合并"
        val cellMergeMap = mutableMapOf<Pair<Int, Int>, Boolean>()
        for (colIdx in weekDays.indices) {
            for (rowIdx in 1 until classPeriods.size) {
                val currentTaskIds = cellTaskMap[rowIdx to colIdx]
                    ?.map { it.id }?.toSet() ?: emptySet()
                val aboveTaskIds = cellTaskMap[rowIdx - 1 to colIdx]
                    ?.map { it.id }?.toSet() ?: emptySet()
                if (currentTaskIds.isNotEmpty() && currentTaskIds == aboveTaskIds) {
                    cellMergeMap[rowIdx to colIdx] = true
                }
            }
        }

        return ScheduleUiState.Ready(
            classPeriods = classPeriods,
            weekDays = weekDays,
            weeks = weeks,
            currentWeekIndex = weekIndex,
            cellTaskMap = cellTaskMap,
            tagsMap = data.tags.associateBy { it.id },
            cellMergeMap = cellMergeMap,
        )
    }

    // ── 公开操作 ──────────────────────────────────────────────────

    /** 切换到指定教学周 */
    fun setWeekIndex(index: Int) {
        _currentWeekIndex.value = index
    }

    /** 上一周 */
    fun previousWeek() {
        if (_currentWeekIndex.value > 0) {
            _currentWeekIndex.value -= 1
        }
    }

    /** 下一周（调用方需自行保证不越界，或由 combine 中 clamp 兜底） */
    fun nextWeek(maxIndex: Int) {
        if (_currentWeekIndex.value < maxIndex) {
            _currentWeekIndex.value += 1
        }
    }
}
