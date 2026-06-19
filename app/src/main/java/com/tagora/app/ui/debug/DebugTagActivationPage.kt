package com.tagora.app.ui.debug

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.theme.DashboardColors
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.task.conditionSummary
import kotlinx.coroutines.flow.first

/**
 * 调试页面：手动激活/关闭标签
 * 仅在 debug 编译中可用，用于在没有时间激活系统的情况下测试标签条件逻辑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTagActivationPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = RepositoryProvider.get(context)
    val taskRepo = RepositoryProvider.getTaskRepo(context)

    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var allTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var activeTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        allTags = repository.tagsFlow.first()
        allTasks = taskRepo.tasksFlow.first()
    }

    val tagsMap = remember(allTags) { allTags.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试: 标签激活") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标签开关区域
            item {
                CardGroup(title = { Text("标签激活状态") }) {
                    Column {
                        if (allTags.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无标签",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            allTags.forEachIndexed { index, tag ->
                                val isActive = tag.id in activeTagIds
                                val tagColor = try {
                                    Color(AndroidColor.parseColor(tag.color))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 颜色圆点
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .padding(0.dp),
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(color = tagColor)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }

                                    Text(
                                        text = if (isActive) "激活" else "关闭",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )

                                    Spacer(Modifier.width(8.dp))

                                    Switch(
                                        checked = isActive,
                                        onCheckedChange = { checked ->
                                            activeTagIds = if (checked) {
                                                activeTagIds + tag.id
                                            } else {
                                                activeTagIds - tag.id
                                            }
                                        },
                                    )
                                }

                                if (index < allTags.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 任务激活状态区域
            item {
                CardGroup(title = { Text("任务激活状态（${activeTagIds.size} 个标签激活）") }) {
                    if (allTasks.isEmpty()) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无任务",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        allTasks.forEachIndexed { index, task ->
                            val isActive = task.condition.evaluate(activeTagIds)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = task.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isActive) "✓ 激活" else "✗ 未激活",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) {
                                                DashboardColors.ActiveGreen
                                            } else {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = conditionSummary(task.condition, tagsMap),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }

                            if (index < allTasks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
