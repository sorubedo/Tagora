package com.tagora.app.data

import android.net.Uri
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.TimePeriod
import kotlinx.coroutines.flow.Flow

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

    suspend fun exportAllToUri(uri: Uri)
    suspend fun importAllFromUri(uri: Uri)
}
