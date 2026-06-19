package com.tagora.app.ui.timeperiod.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.collectTagIds
import com.tagora.app.data.model.isIncomplete
import com.tagora.app.theme.TagoraTheme

/**
 * 24 小时时间线可视化组件。
 * 时间段按 startMinute 排序，紧凑卡片列表布局，内容自撑高度。
 */
@Composable
fun DayTimeline(
    periods: List<TimePeriod>,
    tags: Map<String, Tag>,
    onPeriodClick: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier,
    activeTagIds: Set<String> = emptySet(),
    tasks: List<Task> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        periods.forEach { period ->
            val periodTags = period.tagIds.mapNotNull { tags[it] }

            // 该时间段关联的激活任务
            val periodTasks = tasks.filter { task ->
                task.isIncomplete &&
                task.condition.evaluate(activeTagIds) &&
                task.condition.collectTagIds().any { it in period.tagIds }
            }

            val isActive = period.tagIds.isNotEmpty() && period.tagIds.any { it in activeTagIds }

            TimePeriodBlock(
                period = period,
                tags = periodTags,
                isActive = isActive,
                onClick = { onPeriodClick(period) },
                tasks = periodTasks,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DayTimelinePreview() {
    val samplePeriods = listOf(
        TimePeriod(id = "p1", name = "晨间", startMinute = 360, endMinute = 480, color = "#FFF2C94C", tagIds = emptyList()),
        TimePeriod(id = "p2", name = "工作时间", startMinute = 480, endMinute = 720, color = "#FF6B9CE1", tagIds = listOf("t1")),
        TimePeriod(id = "p3", name = "午休", startMinute = 720, endMinute = 840, color = "#FF52B788", tagIds = emptyList()),
        TimePeriod(id = "p4", name = "午后", startMinute = 840, endMinute = 1080, color = "#FFE17055", tagIds = listOf("t1", "t2")),
        TimePeriod(id = "p5", name = "晚间", startMinute = 1080, endMinute = 1320, color = "#FF9B51E0", tagIds = emptyList()),
        TimePeriod(id = "p6", name = "深夜", startMinute = 1320, endMinute = 360, color = "#FF546E7A", tagIds = emptyList()),
    )
    val sampleTags = mapOf(
        "t1" to Tag("t1", "工作", "#FFE17055"),
        "t2" to Tag("t2", "专注", "#FF52B788"),
    )

    TagoraTheme {
        DayTimeline(
            periods = samplePeriods,
            tags = sampleTags,
            onPeriodClick = {},
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
