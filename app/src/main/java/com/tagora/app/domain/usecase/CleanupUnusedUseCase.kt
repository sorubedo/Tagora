package com.tagora.app.domain.usecase

import android.content.Context
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.AssetPathResolver
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.model.PeriodConfig
import com.tagora.app.data.model.TagConfig
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.collectTagIds
import com.tagora.app.data.model.isIncomplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 清理无用标签和空标签时间段。
 *
 * 以当前选中预设的默认配置为白名单保护基线，从活跃任务出发做迭代收敛，
 * 找出真正被需要的 tag/period 连通分量，其余用户创建的孤立项全部清理。
 *
 * - 无用标签：不属于「有用连通分量」且不在默认配置中的标签
 * - 无用时间段：tagIds 全部被清空的时间段（清理了无用 tag 后的连带结果）
 */
class CleanupUnusedUseCase(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository,
    private val context: Context,
    private val prefs: AppPreferences? = null,
) {
    data class Result(
        val removedTagNames: List<String>,
        val removedPeriodNames: List<String>,
    ) {
        val removedTagCount get() = removedTagNames.size
        val removedPeriodCount get() = removedPeriodNames.size
        val isEmpty get() = removedTagCount == 0 && removedPeriodCount == 0
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 从当前预设的 assets 读取默认标签 ID 集合（仅读取，不写入） */
    private fun loadDefaultTagIds(): Set<String> = runCatching {
        val assetName = AssetPathResolver.resolve(prefs?.selectedPreset, "tags.json")
        val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
        json.decodeFromString<TagConfig>(text).tags.map { it.id }.toSet()
    }.getOrDefault(emptySet())

    private fun loadDefaultPeriodIds(): Set<String> {
        val ids = mutableSetOf<String>()
        val fileNames = listOf("periods.json", "weekly_periods.json", "date_periods.json", "deadline_periods.json")
        for (name in fileNames) {
            val assetName = AssetPathResolver.resolve(prefs?.selectedPreset, name)
            runCatching {
                val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
                json.decodeFromString<PeriodConfig>(text).periods.mapTo(ids) { it.id }
            }
        }
        return ids
    }

    suspend fun execute(): Result = withContext(Dispatchers.IO) {
        val defaultTagIds = loadDefaultTagIds()
        val defaultPeriodIds = loadDefaultPeriodIds()

        val tags = repository.tagsFlow.first()
        val dailyPeriods = repository.periodsFlow.first()
        val weeklyPeriods = repository.weeklyPeriodsFlow.first()
        val datePeriods = repository.datePeriodsFlow.first()
        val deadlinePeriods = repository.deadlinePeriodsFlow.first()
        val tasks = taskRepo.tasksFlow.first()

        val incompleteTasks = tasks.filter { it.isIncomplete }

        // 活跃任务引用的 tag → 根有用集合
        val rootUsefulTagIds = incompleteTasks.flatMap { it.condition.collectTagIds() }.toSet()

        // 迭代收敛：从根有用 tag 出发，沿着 period 引用关系扩展
        var usefulTagIds = rootUsefulTagIds
        if (usefulTagIds.isNotEmpty()) {
            val allPeriods = dailyPeriods + weeklyPeriods + datePeriods + deadlinePeriods
            var changed = true
            while (changed) {
                val usefulPeriods = allPeriods.filter { p -> p.tagIds.any { it in usefulTagIds } }
                val expanded = usefulTagIds + usefulPeriods.flatMap { it.tagIds }
                changed = expanded != usefulTagIds
                usefulTagIds = expanded
            }
        }

        // 受保护集合 = 迭代收敛的有用 tag + 默认配置 tag（白名单）
        val protectedTagIds = usefulTagIds + defaultTagIds

        // 孤立 tag：不在受保护集合中
        val orphanTags = tags.filter { it.id !in protectedTagIds }
        val orphanTagIdSet = orphanTags.map { it.id }.toSet()

        if (orphanTags.isEmpty()) {
            return@withContext Result(emptyList(), emptyList())
        }

        // 从各类型 period 中移除孤立 tagId
        fun cleanPeriods(periods: List<TimePeriod>): List<TimePeriod> =
            periods.map { p -> p.copy(tagIds = p.tagIds.filter { it !in orphanTagIdSet }) }
                .filter { it.tagIds.isNotEmpty() }

        val cleanedDaily = cleanPeriods(dailyPeriods)
        val cleanedWeekly = cleanPeriods(weeklyPeriods)
        val cleanedDate = cleanPeriods(datePeriods)
        val cleanedDeadline = cleanPeriods(deadlinePeriods)

        // 收集被删除的 period 名称
        val removedPeriodNames = mutableListOf<String>()
        if (cleanedDaily.size != dailyPeriods.size)
            removedPeriodNames += dailyPeriods.filter { p -> p.id !in cleanedDaily.map { it.id } }.map { it.name }
        if (cleanedWeekly.size != weeklyPeriods.size)
            removedPeriodNames += weeklyPeriods.filter { p -> p.id !in cleanedWeekly.map { it.id } }.map { it.name }
        if (cleanedDate.size != datePeriods.size)
            removedPeriodNames += datePeriods.filter { p -> p.id !in cleanedDate.map { it.id } }.map { it.name }
        if (cleanedDeadline.size != deadlinePeriods.size)
            removedPeriodNames += deadlinePeriods.filter { p -> p.id !in cleanedDeadline.map { it.id } }.map { it.name }

        // 保存
        if (orphanTags.isNotEmpty()) {
            repository.saveTags(tags.filter { it.id in protectedTagIds })
        }
        if (cleanedDaily.size != dailyPeriods.size) repository.savePeriods(cleanedDaily)
        if (cleanedWeekly.size != weeklyPeriods.size) repository.saveWeeklyPeriods(cleanedWeekly)
        if (cleanedDate.size != datePeriods.size) repository.saveDatePeriods(cleanedDate)
        if (cleanedDeadline.size != deadlinePeriods.size) repository.saveDeadlinePeriods(cleanedDeadline)

        Result(
            removedTagNames = orphanTags.map { it.name },
            removedPeriodNames = removedPeriodNames,
        )
    }
}
