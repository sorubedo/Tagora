package com.tagora.app.data.model

/**
 * 时间段类型枚举。
 * JSON 仍使用原始字符串值（"daily"/"weekly"/"date"/"deadline"），
 * 通过扩展属性 [TimePeriod.typeEnum] 获取类型安全的值。
 */
enum class PeriodType(val jsonValue: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    DATE("date"),
    DEADLINE("deadline");

    companion object {
        fun from(value: String): PeriodType =
            entries.first { it.jsonValue == value }
    }
}

/** 类型安全的 PeriodType 扩展 */
val TimePeriod.typeEnum: PeriodType get() = PeriodType.from(type)

val TimePeriod.isDaily: Boolean get() = type == PeriodType.DAILY.jsonValue
val TimePeriod.isWeekly: Boolean get() = type == PeriodType.WEEKLY.jsonValue
val TimePeriod.isDate: Boolean get() = type == PeriodType.DATE.jsonValue
val TimePeriod.isDeadline: Boolean get() = type == PeriodType.DEADLINE.jsonValue
