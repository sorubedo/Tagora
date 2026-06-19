package com.tagora.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.theme.TagoraTheme

/**
 * 应用侧边抽屉内容
 * 所有页面（除控制面板外）均为独立 NavKey 页面，通过导航跳转
 */
@Composable
fun AppDrawerContent(
    onPageSelect: () -> Unit,
    onTimelineClick: () -> Unit,
    onTimePeriodListClick: () -> Unit,
    onTaskListClick: () -> Unit,
    onTagManageClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCompletedTasksClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 品牌头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "看板",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "管理你的一天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // 控制面板
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.Filled.SpaceDashboard, contentDescription = null)
                },
                label = {
                    Text("控制面板", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onPageSelect,
                modifier = Modifier.height(56.dp),
            )

            // 时间线
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.Filled.Timeline, contentDescription = null)
                },
                label = {
                    Text("时间线", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onTimelineClick,
                modifier = Modifier.height(56.dp),
            )

            // 时间段
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
                },
                label = {
                    Text("时间段", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onTimePeriodListClick,
                modifier = Modifier.height(56.dp),
            )

            // 任务
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.Filled.TaskAlt, contentDescription = null)
                },
                label = {
                    Text("任务", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onTaskListClick,
                modifier = Modifier.height(56.dp),
            )

            // 标签管理
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                },
                label = {
                    Text("标签管理", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onTagManageClick,
                modifier = Modifier.height(56.dp),
            )

            // 已完成任务
            NavigationDrawerItem(
                icon = {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                },
                label = {
                    Text("已完成任务", style = MaterialTheme.typography.bodyLarge)
                },
                selected = false,
                onClick = onCompletedTasksClick,
                modifier = Modifier.height(56.dp),
            )

            Spacer(Modifier.weight(1f))

            // 底部分隔
            HorizontalDivider()

            // 设置按钮（右下对齐）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    onClick = onSettingsClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppDrawerContentPreview() {
    TagoraTheme {
        AppDrawerContent(
            onPageSelect = {},
            onTimelineClick = {},
            onTimePeriodListClick = {},
            onTaskListClick = {},
            onTagManageClick = {},
            onSettingsClick = {},
            onCompletedTasksClick = {},
        )
    }
}
