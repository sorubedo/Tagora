package com.tagora.app.data.model

/**
 * 任务状态常量（替代魔法字符串 "incomplete" / "completed" / "timeout"）。
 * 不改 JSON 格式，只提供类型安全的扩展属性。
 */
object TaskStatus {
    const val INCOMPLETE = "incomplete"
    const val COMPLETED = "completed"
    const val TIMEOUT = "timeout"
}

val Task.isIncomplete: Boolean get() = status == TaskStatus.INCOMPLETE
val Task.isCompleted: Boolean get() = status == TaskStatus.COMPLETED
val Task.isTimedOut: Boolean get() = status == TaskStatus.TIMEOUT
