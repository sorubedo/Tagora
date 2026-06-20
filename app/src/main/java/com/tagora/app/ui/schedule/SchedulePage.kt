package com.tagora.app.ui.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.tagora.app.TaskDetail
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.collectTagIds
import com.tagora.app.data.model.descriptionWithoutLocation
import com.tagora.app.data.model.locationText
import com.tagora.app.ui.components.LoadingIndicator
import com.tagora.app.ui.components.TopAppBarWithBack
import androidx.compose.ui.graphics.Color as ComposeColor

// ── 布局常量 ──────────────────────────────────────────────────────

/** 左侧标签列固定宽度 */
private const val LEFT_COL_WIDTH = 56
/** 顶部星期头高度 */
private const val HEADER_HEIGHT = 44
/** 每个格子高度（与左侧标签同步） */
private const val CELL_HEIGHT = 72

// ── 工具函数 ──────────────────────────────────────────────────────

/** 将分钟数（0~1439）格式化为 "HH:MM" */
private fun minutesToTimeString(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

// ── 主页面 ────────────────────────────────────────────────────────

/**
 * 课程表周视图页面。
 */
@Composable
fun SchedulePage(
    onBack: () -> Unit,
    onTaskEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { RepositoryProvider.get(context) }
    val taskRepo = remember { RepositoryProvider.getTaskRepo(context) }
    val viewModel: ScheduleViewModel = viewModel {
        ScheduleViewModel(repository, taskRepo)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBarWithBack(
                title = "课程表",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "说明",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when (val s = state) {
            ScheduleUiState.Loading -> {
                LoadingIndicator(
                    text = "加载课程表...",
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is ScheduleUiState.Error -> {
                ScheduleErrorContent(
                    message = s.message,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is ScheduleUiState.Ready -> {
                ScheduleReadyContent(
                    state = s,
                    onPreviousWeek = { viewModel.previousWeek() },
                    onNextWeek = { viewModel.nextWeek(s.weeks.lastIndex) },
                    onTaskClick = onTaskEdit,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    // ── 说明对话框 ─────────────────────────────────────────────────
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text("课程表说明") },
            text = {
                Text(
                    text = buildString {
                        appendLine("此视图模拟每天的课程安排，综合考虑三种时间段：")
                        appendLine()
                        appendLine("● 日时段 — 左侧的课程节次（如「第1节」「第2节」…），决定任务在当天的哪个时间区间激活")
                        appendLine()
                        appendLine("● 周时段 — 顶部的星期几（如「周一」「周二」…），决定任务在一周中的哪一天激活")
                        appendLine()
                        appendLine("● 日期时段 — 上方的教学周（如「第1周」「第2周」…），决定任务在学期中的哪一周激活")
                        appendLine()
                        appendLine("格子中只显示固定事件（FIXED）类型的任务。任务的标签条件需同时包含以上三种时段对应的标签，才会出现在对应格子中。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("知道了")
                }
            },
        )
    }
}

// ── 错误状态 ──────────────────────────────────────────────────────

@Composable
private fun ScheduleErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "无法显示课程表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

// ── 数据就绪内容 ─────────────────────────────────────────────────

@Composable
private fun ScheduleReadyContent(
    state: ScheduleUiState.Ready,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekPeriod = state.weeks[state.currentWeekIndex]

    Column(modifier = modifier.fillMaxSize()) {
        // ── 周选择器 ───────────────────────────────────────────────
        WeekSelector(
            week = weekPeriod,
            canGoPrevious = state.currentWeekIndex > 0,
            canGoNext = state.currentWeekIndex < state.weeks.lastIndex,
            onPrevious = onPreviousWeek,
            onNext = onNextWeek,
        )

        // ── 提示信息 ───────────────────────────────────────────────
        if (state.classPeriods.isEmpty()) {
            Text(
                text = "暂无课程节次数据，请确保每日时间段中包含课程节次（如「第1节」~「第12节」）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (state.weekDays.isEmpty()) {
            Text(
                text = "暂无星期时段数据，请确保每周时间段中包含「周一」~「周日」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (state.weekDays.isNotEmpty() && state.classPeriods.isNotEmpty() &&
            state.cellTaskMap.isEmpty()
        ) {
            Text(
                text = "当前周没有任何课程任务。请在「任务管理」中新建 FIXED 类型任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // ── 网格 ───────────────────────────────────────────────────
        if (state.classPeriods.isNotEmpty() && state.weekDays.isNotEmpty()) {
            ScheduleGrid(
                classPeriods = state.classPeriods,
                weekDays = state.weekDays,
                cellTaskMap = state.cellTaskMap,
                tagsMap = state.tagsMap,
                onTaskClick = onTaskClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ── 周选择器 ──────────────────────────────────────────────────────

@Composable
private fun WeekSelector(
    week: TimePeriod,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一周",
                    tint = if (canGoPrevious)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    text = week.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (week.startDate != null && week.endDate != null) {
                    Text(
                        text = "${week.startDate} ~ ${week.endDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一周",
                    tint = if (canGoNext)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ── 课程表网格 ────────────────────────────────────────────────────

@Composable
private fun ScheduleGrid(
    classPeriods: List<TimePeriod>,
    weekDays: List<TimePeriod>,
    cellTaskMap: Map<Pair<Int, Int>, List<Task>>,
    tagsMap: Map<String, Tag>,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vScrollState = rememberScrollState()

    Row(modifier = modifier) {
        // ── 左侧固定列 ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(LEFT_COL_WIDTH.dp)
                .verticalScroll(vScrollState),
        ) {
            // 角落占位（与顶部 Header 等高）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT.dp),
            )

            // 课程节次标签
            classPeriods.forEach { period ->
                ClassPeriodLabel(period = period)
            }
        }

        // ── 右侧网格区域（宽度自适应，无需水平滚动） ──────────────
        Column(modifier = Modifier.weight(1f)) {
            // 顶部星期头（垂直固定，水平对齐网格列）
            Row {
                weekDays.forEach { day ->
                    DayHeaderCell(period = day, modifier = Modifier.weight(1f))
                }
            }

            // 网格主体（垂直滚动）
            Column(
                modifier = Modifier.verticalScroll(vScrollState),
            ) {
                classPeriods.forEachIndexed { rowIdx, _ ->
                    Row {
                        weekDays.forEachIndexed { colIdx, _ ->
                            val tasks = cellTaskMap[rowIdx to colIdx].orEmpty()
                            GridCell(
                                tasks = tasks,
                                tagsMap = tagsMap,
                                onClick = { taskId -> onTaskClick(taskId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 顶部星期头单元格 ──────────────────────────────────────────────

@Composable
private fun DayHeaderCell(period: TimePeriod, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(HEADER_HEIGHT.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val isToday = rememberIsToday(period)
            val textColor by animateColorAsState(
                if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                label = "dayHeaderColor",
            )

            Text(
                text = period.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── 左侧课程节次标签 ──────────────────────────────────────────────

@Composable
private fun ClassPeriodLabel(period: TimePeriod) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CELL_HEIGHT.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = period.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 两行时间：开始时间和结束时间各一行
            Text(
                text = minutesToTimeString(period.startMinute),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = minutesToTimeString(period.endMinute),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ── 网格单元格 ────────────────────────────────────────────────────

@Composable
private fun GridCell(
    tasks: List<Task>,
    tagsMap: Map<String, Tag>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (tasks.isEmpty()) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        val firstTagHex = tasks.firstNotNullOfOrNull { task ->
            val tagIds = task.condition.collectTagIds()
            tagIds.firstNotNullOfOrNull { id -> tagsMap[id]?.color }
        }
        parseHexColor(firstTagHex)?.copy(alpha = 0.15f)
            ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier.height(CELL_HEIGHT.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            tasks.take(3).forEach { task ->
                val taskTagColor = rememberTaskTagColor(task, tagsMap)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            (taskTagColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                        )
                        .clickable { onClick(task.id) }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = taskTagColor ?: MaterialTheme.colorScheme.onSurface,
                    )
                    task.locationText?.let { location ->
                        Text(
                            text = location,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            if (tasks.size > 3) {
                Text(
                    text = "+${tasks.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

// ── 辅助函数 ──────────────────────────────────────────────────────

@Composable
private fun rememberIsToday(period: TimePeriod): Boolean {
    val todayDayOfWeek = remember {
        java.time.LocalDate.now().dayOfWeek.value
    }
    return period.dayOfWeeks.contains(todayDayOfWeek)
}

@Composable
private fun rememberTaskTagColor(task: Task, tagsMap: Map<String, Tag>): ComposeColor? {
    return remember(task.id, tagsMap) {
        val tagIds = task.condition.collectTagIds()
        val firstColorHex = tagIds.firstNotNullOfOrNull { id -> tagsMap[id]?.color }
        parseHexColor(firstColorHex)
    }
}

private fun parseHexColor(hex: String?): ComposeColor? {
    if (hex == null) return null
    return try {
        val c = android.graphics.Color.parseColor(hex)
        ComposeColor(
            red = android.graphics.Color.red(c) / 255f,
            green = android.graphics.Color.green(c) / 255f,
            blue = android.graphics.Color.blue(c) / 255f,
            alpha = android.graphics.Color.alpha(c) / 255f,
        )
    } catch (_: Exception) {
        null
    }
}
