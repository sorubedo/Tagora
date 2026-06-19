package com.tagora.app.domain.engine

import com.tagora.app.data.model.Task

/**
 * 任务状态变化事件
 *
 * 由 [TagActivationEngine] 在检测到任务状态变化时发射，
 * 供 [TagActivationForegroundService] 等消费者订阅并发送通知。
 */
sealed class TaskEvent {
    /** 任务标签条件被满足，任务已激活 */
    data class Activated(val task: Task) : TaskEvent()

    /** normal 类型任务永远无法满足，已超时 */
    data class TimedOut(val task: Task) : TaskEvent()

    /** fixed 类型任务自动完成 */
    data class Completed(val task: Task) : TaskEvent()
}
