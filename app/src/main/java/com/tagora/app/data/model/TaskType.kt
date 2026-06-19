package com.tagora.app.data.model

/**
 * 任务类型常量（替代魔法字符串 "normal" / "fixed"）。
 * 不改 JSON 格式，只提供类型安全的扩展属性。
 */
object TaskType {
    const val NORMAL = "normal"
    const val FIXED = "fixed"
}

val Task.isNormal: Boolean get() = type == TaskType.NORMAL
val Task.isFixed: Boolean get() = type == TaskType.FIXED
