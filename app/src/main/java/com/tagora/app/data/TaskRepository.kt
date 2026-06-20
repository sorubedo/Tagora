package com.tagora.app.data

import android.content.Context
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskConditionSerializersModule
import com.tagora.app.data.model.TaskConfig
import com.tagora.app.data.preset.PresetRegistry
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
    private val prefs: AppPreferences? = null,
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
        val provider = PresetRegistry.getSelectedProvider(prefs)
        if (provider != null) {
            try {
                loadAndWriteDefault(provider, "tasks.json").tasks
            } catch (e: Exception) {
                android.util.Log.w("TaskRepo", "从 PresetProvider 加载 tasks.json 失败，回退到内置预设", e)
                loadAndWriteDefault().tasks
            }
        } else {
            loadAndWriteDefault().tasks
        }
    }
}
