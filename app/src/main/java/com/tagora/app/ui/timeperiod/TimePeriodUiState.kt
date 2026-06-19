package com.tagora.app.ui.timeperiod

import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod

sealed interface TimePeriodUiState {
    data object Loading : TimePeriodUiState
    data class Error(val throwable: Throwable) : TimePeriodUiState
    data class Success(
        val tags: List<Tag>,
        val periodsSorted: List<TimePeriod>,
        val weeklyPeriodsSorted: List<TimePeriod>,
        val datePeriodsSorted: List<TimePeriod>,
        val deadlinePeriodsSorted: List<TimePeriod>,
        val tagsMap: Map<String, Tag>,
        val activeTagIds: Set<String> = emptySet(),
        val tasks: List<Task> = emptyList(),
    ) : TimePeriodUiState
}
