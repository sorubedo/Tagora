package com.tagora.app.domain.usecase

import com.tagora.app.data.TimePeriodRepository
import kotlinx.coroutines.flow.first

/**
 * 删除标签并清理所有时间段类型中对该标签的引用。
 */
class DeleteTagUseCase(
    private val repository: TimePeriodRepository,
) {
    suspend fun execute(tagId: String) {
        val tags = repository.tagsFlow.first()
        val periods = repository.periodsFlow.first()
        val weeklyPeriods = repository.weeklyPeriodsFlow.first()
        val datePeriods = repository.datePeriodsFlow.first()
        val deadlinePeriods = repository.deadlinePeriodsFlow.first()

        repository.saveTags(tags.filter { it.id != tagId })
        repository.savePeriods(periods.map { p ->
            p.copy(tagIds = p.tagIds.filter { it != tagId })
        })
        repository.saveWeeklyPeriods(weeklyPeriods.map { p ->
            p.copy(tagIds = p.tagIds.filter { it != tagId })
        })
        repository.saveDatePeriods(datePeriods.map { p ->
            p.copy(tagIds = p.tagIds.filter { it != tagId })
        })
        repository.saveDeadlinePeriods(deadlinePeriods.map { p ->
            p.copy(tagIds = p.tagIds.filter { it != tagId })
        })
    }
}
