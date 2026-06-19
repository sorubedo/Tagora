package com.tagora.app.ui.task

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.tagora.app.util.newId

sealed interface TaskUiState {
    data object Loading : TaskUiState
    data class Error(val throwable: Throwable) : TaskUiState
    data class Success(
        val tags: List<Tag>,
        val tasks: List<Task>,
        val tagsMap: Map<String, Tag>,
        val activeTagIds: Set<String>,
    ) : TaskUiState
}

class TaskViewModel(
    private val periodRepository: TimePeriodRepository,
    private val taskRepository: TaskRepository,
    private val completedTaskRepository: CompletedTaskRepository,
    private val activationEngine: TagActivationEngine,
) : ViewModel() {

    val uiState: StateFlow<TaskUiState> =
        combine<List<Tag>, List<Task>, Set<String>, TaskUiState>(
            periodRepository.tagsFlow,
            taskRepository.tasksFlow,
            activationEngine.activeTagIds,
        ) { tags, tasks, activeTagIds ->
            TaskUiState.Success(
                tags = tags,
                tasks = tasks,
                tagsMap = tags.associateBy { it.id },
                activeTagIds = activeTagIds,
            )
        }.catch { emit(TaskUiState.Error(it)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TaskUiState.Loading,
            )

    fun saveTask(task: Task) {
        viewModelScope.launch {
            val state = uiState.value as? TaskUiState.Success ?: return@launch
            val tasks = state.tasks.toMutableList()
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index >= 0) {
                tasks[index] = task
            } else {
                tasks.add(task)
            }
            taskRepository.saveTasks(tasks)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val state = uiState.value as? TaskUiState.Success ?: return@launch
            val tasks = state.tasks.filter { it.id != taskId }
            taskRepository.saveTasks(tasks)
        }
    }

    fun taskActive(task: Task, activeTagIds: Set<String>): Boolean {
        return task.condition.evaluate(activeTagIds)
    }

    private val completeTaskUseCase = CompleteTaskUseCase(taskRepository, completedTaskRepository)

    /** 手动完成普通事件 */
    fun completeTask(taskId: String) {
        viewModelScope.launch { completeTaskUseCase.execute(taskId) }
    }

    companion object
}
