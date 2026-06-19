package com.tagora.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 带返回按钮和未保存变更保护的通用 TopAppBar。
 *
 * 替换项目中 ~10 处重复的：
 *   TextButton(onClick = { handleBack() }) { Text("← 返回") }
 *   + rememberUnsavedChangesGuard
 *
 * @param title 标题文本
 * @param onBack 返回回调（已处理未保存保护）
 * @param hasUnsavedChanges 是否有未保存的变更
 * @param unsavedTitle 未保存弹窗标题
 * @param unsavedMessage 未保存弹窗消息
 * @param actions 右侧操作按钮（可选）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarWithBack(
    title: String,
    onBack: () -> Unit,
    hasUnsavedChanges: Boolean = false,
    unsavedTitle: String = "放弃更改？",
    unsavedMessage: String = "有未保存的修改，确定要返回吗？",
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 未保存变更保护
    val triggerGuard = rememberUnsavedChangesGuard(
        hasChanges = hasUnsavedChanges,
        title = unsavedTitle,
        message = unsavedMessage,
        onDiscard = onBack,
    )
    var showDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(onClick = {
                if (hasUnsavedChanges) triggerGuard() else onBack()
            }) {
                Text("← 返回")
            }
        },
        actions = actions,
    )
}
