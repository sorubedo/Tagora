package com.tagora.app.data

import com.tagora.app.data.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    val tasksFlow: Flow<List<Task>>
    suspend fun saveTasks(tasks: List<Task>)
    suspend fun loadDefaultTasks(): List<Task>
}
