package com.tagora.app.data

import android.content.Context
import android.net.Uri
import com.tagora.app.data.model.MergedFullConfig
import com.tagora.app.data.model.PeriodConfig
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.TagConfig
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskConditionSerializersModule
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.preset.PresetProvider
import com.tagora.app.data.preset.PresetRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate

data class FullImportResult(
    val tags: List<Tag>,
    val periods: List<TimePeriod>,
    val weeklyPeriods: List<TimePeriod>,
    val datePeriods: List<TimePeriod>,
    val deadlinePeriods: List<TimePeriod> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
)

interface TimePeriodRepository {
    val tagsFlow: Flow<List<Tag>>
    val periodsFlow: Flow<List<TimePeriod>>
    val weeklyPeriodsFlow: Flow<List<TimePeriod>>
    val datePeriodsFlow: Flow<List<TimePeriod>>
    val deadlinePeriodsFlow: Flow<List<TimePeriod>>
    suspend fun saveTags(tags: List<Tag>)
    suspend fun savePeriods(periods: List<TimePeriod>)
    suspend fun saveWeeklyPeriods(periods: List<TimePeriod>)
    suspend fun saveDatePeriods(periods: List<TimePeriod>)
    suspend fun saveDeadlinePeriods(periods: List<TimePeriod>)
    suspend fun loadDefaultTags(): List<Tag>
    suspend fun loadDefaultPeriods(): List<TimePeriod>
    suspend fun loadDefaultWeeklyPeriods(): List<TimePeriod>
    suspend fun loadDefaultDatePeriods(): List<TimePeriod>
    suspend fun loadDefaultDeadlinePeriods(): List<TimePeriod>
    suspend fun resetWeekPeriods(firstDate: String)
    suspend fun exportTagsToUri(uri: Uri)
    suspend fun exportPeriodsToUri(uri: Uri)
    suspend fun exportAllToUri(uri: Uri, tasks: List<Task> = emptyList(), completedTasks: List<Task> = emptyList())
    suspend fun importTagsFromUri(uri: Uri): List<Tag>
    suspend fun importPeriodsFromUri(uri: Uri): List<TimePeriod>
    suspend fun importFromUri(uri: Uri): Pair<List<Tag>, List<TimePeriod>>
    suspend fun importAllFromUri(uri: Uri): FullImportResult
}

class DefaultTimePeriodRepository(
    private val context: Context,
    private val prefs: AppPreferences? = null,
    private val json: Json = Json {
        serializersModule = TaskConditionSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
) : TimePeriodRepository {

    private val tagsRepo = JsonFileRepository(
        context, "tags.json", TagConfig.serializer(), json, "default_tags.json"
    )
    private val periodsRepo = JsonFileRepository(
        context, "periods.json", PeriodConfig.serializer(), json, "default_periods.json"
    )
    private val weeklyPeriodsRepo = JsonFileRepository(
        context, "weekly_periods.json", PeriodConfig.serializer(), json, "default_weekly_periods.json"
    )
    private val datePeriodsRepo = JsonFileRepository(
        context, "date_periods.json", PeriodConfig.serializer(), json, "default_date_periods.json"
    )
    private val deadlinePeriodsRepo = JsonFileRepository(
        context, "deadline_periods.json", PeriodConfig.serializer(), json, "default_deadline_periods.json"
    )

    override val tagsFlow: Flow<List<Tag>> = tagsRepo.flow.map { it.tags }
    override val periodsFlow: Flow<List<TimePeriod>> = periodsRepo.flow.map { it.periods }
    override val weeklyPeriodsFlow: Flow<List<TimePeriod>> = weeklyPeriodsRepo.flow.map { it.periods }
    override val datePeriodsFlow: Flow<List<TimePeriod>> = datePeriodsRepo.flow.map { it.periods }
    override val deadlinePeriodsFlow: Flow<List<TimePeriod>> = deadlinePeriodsRepo.flow.map { it.periods }

    // ── Save ──────────────────────────────────────────────────────

    override suspend fun saveTags(tags: List<Tag>) = tagsRepo.save(TagConfig(tags = tags))
    override suspend fun savePeriods(periods: List<TimePeriod>) = periodsRepo.save(PeriodConfig(periods = periods))
    override suspend fun saveWeeklyPeriods(periods: List<TimePeriod>) = weeklyPeriodsRepo.save(PeriodConfig(periods = periods))
    override suspend fun saveDatePeriods(periods: List<TimePeriod>) = datePeriodsRepo.save(PeriodConfig(periods = periods))
    override suspend fun saveDeadlinePeriods(periods: List<TimePeriod>) = deadlinePeriodsRepo.save(PeriodConfig(periods = periods))

    // ── Load Defaults ─────────────────────────────────────────────

    override suspend fun loadDefaultTags() = withContext(Dispatchers.IO) {
        loadDefaultWithProvider("tags.json") { tagsRepo.loadAndWriteDefault(it, "tags.json").tags }
    }
    override suspend fun loadDefaultPeriods() = withContext(Dispatchers.IO) {
        loadDefaultWithProvider("periods.json") { periodsRepo.loadAndWriteDefault(it, "periods.json").periods }
    }
    override suspend fun loadDefaultWeeklyPeriods() = withContext(Dispatchers.IO) {
        loadDefaultWithProvider("weekly_periods.json") { weeklyPeriodsRepo.loadAndWriteDefault(it, "weekly_periods.json").periods }
    }
    override suspend fun loadDefaultDatePeriods() = withContext(Dispatchers.IO) {
        loadDefaultWithProvider("date_periods.json") { datePeriodsRepo.loadAndWriteDefault(it, "date_periods.json").periods }
    }
    override suspend fun loadDefaultDeadlinePeriods() = withContext(Dispatchers.IO) {
        loadDefaultWithProvider("deadline_periods.json") { deadlinePeriodsRepo.loadAndWriteDefault(it, "deadline_periods.json").periods }
    }

    /**
     * 通用的默认数据加载策略：优先从 PresetProvider 读取，
     * 失败时回退到内置默认预设（根级 default_*.json）。
     */
    private suspend fun <T> loadDefaultWithProvider(
        fileName: String,
        fromProvider: suspend (PresetProvider) -> T,
    ): T {
        val provider = PresetRegistry.getSelectedProvider(prefs)
        if (provider != null) {
            try {
                return fromProvider(provider)
            } catch (e: Exception) {
                android.util.Log.w("TimePeriodRepo", "从 PresetProvider 加载 $fileName 失败，回退到内置默认预设", e)
            }
        }
        // 回退：使用内置默认预设（即未选择任何预设时的根级 default_*.json）
        val defaultProvider = PresetRegistry.getProvider(null)
            ?: throw IllegalStateException("内置默认预设未注册")
        return fromProvider(defaultProvider)
    }

    // ── Reset Week Periods ────────────────────────────────────────

    override suspend fun resetWeekPeriods(firstDate: String) = withContext(Dispatchers.IO) {
        val defaultPeriods = loadDefaultDatePeriods()
        val weekTagIds = defaultPeriods.flatMap { it.tagIds }.toSet()
        val currentPeriods = datePeriodsRepo.value.periods

        val remaining = currentPeriods.filter { period ->
            period.tagIds.none { it in weekTagIds }
        }

        val baseDate = LocalDate.parse(firstDate)
        val newWeeks = defaultPeriods.mapIndexed { index, template ->
            val weekStart = baseDate.plusDays((index * 7).toLong())
            val weekEnd = weekStart.plusDays(6)
            template.copy(
                startDate = weekStart.toString(),
                endDate = weekEnd.toString(),
            )
        }

        saveDatePeriods(remaining + newWeeks)
    }

    // ── Export ────────────────────────────────────────────────────

    override suspend fun exportTagsToUri(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val data = json.encodeToString(TagConfig.serializer(), TagConfig(tags = tagsRepo.value.tags))
            out.write(data.toByteArray())
        } ?: throw IOException("无法写入文件")
    }

    override suspend fun exportPeriodsToUri(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val data = json.encodeToString(PeriodConfig.serializer(), PeriodConfig(periods = periodsRepo.value.periods))
            out.write(data.toByteArray())
        } ?: throw IOException("无法写入文件")
    }

    override suspend fun exportAllToUri(uri: Uri, tasks: List<Task>, completedTasks: List<Task>) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val data = json.encodeToString(
                MergedFullConfig.serializer(),
                MergedFullConfig(
                    tags = tagsRepo.value.tags,
                    periods = periodsRepo.value.periods,
                    weeklyPeriods = weeklyPeriodsRepo.value.periods,
                    datePeriods = datePeriodsRepo.value.periods,
                    deadlinePeriods = deadlinePeriodsRepo.value.periods,
                    tasks = tasks,
                    completedTasks = completedTasks,
                ),
            )
            out.write(data.toByteArray())
        } ?: throw IOException("无法写入文件")
    }

    // ── Import ────────────────────────────────────────────────────

    override suspend fun importTagsFromUri(uri: Uri): List<Tag> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IOException("无法读取文件")
        val config = json.decodeFromString<TagConfig>(text)
        saveTags(config.tags)
        config.tags
    }

    override suspend fun importPeriodsFromUri(uri: Uri): List<TimePeriod> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IOException("无法读取文件")
        val config = json.decodeFromString<PeriodConfig>(text)
        savePeriods(config.periods)
        config.periods
    }

    override suspend fun importFromUri(uri: Uri): Pair<List<Tag>, List<TimePeriod>> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IOException("无法读取文件")
        try {
            @kotlinx.serialization.Serializable
            data class MergedConfig(
                val tags: List<Tag> = emptyList(),
                val periods: List<TimePeriod> = emptyList(),
                val version: Int = 1,
            )
            val merged = json.decodeFromString<MergedConfig>(text)
            saveTags(merged.tags)
            savePeriods(merged.periods)
            merged.tags to merged.periods
        } catch (_: Exception) {
            try {
                val tags = importTagsFromUri(uri)
                tags to periodsRepo.value.periods
            } catch (_: Exception) {
                val periods = importPeriodsFromUri(uri)
                tagsRepo.value.tags to periods
            }
        }
    }

    override suspend fun importAllFromUri(uri: Uri): FullImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IOException("无法读取文件")
        try {
            val merged = json.decodeFromString<MergedFullConfig>(text)
            saveTags(merged.tags)
            savePeriods(merged.periods)
            saveWeeklyPeriods(merged.weeklyPeriods)
            saveDatePeriods(merged.datePeriods)
            saveDeadlinePeriods(merged.deadlinePeriods)
            FullImportResult(
                tags = merged.tags,
                periods = merged.periods,
                weeklyPeriods = merged.weeklyPeriods,
                datePeriods = merged.datePeriods,
                deadlinePeriods = merged.deadlinePeriods,
                tasks = merged.tasks,
                completedTasks = merged.completedTasks,
            )
        } catch (_: Exception) {
            val (tags, periods) = importFromUri(uri)
            FullImportResult(
                tags, periods,
                weeklyPeriodsRepo.value.periods,
                datePeriodsRepo.value.periods,
                deadlinePeriodsRepo.value.periods,
            )
        }
    }
}
