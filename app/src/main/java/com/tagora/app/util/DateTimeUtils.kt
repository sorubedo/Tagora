package com.tagora.app.util

/**
 * 日期时间相关工具函数
 */

/** 周一~周日 的本地化标签（0-indexed 用于外部索引，如 FlowRow 中 forEachIndexed） */
val dayOfWeekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 1-indexed 的星期标签，索引 0 为空字符串（对齐 java.time.DayOfWeek.getValue() = 1..7） */
val dayOfWeekDisplayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 将 DayOfWeek 数值列表（1=周一..7=周日）转为中文显示文本。连续区间用 ~，不连续用 、分隔。 */
fun dayOfWeeksText(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.sorted()
    val labels = dayOfWeekLabels
    val isConsecutive = sorted.size > 1 && sorted.last() - sorted.first() == sorted.size - 1
    return if (isConsecutive) {
        "${labels[sorted.first() - 1]}~${labels[sorted.last() - 1]}"
    } else {
        sorted.joinToString("、") { labels[it - 1] }
    }
}

/** 将分钟数转为 "HH:mm" 格式 */
fun minutesToTimeString(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
