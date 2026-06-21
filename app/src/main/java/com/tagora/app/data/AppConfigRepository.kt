package com.tagora.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tagora.app.data.model.AppConfig
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskConditionSerializersModule
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.preset.PresetRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate

/**
 * 应用全局配置仓库。
 *
 * 使用单个 [JsonFileRepository] 管理所有配置数据（config.json），
 * 同时实现 [TimePeriodRepository]、[TaskRepository] 和 [CompletedTaskRepository] 三个接口。
 *
 * 写操作通过 [Mutex] 序列化，避免并发读写导致数据覆盖。
 */
class AppConfigRepository(
    private val context: Context,
    private val prefs: AppPreferences? = null,
) : TimePeriodRepository, TaskRepository, CompletedTaskRepository {

    private val json = Json {
        serializersModule = TaskConditionSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val mutex = Mutex()

    private val configRepo = JsonFileRepository(
        context = context.applicationContext,
        fileName = "config.json",
        serializer = AppConfig.serializer(),
        json = json,
        defaultAssetName = "default_config.json",
    )

    // ── Flows ───────────────────────────────────────────────────────

    override val tagsFlow: Flow<List<Tag>> = configRepo.flow.map { it.tags }
    override val periodsFlow: Flow<List<TimePeriod>> = configRepo.flow.map { it.periods }
    override val weeklyPeriodsFlow: Flow<List<TimePeriod>> = configRepo.flow.map { it.weeklyPeriods }
    override val datePeriodsFlow: Flow<List<TimePeriod>> = configRepo.flow.map { it.datePeriods }
    override val deadlinePeriodsFlow: Flow<List<TimePeriod>> = configRepo.flow.map { it.deadlinePeriods }
    override val tasksFlow: Flow<List<Task>> = configRepo.flow.map { it.tasks }
    override val completedTasksFlow: Flow<List<Task>> = configRepo.flow.map { it.completedTasks }

    // ── Save ────────────────────────────────────────────────────────

    override suspend fun saveTags(tags: List<Tag>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(tags = tags))
    }

    override suspend fun savePeriods(periods: List<TimePeriod>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(periods = periods))
    }

    override suspend fun saveWeeklyPeriods(periods: List<TimePeriod>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(weeklyPeriods = periods))
    }

    override suspend fun saveDatePeriods(periods: List<TimePeriod>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(datePeriods = periods))
    }

    override suspend fun saveDeadlinePeriods(periods: List<TimePeriod>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(deadlinePeriods = periods))
    }

    override suspend fun saveTasks(tasks: List<Task>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(tasks = tasks))
    }

    override suspend fun saveCompletedTasks(tasks: List<Task>) = mutex.withLock {
        configRepo.save(configRepo.value.copy(completedTasks = tasks))
    }

    override suspend fun addCompletedTask(task: Task) = mutex.withLock {
        val current = configRepo.value
        configRepo.save(current.copy(completedTasks = current.completedTasks + task))
    }

    // ── Load Defaults ───────────────────────────────────────────────

    override suspend fun loadDefaultTags(): List<Tag> = loadDefaultConfig().tags
    override suspend fun loadDefaultPeriods(): List<TimePeriod> = loadDefaultConfig().periods
    override suspend fun loadDefaultWeeklyPeriods(): List<TimePeriod> = loadDefaultConfig().weeklyPeriods
    override suspend fun loadDefaultDatePeriods(): List<TimePeriod> = loadDefaultConfig().datePeriods
    override suspend fun loadDefaultDeadlinePeriods(): List<TimePeriod> = loadDefaultConfig().deadlinePeriods
    override suspend fun loadDefaultTasks(): List<Task> = loadDefaultConfig().tasks
    override suspend fun loadDefaultCompletedTasks(): List<Task> = loadDefaultConfig().completedTasks

    /**
     * 从当前选中的预设加载完整默认配置。
     * 优先使用 PresetProvider，失败时回退到内置默认预设。
     */
    private suspend fun loadDefaultConfig(): AppConfig = withContext(Dispatchers.IO) {
        val provider = PresetRegistry.getSelectedProvider(prefs)
        if (provider != null) {
            try {
                return@withContext configRepo.loadAndWriteDefault(provider, "config.json")
            } catch (e: Exception) {
                Log.w("AppConfigRepo", "从 PresetProvider 加载 config.json 失败，回退到内置默认预设", e)
            }
        }
        // 回退到内置默认预设
        val defaultProvider = PresetRegistry.getProvider(null)
            ?: throw IllegalStateException("内置默认预设未注册")
        configRepo.loadAndWriteDefault(defaultProvider, "config.json")
    }

    // ── Reset Week Periods ──────────────────────────────────────────

    override suspend fun resetWeekPeriods(firstDate: String) = mutex.withLock {
        val defaultConfig = loadDefaultConfig()
        val weekTagIds = defaultConfig.datePeriods.flatMap { it.tagIds }.toSet()
        val currentPeriods = configRepo.value.datePeriods

        val remaining = currentPeriods.filter { period ->
            period.tagIds.none { it in weekTagIds }
        }

        val baseDate = LocalDate.parse(firstDate)
        val newWeeks = defaultConfig.datePeriods.mapIndexed { index, template ->
            val weekStart = baseDate.plusDays((index * 7).toLong())
            val weekEnd = weekStart.plusDays(6)
            template.copy(
                startDate = weekStart.toString(),
                endDate = weekEnd.toString(),
            )
        }

        configRepo.save(configRepo.value.copy(datePeriods = remaining + newWeeks))
    }

    // ── Export ──────────────────────────────────────────────────────

    override suspend fun exportAllToUri(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val data = json.encodeToString(AppConfig.serializer(), configRepo.value)
            out.write(data.toByteArray())
        } ?: throw IOException("无法写入文件")
    }

    // ── Import ──────────────────────────────────────────────────────

    override suspend fun importAllFromUri(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IOException("无法读取文件")
        val config = json.decodeFromString<AppConfig>(text)
        mutex.withLock {
            configRepo.save(config)
        }
    }
}
