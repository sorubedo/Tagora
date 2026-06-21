package com.tagora.app.data.model

import kotlinx.serialization.Serializable

/**
 * 应用全局配置模型。
 * 包含所有时间段（四种类型）、标签、任务和已完成任务。
 * 序列化为单个 JSON 文件，作为应用数据的唯一持久化格式。
 */
@Serializable
data class AppConfig(
    val version: Int = 5,
    val tags: List<Tag> = emptyList(),
    val periods: List<TimePeriod> = emptyList(),
    val weeklyPeriods: List<TimePeriod> = emptyList(),
    val datePeriods: List<TimePeriod> = emptyList(),
    val deadlinePeriods: List<TimePeriod> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
)
