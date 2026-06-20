package com.tagora.app.domain.usecase

import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository

/**
 * 原子化重置所有配置为默认值。
 *
 * 支持仅重置时间段/标签，或同时重置任务。
 * 具体加载哪个预设的默认值由 TimePeriodRepository 内部通过 AppPreferences 决定。
 *
 * **重要**：必须先保存 tasks，再保存 tags/periods。
 * 否则 TagActivationEngine 会在 tags/periods 已更新但 tasks 未更新时触发重算，
 * 将旧任务（引用了已不存在的标签）误判为"永远无法满足"而移入已完成列表。
 */
class ResetToDefaultUseCase(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository? = null,
    private val completedTaskRepo: CompletedTaskRepository? = null,
) {
    /**
     * 执行重置操作。
     *
     * @param includeTasks 是否同时重置任务配置
     */
    suspend fun execute(includeTasks: Boolean = false) {
        // 先加载所有默认数据
        val tags = repository.loadDefaultTags()
        val periods = repository.loadDefaultPeriods()
        val weeklyPeriods = repository.loadDefaultWeeklyPeriods()
        val datePeriods = repository.loadDefaultDatePeriods()
        val deadlinePeriods = repository.loadDefaultDeadlinePeriods()

        if (includeTasks && taskRepo != null) {
            // 关键：先保存任务（清空为用户默认），再保存 tags/periods
            // 这样引擎在 tags/periods 变更触发重算时，tasks 已经是默认值，不会误判旧任务
            val tasks = taskRepo.loadDefaultTasks()
            taskRepo.saveTasks(tasks)

            // 同时重置已完成任务列表
            if (completedTaskRepo != null) {
                val completed = completedTaskRepo.loadDefaultCompletedTasks()
                completedTaskRepo.saveCompletedTasks(completed)
            }
        }

        // 最后保存 tags 和 periods（此时 tasks 已更新，引擎不会误判）
        repository.saveTags(tags)
        repository.savePeriods(periods)
        repository.saveWeeklyPeriods(weeklyPeriods)
        repository.saveDatePeriods(datePeriods)
        repository.saveDeadlinePeriods(deadlinePeriods)
    }
}
