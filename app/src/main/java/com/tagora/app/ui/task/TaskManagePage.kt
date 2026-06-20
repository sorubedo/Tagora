package com.tagora.app.ui.task

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.theme.DashboardColors
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.descriptionWithoutLocation
import com.tagora.app.data.model.isNormal
import com.tagora.app.data.model.locationText
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import kotlinx.coroutines.flow.first

/**
 * 任务列表内容（不含 Scaffold），可在 MainScreen 中直接嵌入
 */
@Composable
fun TaskListContent(
    tasks: List<Task>,
    allTags: List<Tag>,
    onTaskClick: (String) -> Unit,
    onTaskComplete: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeTagIds: Set<String> = emptySet(),
) {
    val tagsMap = remember(allTags) { allTags.associateBy { it.id } }

    if (tasks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无任务，点击右下角 + 添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup {
                    tasks.forEachIndexed { index, task ->
                        val isActive = task.condition.evaluate(activeTagIds)
                        CardGroupItem(
                            onClick = { onTaskClick(task.id) },
                            isLast = index == tasks.lastIndex,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 激活状态指示器
                                Text(
                                    text = if (isActive) "●" else "○",
                                    color = if (isActive) DashboardColors.ActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (task.description.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        val cleanDesc = task.descriptionWithoutLocation
                                        if (cleanDesc.isNotBlank()) {
                                            Text(
                                                text = cleanDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        task.locationText?.let { location ->
                                            Text(
                                                text = location,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = conditionSummary(task.condition, tagsMap),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                // 普通事件 + 当前激活 → 显示完成按钮
                                if (task.isNormal && isActive && onTaskComplete != null) {
                                    Surface(
                                        onClick = { onTaskComplete(task.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = DashboardColors.ActiveGreen.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "完成",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = DashboardColors.ActiveGreen,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 独立任务管理页面（从设置页进入时使用，带 Scaffold 和返回按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagePage(
    onBack: () -> Unit,
    onTaskEdit: (String?) -> Unit,
    onConditionEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val taskRepo = RepositoryProvider.getTaskRepo(context)
    val repository = RepositoryProvider.get(context)
    val engine = RepositoryProvider.getActivationEngine(context)

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var activeTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        tasks = taskRepo.tasksFlow.first()
        allTags = repository.tagsFlow.first()
    }

    LaunchedEffect(Unit) {
        engine.activeTagIds.collect { ids ->
            activeTagIds = ids
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务管理") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onTaskEdit(null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建任务")
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        TaskListContent(
            tasks = tasks,
            allTags = allTags,
            activeTagIds = activeTagIds,
            onTaskClick = { onTaskEdit(it) },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
