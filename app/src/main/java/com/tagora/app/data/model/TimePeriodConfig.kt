package com.tagora.app.data.model

import kotlinx.serialization.Serializable

/** 匹配 "t-class-N" 格式的标签（N 为正整数） */
val CLASS_TAG_REGEX = Regex("^t-class-(\\d+)$")
/** 匹配 "t-w-N" 格式的标签（N 为正整数） */
val WEEK_TAG_REGEX = Regex("^t-w(\\d+)$")

@Serializable
data class TagConfig(
    val version: Int = 1,
    val tags: List<Tag> = emptyList(),
)

@Serializable
data class PeriodConfig(
    val version: Int = 1,
    val periods: List<TimePeriod> = emptyList(),
)

@Serializable
data class TimePeriod(
    val id: String,
    val name: String,
    val type: String = "daily",       // "daily"、"weekly"、"date" 或 "deadline"
    val startMinute: Int = 0,         // 仅 daily 有效
    val endMinute: Int = 0,           // 仅 daily 有效
    val color: String,
    val tagIds: List<String> = emptyList(),
    val dayOfWeeks: List<Int> = emptyList(), // 仅 weekly 有效: [1]=周一, [1,2,3,4,5]=工作日
    val startDate: String? = null,     // 仅 type="date" 有效, ISO "yyyy-MM-dd"
    val endDate: String? = null,       // type="date" 和 "deadline" 有效, ISO "yyyy-MM-dd"
)

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: String,
)
