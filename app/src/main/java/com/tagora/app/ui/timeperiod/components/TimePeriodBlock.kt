package com.tagora.app.ui.timeperiod.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.theme.TagoraTheme
import com.tagora.app.util.contentColorFor
import com.tagora.app.util.minutesToTimeString

/**
 * 时间段色块组件，显示在 DayTimeline 中
 * 参考 RikkaHub 的 Card/Surface 模式 + animateColorAsState
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePeriodBlock(
    period: TimePeriod,
    tags: List<Tag>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    tasks: List<Task> = emptyList(),
) {
    val bgColor = Color(AndroidColor.parseColor(period.color))
    val animatedBg by animateColorAsState(
        targetValue = bgColor,
        label = "periodBlockColor",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = animatedBg,
        shadowElevation = if (isActive) 6.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = period.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColorFor(animatedBg),
            )
            Text(
                text = "${minutesToTimeString(period.startMinute)} - ${minutesToTimeString(period.endMinute)}",
                style = MaterialTheme.typography.labelSmall,
                color = contentColorFor(animatedBg).copy(alpha = 0.8f),
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            selected = true,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tasks.forEach { task ->
                        Text(
                            text = "• ${task.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColorFor(animatedBg).copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimePeriodBlockPreview() {
    TagoraTheme {
        TimePeriodBlock(
            period = TimePeriod(
                id = "1",
                name = "工作时间",
                startMinute = 480,
                endMinute = 720,
                color = "#FF6B9CE1",
                tagIds = listOf("1", "2"),
            ),
            tags = listOf(
                Tag(id = "1", name = "工作", color = "#FFE17055"),
                Tag(id = "2", name = "专注", color = "#FF52B788"),
            ),
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
