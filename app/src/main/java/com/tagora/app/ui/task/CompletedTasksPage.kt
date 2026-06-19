package com.tagora.app.ui.task

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.isTimedOut
import com.tagora.app.theme.DashboardColors
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.ui.components.ConfirmDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTasksPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val completedRepo = RepositoryProvider.getCompletedTaskRepo(context)
    val repository = RepositoryProvider.get(context)

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }

    LaunchedEffect(Unit) {
        allTags = repository.tagsFlow.first()
    }

    val tagsMap = remember(allTags) { allTags.associateBy { it.id } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var showClearDialog by remember { mutableStateOf(false) }

    // 实时监听已完成任务变化
    LaunchedEffect(Unit) {
        completedRepo.completedTasksFlow.collect { tasks = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已完成任务") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
                actions = {
                    if (tasks.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "暂无已完成或超时任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    CardGroup(
                        title = {
                            Text(
                                text = "已完成 / 超时 (${tasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    ) {
                        tasks.sortedByDescending { it.completedAt }.forEachIndexed { index, task ->
                            val isTimeout = task.isTimedOut
                            CardGroupItem(isLast = index == tasks.lastIndex) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isTimeout) "⏰" else "✅",
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
                                            Text(
                                                text = task.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = if (isTimeout) "超时" else "已完成",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isTimeout) DashboardColors.DeadlinePink else DashboardColors.ActiveGreen,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            task.completedAt?.let { ts ->
                                                Text(
                                                    text = Instant.ofEpochMilli(ts)
                                                        .atZone(ZoneId.systemDefault())
                                                        .toLocalDateTime()
                                                        .format(dateFormatter),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            val updated = tasks.filter { it.id != task.id }
                                            completedRepo.saveCompletedTasks(updated)
                                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // 清空确认对话框
        if (showClearDialog) {
            ConfirmDialog(
                title = "清空全部已完成任务？",
                text = "将删除所有已完成和超时的任务记录，此操作不可撤销。",
                confirmText = "确认清空",
                confirmColor = MaterialTheme.colorScheme.error,
                onConfirm = {
                    scope.launch {
                        completedRepo.saveCompletedTasks(emptyList())
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showClearDialog = false },
            )
        }
    }
}
