package com.tagora.app.domain.engine

import android.content.Context
import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskStatus
import com.tagora.app.data.model.TaskType
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.isDaily
import com.tagora.app.data.model.isDate
import com.tagora.app.data.model.isDeadline
import com.tagora.app.data.model.isFixed
import com.tagora.app.data.model.isIncomplete
import com.tagora.app.data.model.isWeekly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

/**
 * 标签自动激活引擎（域层核心）
 *
 * 根据当前系统时间，匹配所有四种类型的 TimePeriod（daily/weekly/date/deadline），
 * 计算出当前应激活的标签 ID 集合，并通过 [activeTagIds] StateFlow 暴露。
 *
 * 刷新时机：
 * - 每 30 秒（对齐 :00 和 :30）自动重算
 * - 任何 TimePeriod 数据变更时（通过 Flow combine）立即重算
 * - 调用 [refresh] 手动触发（如从后台恢复时）
 */
class TagActivationEngine(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository,
    private val completedRepo: CompletedTaskRepository,
    private val context: Context,
) {
    /** 内部激活标签状态，用于 combine 输出 */
    private val _activeTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 当前激活的标签 ID 集合（公开只读） */
    val activeTagIds: StateFlow<Set<String>> = _activeTagIds.asStateFlow()

    /** 所有 daily 类型 TimePeriod 的 tagIds 的并集（当天全部日时间线标签） */
    private val _allDailyTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 当天全部日时间线标签 ID 集合（公开只读） */
    val allDailyTagIds: StateFlow<Set<String>> = _allDailyTagIds.asStateFlow()

    /** 日时间线数量（含无标签的时间段） */
    private val _dailyPeriodCount = MutableStateFlow(0)

    /** 日时间线数量（公开只读） */
    val dailyPeriodCount: StateFlow<Int> = _dailyPeriodCount.asStateFlow()

    /** 所有 weekly 类型 TimePeriod 的 tagIds 的并集（当周全部周时间线标签） */
    private val _allWeeklyTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 当周全部周时间线标签 ID 集合（公开只读） */
    val allWeeklyTagIds: StateFlow<Set<String>> = _allWeeklyTagIds.asStateFlow()

    /** 周时间线数量（含无标签的时间段） */
    private val _weeklyPeriodCount = MutableStateFlow(0)

    /** 周时间线数量（公开只读） */
    val weeklyPeriodCount: StateFlow<Int> = _weeklyPeriodCount.asStateFlow()

    /** 明天将激活的标签 ID 集合（daily + weekly匹配明天星期几 + date匹配明天日期） */
    private val _tomorrowTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 明天标签 ID 集合（公开只读） */
    val tomorrowTagIds: StateFlow<Set<String>> = _tomorrowTagIds.asStateFlow()

    /** 所有 deadline 类型 TimePeriod 未过期的 tagIds 并集 */
    private val _allDeadlineTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 死线标签 ID 集合（公开只读） */
    val allDeadlineTagIds: StateFlow<Set<String>> = _allDeadlineTagIds.asStateFlow()

    /** 未来还有可能激活的标签 ID 集合（用于判断任务是否永远无法满足） */
    private val _futureTagIds = MutableStateFlow<Set<String>>(emptySet())

    /** 未来可能激活的标签（公开只读） */
    val futureTagIds: StateFlow<Set<String>> = _futureTagIds.asStateFlow()

    /** 任务状态变化事件流（供外部订阅发送通知） */
    private val _taskEvents = MutableSharedFlow<TaskEvent>(replay = 0, extraBufferCapacity = 16)

    /** 任务状态变化事件（公开只读） */
    val taskEvents: SharedFlow<TaskEvent> = _taskEvents.asSharedFlow()

    /** 上一次 tick 中处于激活状态的任务 ID 集合（用于检测新激活） */
    private var lastActiveTaskIds: Set<String> = emptySet()

    /** 保护 lastActiveTaskIds 读写，防止 combine 并发触发时竞态 */
    private val taskMutex = Mutex()

    /** 手动刷新触发器 */
    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /** 引擎专用协程作用域，start/stop 控制生命周期 */
    private var scope: CoroutineScope? = null

    /** 是否已启动 */
    val isRunning: Boolean get() = scope != null

    /**
     * 每 30 秒触发，对齐到真实时钟的 :00 和 :30。
     * 启动后立即计算，之后在每分钟的 0 秒和 30 秒准时刷新。
     */
    private val tickerFlow = flow {
        while (true) {
            emit(Unit)
            val now = LocalTime.now()
            val secondsInHalf = now.second % 30
            val secondsToNext = 30 - secondsInHalf
            delay(secondsToNext * 1000L)
        }
    }

    /**
     * 启动引擎：开始监听时间段变化和 ticker，自动计算激活标签。
     * 重复调用无副作用（幂等）。
     */
    fun start() {
        if (scope != null) return

        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope

        @Suppress("UNCHECKED_CAST")
        combine(
            repository.periodsFlow,
            repository.weeklyPeriodsFlow,
            repository.datePeriodsFlow,
            repository.deadlinePeriodsFlow,
            taskRepo.tasksFlow,
            _refreshTrigger.onStart { emit(Unit) },
            tickerFlow.onStart { emit(Unit) },
        ) { values ->
            val dailyPeriods = values[0] as List<TimePeriod>
            val weeklyPeriods = values[1] as List<TimePeriod>
            val datePeriods = values[2] as List<TimePeriod>
            val deadlinePeriods = values[3] as List<TimePeriod>
            val allTasks = values[4] as List<Task>
            val now = LocalTime.now()
            val today = LocalDate.now()

            val dailyTags = matchDailyPeriods(dailyPeriods, now)
            val weeklyTags = matchWeeklyPeriods(weeklyPeriods, today)
            val dateTags = matchDatePeriods(datePeriods, today)
            val deadlineTags = matchDeadlinePeriods(deadlinePeriods, today)

            // 全部未过期死线标签的并集
            _allDeadlineTagIds.value = deadlinePeriods
                .filter { it.isDeadline }
                .flatMap { it.tagIds }
                .toSet()

            // 当天全部日时间线标签的并集（不依赖当前时间）
            val allDailyTags = dailyPeriods
                .filter { it.isDaily }
                .flatMap { it.tagIds }
                .toSet()
            _allDailyTagIds.value = allDailyTags
            _dailyPeriodCount.value = dailyPeriods.size

            // 当周全部周时间线标签的并集（不依赖当前星期几）
            val allWeeklyTags = weeklyPeriods
                .filter { it.isWeekly }
                .flatMap { it.tagIds }
                .toSet()
            _allWeeklyTagIds.value = allWeeklyTags
            _weeklyPeriodCount.value = weeklyPeriods.size

            // 明天将激活的标签：daily全部 + weekly匹配明天星期几 + date匹配明天日期 + deadline匹配明天
            val tomorrow = today.plusDays(1)
            val tomorrowTags = allDailyTags +
                matchWeeklyPeriods(weeklyPeriods, tomorrow) +
                matchDatePeriods(datePeriods, tomorrow) +
                matchDeadlinePeriods(deadlinePeriods, tomorrow)
            _tomorrowTagIds.value = tomorrowTags

            // 未来还有可能激活的标签：daily/weekly 永远可能 + date/deadline 未过期
            val futureDateTags = datePeriods
                .filter { it.isDate }
                .filter { it.endDate?.let { !today.isAfter(LocalDate.parse(it)) } ?: false }
                .flatMap { it.tagIds }.toSet()
            val futureDeadlineTags = deadlinePeriods
                .filter { it.isDeadline }
                .filter { it.endDate?.let { !today.isAfter(LocalDate.parse(it)) } ?: false }
                .flatMap { it.tagIds }.toSet()
            val futureTags = allDailyTags + allWeeklyTags + futureDateTags + futureDeadlineTags
            _futureTagIds.value = futureTags

            // 对每个未完成任务判断是否永远无法满足
            val incompleteTasks = allTasks.filter { it.isIncomplete }
            val nowMillis = System.currentTimeMillis()
            val taskRepoRef = taskRepo
            val completedRepoRef = completedRepo

            // 检测新激活的任务（当前标签条件满足的任务）
            // 使用 Mutex 保护 lastActiveTaskIds，防止 ticker + refresh + 数据变更并发触发导致重复发射事件
            taskMutex.withLock {
                val currentActiveIds = dailyTags + weeklyTags + dateTags + deadlineTags
                val currentActiveTasks = incompleteTasks.filter { it.condition.evaluate(currentActiveIds) }
                val currentActiveTaskIds = currentActiveTasks.map { it.id }.toSet()
                val newlyActivated = currentActiveTaskIds - lastActiveTaskIds
                currentActiveTasks.filter { it.id in newlyActivated }.forEach { task ->
                    _taskEvents.tryEmit(TaskEvent.Activated(task))
                }
                lastActiveTaskIds = currentActiveTaskIds
            }

            incompleteTasks.forEach { task ->
                if (!task.condition.evaluate(futureTags)) {
                    val newStatus = if (task.isFixed) TaskStatus.COMPLETED else TaskStatus.TIMEOUT
                    // 发射超时/完成事件
                    if (newStatus == TaskStatus.TIMEOUT) {
                        _taskEvents.tryEmit(TaskEvent.TimedOut(task))
                    } else {
                        _taskEvents.tryEmit(TaskEvent.Completed(task))
                    }
                    newScope.launch {
                        val updated = task.copy(status = newStatus, completedAt = nowMillis)
                        val tasks = taskRepoRef.tasksFlow.first().filter { it.id != task.id }
                        taskRepoRef.saveTasks(tasks)
                        completedRepoRef.addCompletedTask(updated)
                    }
                }
            }

            dailyTags + weeklyTags + dateTags + deadlineTags
        }
            .catch {
                // 异常时降级为空集合，避免引擎停止工作
                _activeTagIds.value = emptySet()
                _allDailyTagIds.value = emptySet()
                _dailyPeriodCount.value = 0
                _allWeeklyTagIds.value = emptySet()
                _weeklyPeriodCount.value = 0
                _tomorrowTagIds.value = emptySet()
                _allDeadlineTagIds.value = emptySet()
                _futureTagIds.value = emptySet()
            }
            .onEach { tagIds ->
                _activeTagIds.value = tagIds
            }
            .launchIn(newScope)
    }

    /**
     * 停止引擎：取消所有协程，释放资源。
     */
    fun stop() {
        scope?.cancel()
        scope = null
    }

    /**
     * 手动触发一次立即刷新（忽略当前 ticker 周期）。
     * 用于从后台恢复等场景。
     */
    fun refresh() {
        _refreshTrigger.tryEmit(Unit)
    }

    // ── 时间匹配算法 ──────────────────────────────────────────────

    /**
     * 匹配 daily 类型时间段。
     * 当前分钟数 ∈ [startMinute, endMinute)，支持跨午夜（startMinute > endMinute）。
     */
    private fun matchDailyPeriods(periods: List<TimePeriod>, now: LocalTime): Set<String> {
        val currentMinute = now.hour * 60 + now.minute
        return periods
            .filter { it.isDaily }
            .filter { period ->
                when {
                    period.startMinute < period.endMinute ->
                        currentMinute in period.startMinute until period.endMinute
                    period.startMinute > period.endMinute ->
                        currentMinute >= period.startMinute || currentMinute < period.endMinute
                    else -> false // startMinute == endMinute: 永不匹配
                }
            }
            .flatMap { it.tagIds }
            .toSet()
    }

    /**
     * 匹配 weekly 类型时间段。
     * DayOfWeek.MONDAY.value == 1 .. SUNDAY.value == 7，与 dayOfWeeks 约定一致。
     */
    private fun matchWeeklyPeriods(periods: List<TimePeriod>, today: LocalDate): Set<String> {
        val currentDayOfWeek = today.dayOfWeek.value
        return periods
            .filter { it.isWeekly }
            .filter { period -> period.dayOfWeeks.contains(currentDayOfWeek) }
            .flatMap { it.tagIds }
            .toSet()
    }

    /**
     * 匹配 date 类型时间段。
     * 日期范围包含起止两端。
     */
    private fun matchDatePeriods(periods: List<TimePeriod>, today: LocalDate): Set<String> {
        return periods
            .filter { it.isDate }
            .filter { period ->
                val start = period.startDate?.let { LocalDate.parse(it) } ?: return@filter false
                val end = period.endDate?.let { LocalDate.parse(it) } ?: return@filter false
                !today.isBefore(start) && !today.isAfter(end)
            }
            .flatMap { it.tagIds }
            .toSet()
    }

    /**
     * 匹配 deadline 类型时间段。
     * 在截止日期之前（含当天）始终激活标签，无起始时间限制。
     */
    private fun matchDeadlinePeriods(periods: List<TimePeriod>, today: LocalDate): Set<String> {
        return periods
            .filter { it.isDeadline }
            .filter { period ->
                val deadline = period.endDate?.let { LocalDate.parse(it) } ?: return@filter false
                !today.isAfter(deadline) // today <= deadline
            }
            .flatMap { it.tagIds }
            .toSet()
    }
}
