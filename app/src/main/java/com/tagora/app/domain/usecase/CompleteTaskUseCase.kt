package com.tagora.app.domain.usecase

import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.model.TaskStatus
import com.tagora.app.data.model.isNormal
import kotlinx.coroutines.flow.first

/**
 * 完成一个普通任务：从活跃任务列表中移除，添加到已完成仓库。
 *
 * 在 DashboardViewModel 和 TaskViewModel 中原本各有一份相同的内联逻辑，
 * 提取为 UseCase 消除重复。
 */
class CompleteTaskUseCase(
    private val taskRepo: TaskRepository,
    private val completedRepo: CompletedTaskRepository,
) {
    suspend fun execute(taskId: String) {
        val tasks = taskRepo.tasksFlow.first()
        val task = tasks.find { it.id == taskId } ?: return
        if (!task.isNormal) return
        val completed = task.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
        )
        taskRepo.saveTasks(tasks.filter { it.id != taskId })
        completedRepo.addCompletedTask(completed)
    }
}
