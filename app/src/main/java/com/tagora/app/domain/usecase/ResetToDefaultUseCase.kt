package com.tagora.app.domain.usecase

import android.content.Context
import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository

/**
 * 原子化重置所有配置为默认值。
 *
 * 支持仅重置时间段/标签，或同时重置任务。
 * 具体加载哪个预设的默认值由 TimePeriodRepository 内部通过 AppPreferences 决定。
 *
 * **重要**：重置期间会暂停 [TagActivationEngine]，避免各 repo 逐个 save 时
 * 触发引擎在数据不一致的中间态重算，导致任务被误判为"永远无法满足"。
 * 全部保存完成后再恢复引擎运行。
 */
class ResetToDefaultUseCase(
    private val repository: TimePeriodRepository,
    private val taskRepo: TaskRepository? = null,
    private val completedTaskRepo: CompletedTaskRepository? = null,
    private val context: Context,
) {
    /**
     * 执行重置操作。
     *
     * @param includeTasks 是否同时重置任务配置
     */
    suspend fun execute(includeTasks: Boolean = false) {
        // 暂停引擎，避免中间态触发误判
        val engine = RepositoryProvider.getActivationEngine(context)
        val wasRunning = engine.isRunning
        if (wasRunning) engine.stop()

        try {
            // 先加载所有默认数据
            val tags = repository.loadDefaultTags()
            val periods = repository.loadDefaultPeriods()
            val weeklyPeriods = repository.loadDefaultWeeklyPeriods()
            val datePeriods = repository.loadDefaultDatePeriods()
            val deadlinePeriods = repository.loadDefaultDeadlinePeriods()

            if (includeTasks && taskRepo != null) {
                val tasks = taskRepo.loadDefaultTasks()
                taskRepo.saveTasks(tasks)

                if (completedTaskRepo != null) {
                    val completed = completedTaskRepo.loadDefaultCompletedTasks()
                    completedTaskRepo.saveCompletedTasks(completed)
                }
            }

            // 保存 tags 和 periods（引擎已暂停，顺序安全）
            repository.saveTags(tags)
            repository.savePeriods(periods)
            repository.saveWeeklyPeriods(weeklyPeriods)
            repository.saveDatePeriods(datePeriods)
            repository.saveDeadlinePeriods(deadlinePeriods)
        } finally {
            // 确保引擎恢复，即使重置过程中抛异常也不会静默停止
            if (wasRunning) engine.start()
        }
    }
}
