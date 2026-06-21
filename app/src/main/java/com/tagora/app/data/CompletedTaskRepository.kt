package com.tagora.app.data

import com.tagora.app.data.model.Task
import kotlinx.coroutines.flow.Flow

interface CompletedTaskRepository {
    val completedTasksFlow: Flow<List<Task>>
    suspend fun saveCompletedTasks(tasks: List<Task>)
    suspend fun addCompletedTask(task: Task)
    suspend fun loadDefaultCompletedTasks(): List<Task>
}
