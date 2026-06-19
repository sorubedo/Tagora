package com.tagora.app.ui.timeperiod

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagora.app.domain.engine.TagActivationEngine
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.domain.usecase.DeleteTagUseCase
import com.tagora.app.domain.usecase.ResetToDefaultUseCase
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimePeriodViewModel(
    private val repository: TimePeriodRepository,
    private val activationEngine: TagActivationEngine,
    private val taskRepo: TaskRepository,
) : ViewModel() {

    // 使用嵌套 combine 避免 @Suppress("UNCHECKED_CAST")（类型安全重载上限为 5）
    private val periodData = combine(
        repository.tagsFlow,
        repository.periodsFlow,
        repository.weeklyPeriodsFlow,
        repository.datePeriodsFlow,
        repository.deadlinePeriodsFlow,
    ) { tags, periods, weeklyPeriods, datePeriods, deadlinePeriods ->
        PeriodData(tags, periods, weeklyPeriods, datePeriods, deadlinePeriods)
    }

    val uiState: StateFlow<TimePeriodUiState> =
        combine<PeriodData, Set<String>, List<Task>, TimePeriodUiState>(
            periodData, activationEngine.activeTagIds, taskRepo.tasksFlow
        ) { data, activeTagIds, tasks ->
            TimePeriodUiState.Success(
                tags = data.tags,
                periodsSorted = data.periods.sortedBy { it.startMinute },
                weeklyPeriodsSorted = data.weeklyPeriods.sortedBy { it.dayOfWeeks.firstOrNull() ?: 99 },
                datePeriodsSorted = data.datePeriods.sortedBy { it.startDate ?: "9999-12-31" },
                deadlinePeriodsSorted = data.deadlinePeriods.sortedBy { it.endDate ?: "9999-12-31" },
                tagsMap = data.tags.associateBy { it.id },
                activeTagIds = activeTagIds,
                tasks = tasks,
            )
        }.catch { emit(TimePeriodUiState.Error(it)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TimePeriodUiState.Loading,
            )

    private data class PeriodData(
        val tags: List<Tag>,
        val periods: List<TimePeriod>,
        val weeklyPeriods: List<TimePeriod>,
        val datePeriods: List<TimePeriod>,
        val deadlinePeriods: List<TimePeriod>,
    )

    // ── Tag CRUD ──────────────────────────────────────────────────

    private val deleteTagUseCase = DeleteTagUseCase(repository)

    fun deleteTag(id: String) {
        viewModelScope.launch { deleteTagUseCase.execute(id) }
    }

    // ── Week Reset ────────────────────────────────────────────────

    fun resetWeekPeriods(firstDate: String) {
        viewModelScope.launch {
            repository.resetWeekPeriods(firstDate)
        }
    }

    // ── Export / Import ───────────────────────────────────────────

    fun exportConfig(uri: Uri) {
        viewModelScope.launch {
            repository.exportAllToUri(uri)
        }
    }

    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.importAllFromUri(uri)
            } catch (e: Exception) {
                android.util.Log.e("TimePeriodViewModel", "导入配置失败: $uri", e)
            }
        }
    }

    // ── Reset to Default ──────────────────────────────────────────

    private val resetToDefaultUseCase = ResetToDefaultUseCase(repository)

    fun resetToDefault() {
        viewModelScope.launch { resetToDefaultUseCase.execute() }
    }

    companion object
}
