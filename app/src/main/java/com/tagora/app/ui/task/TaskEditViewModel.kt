package com.tagora.app.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tagora.app.data.model.TaskCondition

/**
 * 跨 TaskDetailPage / TaskConditionPage 共享的状态。
 * 使用 object 单例，因为 Navigation3 的 entry 各自隔离 ViewModelStore，
 * viewModel() 在两个页面拿不到同一实例。
 */
object TaskEditState {
    /** 条件编辑页传回的条件，null 表示无待接收的条件 */
    var pendingCondition by mutableStateOf<TaskCondition?>(null)
        private set

    fun putCondition(condition: TaskCondition) {
        pendingCondition = condition
    }

    fun takeCondition(): TaskCondition? {
        val c = pendingCondition
        pendingCondition = null
        return c
    }

    /** 取消编辑时清理悬挂的条件（用户不点"确定"直接返回时调用） */
    fun clearCondition() {
        pendingCondition = null
    }
}
