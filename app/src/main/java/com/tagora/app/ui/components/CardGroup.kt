package com.tagora.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.theme.TagoraTheme

/**
 * RikkaHub 风格 CardGroup 组件（简化版）
 * 参照 RikkaHub CardGroup.kt
 *
 * 将一个分组的内容包裹在圆角 Card 中，子项之间用分隔线隔开
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (title != null) {
            title()
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(content = content)
        }
    }
}

/**
 * CardGroup 中的单项（带分隔线）
 */
@Composable
fun CardGroupItem(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isLast: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(0.dp),
            ) {
                content()
            }
        } else {
            content()
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardGroupPreview() {
    TagoraTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CardGroup(
                title = { Text("示例分组") },
            ) {
                CardGroupItem(isLast = false) {
                    Text(
                        "第一项",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                CardGroupItem(isLast = true) {
                    Text(
                        "最后一项",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
