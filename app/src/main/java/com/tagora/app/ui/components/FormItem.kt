package com.tagora.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.theme.TagoraTheme

/**
 * RikkaHub 风格 FormItem 组件
 * 参照 RikkaHub Form.kt:22-58
 *
 * 插槽说明：
 * - label: 左侧标签（必填，titleMedium 字体）
 * - description: 标签下方的描述文本（可选，labelSmall + 60% alpha）
 * - content: description 下方的额外内容（可选）
 * - tail: 右侧尾部内容（可选，如 Switch）
 */
@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    label()
                }
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        ),
                    ) {
                        description()
                    }
                }
                content()
            }
            if (tail != null) {
                Spacer(Modifier.width(8.dp))
                tail()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FormItemPreview() {
    TagoraTheme {
        Column(modifier = Modifier.width(360.dp)) {
            FormItem(
                label = { Text("标签示例") },
                description = { Text("这是一段描述文字，用来解释这个设置项的作用") },
                modifier = Modifier.height(80.dp),
            )
        }
    }
}
