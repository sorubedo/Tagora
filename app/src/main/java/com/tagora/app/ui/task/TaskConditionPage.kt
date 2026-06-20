package com.tagora.app.ui.task

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.AndCondition
import com.tagora.app.data.model.MultiTagCondition
import com.tagora.app.data.model.OrCondition
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── 条件类型枚举 ──────────────────────────────────────────────

private enum class ConditionType(val label: String) {
    MULTI("多标签(任一)"),
    AND("全部满足 (AND)"),
    OR("任一满足 (OR)"),
}

private fun conditionTypeOf(c: TaskCondition): ConditionType = when (c) {
    is MultiTagCondition -> ConditionType.MULTI
    is AndCondition -> ConditionType.AND
    is OrCondition -> ConditionType.OR
}

/** 按类型创建默认空条件 */
private fun emptyConditionOf(type: ConditionType): TaskCondition = when (type) {
    ConditionType.MULTI -> MultiTagCondition(emptyList())
    ConditionType.AND -> AndCondition(listOf())
    ConditionType.OR -> OrCondition(listOf())
}

// ── 页面主体 ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskConditionPage(
    taskId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = RepositoryProvider.get(context)
    val taskRepo = RepositoryProvider.getTaskRepo(context)

    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var task by remember { mutableStateOf<Task?>(null) }
    var condition by remember { mutableStateOf<TaskCondition>(AndCondition(emptyList())) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allTags = repository.tagsFlow.first()
        val tasks = taskRepo.tasksFlow.first()
        task = tasks.find { it.id == taskId }
        condition = task?.condition ?: AndCondition(emptyList())
        loaded = true
    }

    val tagsMap = remember(allTags) { allTags.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签条件") },
                navigationIcon = {
                    TextButton(onClick = {
                        TaskEditState.clearCondition()
                        onBack()
                    }) { Text("← 返回") }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = task?.name ?: "任务",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 递归渲染条件树（根节点无删除按钮）
            ConditionNodeEditor(
                condition = condition,
                allTags = allTags,
                tagsMap = tagsMap,
                onConditionChange = { condition = it },
                onDelete = null,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    // 通过单例传回条件到 TaskDetailPage，不在此处持久化
                    // 最终由 TaskDetailPage 的「保存」按钮统一写入仓库
                    TaskEditState.putCondition(condition)
                    Toast.makeText(context, "条件已更新", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确定")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 通用：Mini 类型切换条 ─────────────────────────────────────

@Composable
private fun NodeTypeBar(
    currentType: ConditionType,
    onTypeChange: (ConditionType) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型标签 + 下拉
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = currentType.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ConditionType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            onTypeChange(type)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 删除按钮（仅非根节点显示）
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── 递归节点编辑器 ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionNodeEditor(
    condition: TaskCondition,
    allTags: List<Tag>,
    tagsMap: Map<String, Tag>,
    onConditionChange: (TaskCondition) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // ── 顶部类型切换条 ──
            NodeTypeBar(
                currentType = conditionTypeOf(condition),
                onTypeChange = { newType ->
                    onConditionChange(emptyConditionOf(newType))
                },
                onDelete = onDelete,
            )

            // ── 根据类型渲染具体编辑器 ──
            when (condition) {
                is MultiTagCondition -> MultiTagEditor(
                    condition = condition,
                    allTags = allTags,
                    onConditionChange = onConditionChange,
                )

                is AndCondition -> GroupConditionEditor(
                    title = "AND 条件组",
                    conditions = condition.conditions,
                    allTags = allTags,
                    tagsMap = tagsMap,
                    onConditionsChange = { onConditionChange(condition.copy(conditions = it)) },
                )

                is OrCondition -> GroupConditionEditor(
                    title = "OR 条件组",
                    conditions = condition.conditions,
                    allTags = allTags,
                    tagsMap = tagsMap,
                    onConditionsChange = { onConditionChange(condition.copy(conditions = it)) },
                )

            }
        }
    }
}

// ── 多标签编辑器（任一满足）────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiTagEditor(
    condition: MultiTagCondition,
    allTags: List<Tag>,
    onConditionChange: (TaskCondition) -> Unit,
) {
    val selectedIds = condition.tagIds.toSet()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        allTags.forEach { tag ->
            val isSelected = tag.id in selectedIds
            val tagColor = try {
                Color(AndroidColor.parseColor(tag.color))
            } catch (_: Exception) {
                MaterialTheme.colorScheme.primary
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    val newIds = if (isSelected) {
                        condition.tagIds - tag.id
                    } else {
                        condition.tagIds + tag.id
                    }
                    onConditionChange(condition.copy(tagIds = newIds))
                },
                label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tagColor.copy(alpha = 0.3f),
                    selectedLabelColor = tagColor,
                ),
            )
        }
    }
}

// ── AND/OR 条件组编辑器 ───────────────────────────────────────

@Composable
private fun GroupConditionEditor(
    title: String,
    conditions: List<TaskCondition>,
    allTags: List<Tag>,
    tagsMap: Map<String, Tag>,
    onConditionsChange: (List<TaskCondition>) -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (conditions.isEmpty()) {
            Text(
                text = "暂无条件，点击下方「添加」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            conditions.forEachIndexed { index, child ->
                ConditionNodeEditor(
                    condition = child,
                    allTags = allTags,
                    tagsMap = tagsMap,
                    onConditionChange = { newChild ->
                        onConditionsChange(
                            conditions.toMutableList().also { it[index] = newChild }
                        )
                    },
                    onDelete = {
                        onConditionsChange(
                            conditions.toMutableList().also { it.removeAt(index) }
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // 添加按钮 — 默认创建多标签条件，用户可通过切换器改类型
        TextButton(
            onClick = {
                onConditionsChange(conditions + MultiTagCondition(emptyList()))
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加子条件")
        }
    }
}


