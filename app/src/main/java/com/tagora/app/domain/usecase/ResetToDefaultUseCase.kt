package com.tagora.app.domain.usecase

import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository

/**
 * 原子化重置所有配置为默认值。
 *
 * 支持仅重置时间段/标签，或同时重置任务。
 * 具体加载哪个预设的默认值由 TimePeriodRepository 内部通过 AppPreferences 决定。
 */
class ResetToDefaultUseCase(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository? = null,
) {
    /**
     * 执行重置操作。
     *
     * @param includeTasks 是否同时重置任务配置
     */
    suspend fun execute(includeTasks: Boolean = false) {
        val tags = repository.loadDefaultTags()
        val periods = repository.loadDefaultPeriods()
        val weeklyPeriods = repository.loadDefaultWeeklyPeriods()
        val datePeriods = repository.loadDefaultDatePeriods()
        val deadlinePeriods = repository.loadDefaultDeadlinePeriods()
        repository.saveTags(tags)
        repository.savePeriods(periods)
        repository.saveWeeklyPeriods(weeklyPeriods)
        repository.saveDatePeriods(datePeriods)
        repository.saveDeadlinePeriods(deadlinePeriods)
        if (includeTasks && taskRepo != null) {
            val tasks = taskRepo.loadDefaultTasks()
            taskRepo.saveTasks(tasks)
        }
    }
}
