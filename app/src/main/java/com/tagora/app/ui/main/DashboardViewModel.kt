package com.tagora.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.domain.engine.TagActivationEngine
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.domain.usecase.CompleteTaskUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val error: Throwable? = null,
    val tasks: List<Task> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val activeTagIds: Set<String> = emptySet(),
    val dailyTagIds: Set<String> = emptySet(),
    val dailyPeriodCount: Int = 0,
    val weeklyTagIds: Set<String> = emptySet(),
    val weeklyPeriodCount: Int = 0,
    val tomorrowTagIds: Set<String> = emptySet(),
    val deadlineTagIds: Set<String> = emptySet(),
)

/**
 * 控制面板 ViewModel。
 * 使用嵌套 combine 合并 9 个 Flow，输出单一 StateFlow<DashboardUiState>。
 */
class DashboardViewModel(
    private val engine: TagActivationEngine,
    private val taskRepo: TaskRepository,
    private val repository: TimePeriodRepository,
    private val completedTaskRepo: CompletedTaskRepository,
) : ViewModel() {

    private val completeTaskUseCase = CompleteTaskUseCase(taskRepo, completedTaskRepo)

    private data class EngineData(
        val activeTagIds: Set<String>,
        val dailyTagIds: Set<String>,
        val dailyPeriodCount: Int,
        val weeklyTagIds: Set<String>,
        val weeklyPeriodCount: Int,
    )

    val uiState: StateFlow<DashboardUiState> = run {
        // 第一层：合并 5 个引擎 Flow（combine 类型安全重载上限为 5）
        val engineData = combine(
            engine.activeTagIds,
            engine.allDailyTagIds,
            engine.dailyPeriodCount,
            engine.allWeeklyTagIds,
            engine.weeklyPeriodCount,
        ) { activeIds, dailyIds, dailyCount, weeklyIds, weeklyCount ->
            EngineData(activeIds, dailyIds, dailyCount, weeklyIds, weeklyCount)
        }

        // 第二层：合并 engineData + 剩余 4 个 Flow
        combine(
            engineData,
            engine.tomorrowTagIds,
            engine.allDeadlineTagIds,
            taskRepo.tasksFlow,
            repository.tagsFlow,
        ) { ed, tomorrowIds, deadlineIds, tasks, tags ->
            DashboardUiState(
                loading = false,
                tasks = tasks,
                allTags = tags,
                activeTagIds = ed.activeTagIds,
                dailyTagIds = ed.dailyTagIds,
                dailyPeriodCount = ed.dailyPeriodCount,
                weeklyTagIds = ed.weeklyTagIds,
                weeklyPeriodCount = ed.weeklyPeriodCount,
                tomorrowTagIds = tomorrowIds,
                deadlineTagIds = deadlineIds,
            )
        }.catch { e ->
            emit(DashboardUiState(loading = false, error = e))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(loading = true),
        )
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch { completeTaskUseCase.execute(taskId) }
    }
}
