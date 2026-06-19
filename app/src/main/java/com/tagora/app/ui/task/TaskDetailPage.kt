package com.tagora.app.ui.task

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.AndCondition
import com.tagora.app.data.model.MultiTagCondition
import com.tagora.app.data.model.NotCondition
import com.tagora.app.data.model.OrCondition
import com.tagora.app.util.newId
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskCondition
import com.tagora.app.data.model.TaskType
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.ui.components.FormItem
import com.tagora.app.ui.components.rememberUnsavedChangesGuard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 生成条件的人类可读摘要
 * 例如："工作", "工作 AND 专注", "NOT (休息 OR 用餐)"
 */
fun conditionSummary(condition: TaskCondition, tagsMap: Map<String, Tag>): String {
    return when (condition) {
        is MultiTagCondition -> {
            if (condition.tagIds.isEmpty()) "(未设置)"
            else {
                val names = condition.tagIds.mapNotNull { tagsMap[it]?.name }
                if (names.size <= 3) names.joinToString(" OR ")
                else names.take(3).joinToString(" OR ") + " (+${names.size - 3})"
            }
        }

        is AndCondition -> {
            if (condition.conditions.isEmpty()) "无条件"
            else condition.conditions.joinToString(" AND ") {
                when (it) {
                    is MultiTagCondition -> conditionSummary(it, tagsMap)
                    else -> "(${conditionSummary(it, tagsMap)})"
                }
            }
        }

        is OrCondition -> {
            if (condition.conditions.isEmpty()) "无条件"
            else condition.conditions.joinToString(" OR ") {
                when (it) {
                    is MultiTagCondition -> conditionSummary(it, tagsMap)
                    else -> "(${conditionSummary(it, tagsMap)})"
                }
            }
        }

        is NotCondition -> {
            "NOT ${conditionSummary(condition.condition, tagsMap)}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailPage(
    taskId: String?,
    onBack: () -> Unit,
    onConditionEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taskRepo = RepositoryProvider.getTaskRepo(context)
    val repository = RepositoryProvider.get(context)
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var condition by remember { mutableStateOf<TaskCondition>(AndCondition(emptyList())) }
    var taskType by rememberSaveable { mutableStateOf(TaskType.NORMAL) }
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    // 新建任务时生成的临时 ID，确保条件编辑与最终保存使用同一个 ID
    var generatedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    // 保存初始状态用于比对
    var initialName by rememberSaveable { mutableStateOf("") }
    var initialDescription by rememberSaveable { mutableStateOf("") }
    var initialCondition by remember { mutableStateOf<TaskCondition>(AndCondition(emptyList())) }
    var initialTaskType by rememberSaveable { mutableStateOf(TaskType.NORMAL) }

    LaunchedEffect(Unit) {
        // 始终刷新标签列表
        allTags = repository.tagsFlow.first()

        // 只在首次进入时加载初始数据，防止从条件编辑页返回后覆盖编辑
        if (!loaded) {
            val tasks = taskRepo.tasksFlow.first()
            val task = tasks.find { it.id == taskId }
            if (task != null) {
                name = task.name
                description = task.description
                condition = task.condition
                taskType = task.type
            }
            loaded = true
            // 保存初始状态
            initialName = name
            initialDescription = description
            initialCondition = condition
            initialTaskType = taskType
        }
    }

    // 接收条件编辑页传回的条件
    LaunchedEffect(Unit) {
        snapshotFlow { TaskEditState.pendingCondition }
            .collect { pending ->
                if (pending != null) {
                    condition = pending
                    TaskEditState.takeCondition()
                }
            }
    }

    val tagsMap = remember(allTags) { allTags.associateBy { it.id } }
    val isNew = taskId == null

    // 是否有未保存的变更
    val hasChanges = loaded && (
        name != initialName ||
        description != initialDescription ||
        condition != initialCondition ||
        taskType != initialTaskType
    )

    // 未保存变更：系统返回键拦截 + 确认弹窗
    val triggerUnsavedGuard = rememberUnsavedChangesGuard(
        hasChanges = hasChanges,
        title = "放弃更改？",
        message = "有未保存的修改，确定要返回吗？",
        onDiscard = onBack,
    )

    // 处理顶部返回按钮
    fun handleBack() {
        if (hasChanges) triggerUnsavedGuard() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建任务" else "编辑任务") },
                navigationIcon = {
                    TextButton(onClick = { handleBack() }) { Text("← 返回") }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 名称
            CardGroup {
                FormItem(
                    label = { Text("任务名") },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如：上午学习、午间运动") },
                    )
                }
            }

            // 描述
            CardGroup {
                FormItem(
                    label = { Text("描述") },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = { Text("可选：任务的详细说明") },
                    )
                }
            }

            // 任务类型
            CardGroup(title = { Text("任务类型") }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = taskType == TaskType.NORMAL,
                            onClick = { taskType = TaskType.NORMAL },
                            label = { Text("普通事件") },
                        )
                        FilterChip(
                            selected = taskType == TaskType.FIXED,
                            onClick = { taskType = TaskType.FIXED },
                            label = { Text("固定事件") },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (taskType == TaskType.FIXED)
                            "到点自动激活，无法手动完成。tags 永远无法满足时自动完成"
                        else
                            "激活时可点击完成。tags 永远无法满足时超时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 标签条件
            CardGroup(title = { Text("标签条件") }) {
                CardGroupItem(
                    onClick = {
                        // 不在此处保存任务，条件通过 TaskEditState 传回，
                        // 最终由用户点击「保存」按钮统一持久化
                        val id = if (isNew) {
                            generatedTaskId ?: newId().also { generatedTaskId = it }
                        } else {
                            taskId
                        }
                        onConditionEdit(id)
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "激活条件",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = conditionSummary(condition, tagsMap),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val tasks = taskRepo.tasksFlow.first().toMutableList()
                        val id = taskId ?: generatedTaskId ?: newId()
                        val task = Task(
                            id = id,
                            name = name.ifBlank { "未命名任务" },
                            description = description,
                            condition = condition,
                            type = taskType,
                        )
                        val index = tasks.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            tasks[index] = task
                        } else {
                            tasks.add(task)
                        }
                        taskRepo.saveTasks(tasks)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }

            if (!isNew) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val tasks = taskRepo.tasksFlow.first().filter { it.id != taskId }
                            taskRepo.saveTasks(tasks)
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除此任务")
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

}
