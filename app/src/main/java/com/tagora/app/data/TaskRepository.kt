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

interface TaskRepository {
    val tasksFlow: Flow<List<Task>>
    suspend fun saveTasks(tasks: List<Task>)
    suspend fun loadDefaultTasks(): List<Task>
}

class DefaultTaskRepository(
    context: Context,
    json: Json = Json {
        serializersModule = TaskConditionSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
) : JsonFileRepository<TaskConfig>(
    context = context,
    fileName = "tasks.json",
    serializer = TaskConfig.serializer(),
    json = json,
    defaultAssetName = "default_tasks.json",
), TaskRepository {

    override val tasksFlow: Flow<List<Task>> = flow.map { it.tasks }

    override suspend fun saveTasks(tasks: List<Task>) {
        save(TaskConfig(tasks = tasks))
    }

    override suspend fun loadDefaultTasks(): List<Task> = withContext(Dispatchers.IO) {
        loadAndWriteDefault().tasks
    }
}
