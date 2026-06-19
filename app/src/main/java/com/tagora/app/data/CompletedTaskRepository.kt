package com.tagora.app.data

import android.content.Context
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskConditionSerializersModule
import com.tagora.app.data.model.TaskConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

interface CompletedTaskRepository {
    val completedTasksFlow: Flow<List<Task>>
    suspend fun saveCompletedTasks(tasks: List<Task>)
    suspend fun addCompletedTask(task: Task)
    suspend fun loadDefaultCompletedTasks(): List<Task>
}

class DefaultCompletedTaskRepository(
    context: Context,
    json: Json = Json {
        serializersModule = TaskConditionSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
) : JsonFileRepository<TaskConfig>(
    context = context,
    fileName = "completed_tasks.json",
    serializer = TaskConfig.serializer(),
    json = json,
    defaultAssetName = "default_completed_tasks.json",
), CompletedTaskRepository {

    override val completedTasksFlow: Flow<List<Task>> = flow.map { it.tasks }

    override suspend fun saveCompletedTasks(tasks: List<Task>) {
        save(TaskConfig(tasks = tasks))
    }

    override suspend fun addCompletedTask(task: Task) = withContext(Dispatchers.IO) {
        val current = value.tasks
        saveCompletedTasks(current + task)
    }

    override suspend fun loadDefaultCompletedTasks(): List<Task> = withContext(Dispatchers.IO) {
        loadAndWriteDefault().tasks
    }

    /** 已完成任务的默认文件可能为空，读取失败时回退到空列表 */
    override fun loadAndWriteDefault(): TaskConfig {
        return try {
            super.loadAndWriteDefault()
        } catch (_: IOException) {
            TaskConfig(tasks = emptyList())
        }
    }
}
