package com.tagora.app.data.model

import kotlinx.serialization.Serializable

/**
 * WebDAV 备份/恢复使用的全量配置合并模型。
 * 包含所有时间段（四种类型）、标签、任务和已完成任务。
 */
@Serializable
data class MergedFullConfig(
    val version: Int = 5,
    val tags: List<Tag> = emptyList(),
    val periods: List<TimePeriod> = emptyList(),
    val weeklyPeriods: List<TimePeriod> = emptyList(),
    val datePeriods: List<TimePeriod> = emptyList(),
    val deadlinePeriods: List<TimePeriod> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
)
