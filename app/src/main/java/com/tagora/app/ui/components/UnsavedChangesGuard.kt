package com.tagora.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 未保存变更守卫 Hook。
 *
 * 统一了 TaskDetailPage 和 TimePeriodDetailPage 中重复的
 * "BackHandler 拦截 + 确认弹窗" 模式。
 *
 * @param hasChanges 是否有未保存的变更
 * @param title 对话框标题
 * @param message 对话框提示文字
 * @param confirmText 确认按钮文字
 * @param onDiscard 用户确认丢弃变更时的回调
 * @return 手动触发对话框的函数（用于导航栏返回按钮等）
 */
@Composable
fun rememberUnsavedChangesGuard(
    hasChanges: Boolean,
    title: String = "未保存的更改",
    message: String = "有未保存的修改，确定要返回吗？",
    confirmText: String = "放弃",
    onDiscard: () -> Unit,
): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = hasChanges) {
        showDialog = true
    }

    if (showDialog) {
        ConfirmDialog(
            title = title,
            text = message,
            confirmText = confirmText,
            onConfirm = {
                showDialog = false
                onDiscard()
            },
            onDismiss = { showDialog = false },
        )
    }

    return { showDialog = true }
}
