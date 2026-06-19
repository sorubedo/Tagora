package com.tagora.app.ui.main

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.PeriodType
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.typeEnum
import com.tagora.app.theme.TagoraTheme
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.util.dayOfWeeksText
import com.tagora.app.util.minutesToTimeString

/**
 * CardGroup 风格时间段列表页
 * 展示日时段、周时段和日期时段三个分组
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePeriodListPage(
    dailyPeriods: List<TimePeriod>,
    weeklyPeriods: List<TimePeriod>,
    datePeriods: List<TimePeriod>,
    deadlinePeriods: List<TimePeriod>,
    tags: Map<String, Tag>,
    onDailyPeriodClick: (TimePeriod) -> Unit,
    onWeeklyPeriodClick: (TimePeriod) -> Unit,
    onDatePeriodClick: (TimePeriod) -> Unit,
    onDeadlinePeriodClick: (TimePeriod) -> Unit,
    onAddDailyClick: () -> Unit,
    onAddWeeklyClick: () -> Unit,
    onAddDateClick: () -> Unit,
    onAddDeadlineClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeTagIds: Set<String> = emptySet(),
) {
    val isEmpty = dailyPeriods.isEmpty() && weeklyPeriods.isEmpty() && datePeriods.isEmpty() && deadlinePeriods.isEmpty()

    Box(modifier = modifier) {
        if (isEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "还没有时间段",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "点击右下角 + 按钮添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 日时段分组
                if (dailyPeriods.isNotEmpty()) PeriodGroupSection(title = "日时段", periods = dailyPeriods, tags = tags, onClick = onDailyPeriodClick)
                // 周时段分组
                if (weeklyPeriods.isNotEmpty()) PeriodGroupSection(title = "周时段", periods = weeklyPeriods, tags = tags, onClick = onWeeklyPeriodClick)
                // 日期时段分组
                if (datePeriods.isNotEmpty()) PeriodGroupSection(title = "日期时段", periods = datePeriods, tags = tags, onClick = onDatePeriodClick)
                // 死线时段分组
                if (deadlinePeriods.isNotEmpty()) PeriodGroupSection(title = "死线时段", periods = deadlinePeriods, tags = tags, onClick = onDeadlinePeriodClick)
            }
        }

        // FAB 列：日时段 + 周时段 + 日期时段 + 死线时段
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = onAddDailyClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加日时间段",
                )
            }
            FloatingActionButton(
                onClick = onAddWeeklyClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加周时间段",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            FloatingActionButton(
                onClick = onAddDateClick,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加日期时间段",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            FloatingActionButton(
                onClick = onAddDeadlineClick,
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加死线时间段",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/**
 * 时间段分组列表区段。统一了"日/周/日期/死线"四种分组的 UI 模板。
 */
private fun LazyListScope.PeriodGroupSection(
    title: String,
    periods: List<TimePeriod>,
    tags: Map<String, Tag>,
    onClick: (TimePeriod) -> Unit,
) {
    item {
        CardGroup(title = { Text(title) }) {
            periods.forEachIndexed { index, period ->
                val periodTags = period.tagIds.mapNotNull { tags[it] }
                CardGroupItem(
                    onClick = { onClick(period) },
                    isLast = index == periods.lastIndex,
                ) {
                    PeriodListItem(period = period, periodTags = periodTags)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodListItem(
    period: TimePeriod,
    periodTags: List<Tag>,
) {
    val color = Color(AndroidColor.parseColor(period.color))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 颜色圆点
        Canvas(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape),
        ) {
            drawCircle(color = color)
        }
        Spacer(Modifier.width(16.dp))

        // 中间内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = period.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = period.subtitleText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (periodTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        periodTags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (periodTags.size > 2) {
                            Text(
                                text = "+${periodTags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
        }

        // 右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimePeriodListPagePreview() {
    val sampleDaily = listOf(
        TimePeriod("1", "晨间", "daily", 360, 480, "#FFF2C94C", listOf("t1")),
        TimePeriod("2", "工作时间", "daily", 480, 720, "#FF6B9CE1", listOf("t1", "t2")),
        TimePeriod("3", "午休", "daily", 720, 840, "#FF52B788", emptyList()),
    )
    val sampleWeekly = listOf(
        TimePeriod("w-mon", "周一", "weekly", 0, 0, "#FF6B9CE1", listOf("t3"), dayOfWeeks = listOf(1)),
        TimePeriod("w-sat", "周六", "weekly", 0, 0, "#FF52B788", listOf("t4"), dayOfWeeks = listOf(6)),
        TimePeriod("w-workday", "工作日", "weekly", 0, 0, "#FF42A5F5", listOf("t5"), dayOfWeeks = listOf(1, 2, 3, 4, 5)),
    )
    val sampleDate = listOf(
        TimePeriod("dw-1", "第1周", "date", 0, 0, "#FF6B9CE1", listOf("t-w1"), startDate = "2026-03-02", endDate = "2026-03-08"),
        TimePeriod("dw-2", "第2周", "date", 0, 0, "#FFE17055", listOf("t-w2"), startDate = "2026-03-09", endDate = "2026-03-15"),
    )
    val sampleTags = mapOf(
        "t1" to Tag("t1", "工作", "#FFE17055"),
        "t2" to Tag("t2", "专注", "#FF52B788"),
        "t3" to Tag("t3", "周一", "#FF6B9CE1"),
        "t4" to Tag("t4", "周六", "#FF52B788"),
        "t5" to Tag("t5", "工作日", "#FF42A5F5"),
        "t-w1" to Tag("t-w1", "第1周", "#FF6B9CE1"),
        "t-w2" to Tag("t-w2", "第2周", "#FF6B9CE1"),
    )

    TagoraTheme {
        TimePeriodListPage(
            dailyPeriods = sampleDaily,
            weeklyPeriods = sampleWeekly,
            datePeriods = sampleDate,
            deadlinePeriods = emptyList(),
            tags = sampleTags,
            onDailyPeriodClick = {},
            onWeeklyPeriodClick = {},
            onDatePeriodClick = {},
            onDeadlinePeriodClick = {},
            onAddDailyClick = {},
            onAddWeeklyClick = {},
            onAddDateClick = {},
            onAddDeadlineClick = {},
        )
    }
}

/** 获取时间段的副标题文本 */
private fun TimePeriod.subtitleText(): String = when (typeEnum) {
    PeriodType.WEEKLY -> dayOfWeeksText(dayOfWeeks)
    PeriodType.DATE -> {
        val s = startDate ?: "?"
        val e = endDate ?: "?"
        "$s ~ $e"
    }
    PeriodType.DEADLINE -> "截止: ${endDate ?: "?"}"
    else -> "${minutesToTimeString(startMinute)} - ${minutesToTimeString(endMinute)}"
}
