package com.tagora.app.ui.main

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.isNormal
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.ui.components.ConfirmDialog
import com.tagora.app.theme.DashboardColors
import com.tagora.app.ui.task.conditionSummary
import com.tagora.app.util.dayOfWeekDisplayNames
import com.tagora.app.ui.timeperiod.components.TagChip
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 任务在 Dashboard 中的六种激活状态
 */
private enum class TaskActivationState {
    /** 当前激活：标签条件被当前时间匹配 */
    ACTIVE,
    /** 死线任务：当前未激活，但涉及未过期的死线标签 */
    DEADLINE,
    /** 当天任务：当前未激活，但当天某个日时间线会激活 */
    DAILY,
    /** 明日任务：当天未激活，但明天会激活 */
    TOMORROW,
    /** 当周任务：本周会激活（不在上述范围） */
    WEEKLY,
    /** 未激活：当周不会激活 */
    INACTIVE,
}

/**
 * Dashboard 控制面板
 *
 * 实时显示：
 * - 当前日期时间
 * - 当前激活的标签
 * - 当前激活的任务
 * - 当天任务列表（使用当天全部日时间线标签评估）
 * - 全部任务概览
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(
    activeTagIds: Set<String>,
    dailyTagIds: Set<String>,
    dailyPeriodCount: Int,
    weeklyTagIds: Set<String>,
    weeklyPeriodCount: Int,
    tomorrowTagIds: Set<String>,
    deadlineTagIds: Set<String>,
    tasks: List<Task>,
    allTags: List<Tag>,
    tagsMap: Map<String, Tag>,
    onTaskClick: (String) -> Unit,
    onTaskComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // produceState：Compose 惯用的"随时间变化的异步值"模式
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1000L)
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }
    val dayOfWeekNames = dayOfWeekDisplayNames

    // 激活的标签对象列表
    val activeTags = remember(activeTagIds, allTags) {
        allTags.filter { it.id in activeTagIds }
    }

    // 仅当标签集非空时才判断匹配，防止无条件任务（空条件对空集也返回 true）被误判
    fun matches(task: Task, tagIds: Set<String>) =
        tagIds.isNotEmpty() && task.condition.evaluate(tagIds)

    // 今天所有可能激活的标签（daily全部 + 今天匹配的weekly/date + deadline全部）
    // 用于评估跨类型 AND 条件（如 "(第一节课 OR 第二节课) AND (周六)"），
    // 否则仅用 dailyTagIds 会遗漏 weekly/date/deadline 标签导致条件永远不满足
    val todayRelevantTagIds = remember(activeTagIds, dailyTagIds, deadlineTagIds) {
        activeTagIds + dailyTagIds + deadlineTagIds
    }

    // 本周所有可能激活的标签（daily全部 + weekly全部 + deadline全部 + 今天匹配的date）
    val thisWeekRelevantTagIds = remember(activeTagIds, dailyTagIds, weeklyTagIds, deadlineTagIds) {
        activeTagIds + dailyTagIds + weeklyTagIds + deadlineTagIds
    }

    // 六态分类
    fun classifyTask(task: Task): TaskActivationState = when {
        matches(task, deadlineTagIds) -> TaskActivationState.DEADLINE
        task.condition.evaluate(activeTagIds) -> TaskActivationState.ACTIVE
        matches(task, todayRelevantTagIds) -> TaskActivationState.DAILY
        matches(task, tomorrowTagIds) -> TaskActivationState.TOMORROW
        matches(task, thisWeekRelevantTagIds) -> TaskActivationState.WEEKLY
        else -> TaskActivationState.INACTIVE
    }

    // 死线任务
    val deadlineTasks = remember(deadlineTagIds, tasks) {
        tasks.filter { matches(it, deadlineTagIds) }
    }

    // 当前激活的任务（非死线）
    val activeTasks = remember(activeTagIds, deadlineTagIds, tasks) {
        tasks.filter { task ->
            !matches(task, deadlineTagIds) && task.condition.evaluate(activeTagIds)
        }
    }

    // 当天任务：使用 todayRelevantTagIds 替代 dailyTagIds，支持跨类型 AND 条件
    val dailyTasks = remember(deadlineTagIds, activeTagIds, todayRelevantTagIds, tasks) {
        tasks.filter { task ->
            !matches(task, deadlineTagIds) &&
                !task.condition.evaluate(activeTagIds) &&
                matches(task, todayRelevantTagIds)
        }
    }

    // 明日任务
    val tomorrowTasks = remember(deadlineTagIds, activeTagIds, todayRelevantTagIds, tomorrowTagIds, tasks) {
        tasks.filter { task ->
            !matches(task, deadlineTagIds) &&
                !task.condition.evaluate(activeTagIds) &&
                !matches(task, todayRelevantTagIds) &&
                matches(task, tomorrowTagIds)
        }
    }

    // 当周任务：使用 thisWeekRelevantTagIds 替代 weeklyTagIds，支持跨类型 AND 条件
    val weeklyTasks = remember(deadlineTagIds, activeTagIds, todayRelevantTagIds, tomorrowTagIds, thisWeekRelevantTagIds, tasks) {
        tasks.filter { task ->
            !matches(task, deadlineTagIds) &&
                !task.condition.evaluate(activeTagIds) &&
                !matches(task, todayRelevantTagIds) &&
                !matches(task, tomorrowTagIds) &&
                matches(task, thisWeekRelevantTagIds)
        }
    }

    // 未激活任务
    val inactiveTasks = remember(deadlineTagIds, activeTagIds, todayRelevantTagIds, tomorrowTagIds, thisWeekRelevantTagIds, tasks) {
        tasks.filter { task ->
            !matches(task, deadlineTagIds) &&
                !task.condition.evaluate(activeTagIds) &&
                !matches(task, todayRelevantTagIds) &&
                !matches(task, tomorrowTagIds) &&
                !matches(task, thisWeekRelevantTagIds)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 当前时间 ──
        item {
            CardGroup(
                title = {
                    Text(
                        text = "当前时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                },
            ) {
                CardGroupItem(isLast = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val today = remember { LocalDate.now() }
                        Text(
                            text = "${dateFormatter.format(today)} ${dayOfWeekNames[today.dayOfWeek.value]}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = timeFormatter.format(now),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ── 当前激活标签 ──
        item {
            CardGroup(
                title = {
                    Text(
                        text = "当前激活标签 (${activeTags.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                },
            ) {
                CardGroupItem(isLast = true) {
                    if (activeTags.isEmpty()) {
                        Text(
                            text = "当前无激活标签",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            activeTags.forEach { tag ->
                                TagChip(
                                    tag = tag,
                                    selected = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 死线任务的空提示
        val deadlineEmptyMessage = when {
            deadlineTagIds.isEmpty() -> "暂无死线时间段，请先添加死线类型"
            else -> "当前无待处理的死线任务"
        }
        // 当天任务的空提示
        val dailyEmptyMessage = when {
            dailyPeriodCount == 0 -> "暂无每日时间段配置，请先添加日时间线"
            dailyTagIds.isEmpty() -> "日时间线未关联任何标签，请为时间段添加标签"
            else -> "当天无其他待激活任务"
        }
        // 明日任务的空提示
        val tomorrowEmptyMessage = if (tomorrowTagIds.isEmpty())
            "明天暂无匹配的标签激活，请检查周/日期时间线"
        else
            "明天无新增待激活任务"
        // 当周任务的空提示
        val weeklyEmptyMessage = when {
            weeklyPeriodCount == 0 -> "暂无每周时间段配置，请先添加周时间线"
            weeklyTagIds.isEmpty() -> "周时间线未关联任何标签，请为时间段添加标签"
            else -> "当周无其他待激活任务"
        }

        TaskCategorySection(
            title = "当前激活任务",
            tasks = activeTasks,
            state = TaskActivationState.ACTIVE,
            emptyMessage = "当前无激活任务",
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )
        TaskCategorySection(
            title = "死线任务",
            tasks = deadlineTasks,
            state = TaskActivationState.DEADLINE,
            emptyMessage = deadlineEmptyMessage,
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )
        TaskCategorySection(
            title = "当天任务列表",
            tasks = dailyTasks,
            state = TaskActivationState.DAILY,
            emptyMessage = dailyEmptyMessage,
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )
        TaskCategorySection(
            title = "明日任务列表",
            tasks = tomorrowTasks,
            state = TaskActivationState.TOMORROW,
            emptyMessage = tomorrowEmptyMessage,
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )
        TaskCategorySection(
            title = "当周任务列表",
            tasks = weeklyTasks,
            state = TaskActivationState.WEEKLY,
            emptyMessage = weeklyEmptyMessage,
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )
        TaskCategorySection(
            title = "全部任务",
            tasks = inactiveTasks,
            state = TaskActivationState.INACTIVE,
            emptyMessage = "所有任务已在上方列表中",
            tagsMap = tagsMap,
            onTaskClick = onTaskClick,
            onTaskComplete = onTaskComplete,
        )

        // 底部留白
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * 任务分类卡片区域。统一了"激活/死线/当天/明日/当周/全部"六种分类的 UI 模板。
 */
private fun LazyListScope.TaskCategorySection(
    title: String,
    tasks: List<Task>,
    state: TaskActivationState,
    emptyMessage: String,
    tagsMap: Map<String, Tag>,
    onTaskClick: (String) -> Unit,
    onTaskComplete: (String) -> Unit,
) {
    item {
        CardGroup(
            title = {
                Text(
                    text = "$title (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            },
        ) {
            if (tasks.isEmpty()) {
                CardGroupItem(isLast = true) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                tasks.forEachIndexed { index, task ->
                    SwipeableTaskItem(
                        task = task,
                        tagsMap = tagsMap,
                        state = state,
                        isLast = index == tasks.lastIndex,
                        onEdit = { onTaskClick(task.id) },
                        onComplete = if (task.isNormal) ({ onTaskComplete(task.id) }) else null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskItem(
    task: Task,
    tagsMap: Map<String, Tag>,
    state: TaskActivationState,
    isLast: Boolean,
    onEdit: () -> Unit,
    onComplete: (() -> Unit)? = null,
) {
    // 所有普通任务都可以滑动完成，非激活状态完成时需二次确认
    val canComplete = task.isNormal && onComplete != null
    val isActive = state == TaskActivationState.ACTIVE || state == TaskActivationState.DEADLINE
    val haptic = LocalHapticFeedback.current
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }
    // 防止 confirmValueChange 在动画期间被多次调用导致重复导航
    var actionTriggered by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!actionTriggered) {
                        actionTriggered = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit()
                    }
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (canComplete && !actionTriggered) {
                        actionTriggered = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isActive) {
                            onComplete!!.invoke()
                        } else {
                            showCompleteConfirmDialog = true
                        }
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    // 开始滑动时触发轻量振动
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .collect { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
    ) {
        CardGroupItem(onClick = onEdit, isLast = isLast) {
            TaskRowContent(
                task = task,
                tagsMap = tagsMap,
                state = state,
                showCompleteHint = canComplete,
            )
        }
    }

    if (showCompleteConfirmDialog) {
        ConfirmDialog(
            title = "确认完成",
            text = "该任务尚未激活，确定要标记为完成吗？",
            confirmText = "确定完成",
            onConfirm = { onComplete!!.invoke() },
            onDismiss = { showCompleteConfirmDialog = false; actionTriggered = false },
        )
    }
}

@Composable
private fun TaskRowContent(
    task: Task,
    tagsMap: Map<String, Tag>,
    state: TaskActivationState,
    showCompleteHint: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 六态激活指示器
        val (indicator, indicatorColor) = when (state) {
            TaskActivationState.ACTIVE -> "●" to DashboardColors.ActiveGreen
            TaskActivationState.DEADLINE -> "◈" to DashboardColors.DeadlinePink
            TaskActivationState.DAILY -> "◐" to DashboardColors.DailyOrange
            TaskActivationState.TOMORROW -> "◐" to DashboardColors.TomorrowCyan
            TaskActivationState.WEEKLY -> "◐" to DashboardColors.WeeklyBlue
            TaskActivationState.INACTIVE -> "○" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        }
        Text(
            text = indicator,
            color = indicatorColor,
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
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conditionSummary(task.condition, tagsMap),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 滑动提示
                if (showCompleteHint) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DashboardColors.ActiveGreen.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "← 完成",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = DashboardColors.ActiveGreen.copy(alpha = 0.6f),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DashboardColors.WeeklyBlue.copy(alpha = 0.1f),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(
                        text = "编辑 →",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = DashboardColors.WeeklyBlue.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
