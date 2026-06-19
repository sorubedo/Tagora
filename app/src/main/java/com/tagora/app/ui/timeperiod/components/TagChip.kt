package com.tagora.app.ui.timeperiod.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.Tag
import com.tagora.app.theme.TagoraTheme
import com.tagora.app.util.contentColorFor

/**
 * 参照 RikkaHub Tag 组件精确对齐
 * - 药丸形 RoundedCornerShape(50)
 * - 未选中：tertiaryContainer 背景（RikkaHub DEFAULT 类型）
 * - 选中：自定义颜色背景
 * - padding: horizontal=6.dp, vertical=1.dp
 */
@Composable
fun TagChip(
    tag: Tag,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val tagColor = Color(AndroidColor.parseColor(tag.color))
    val bgColor = if (selected) tagColor else MaterialTheme.colorScheme.tertiaryContainer
    val textColor = if (selected) contentColorFor(tagColor)
        else MaterialTheme.colorScheme.onTertiaryContainer

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color = bgColor, shape = RoundedCornerShape(50))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
            Text(
                text = tag.name,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onDelete != null) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除标签",
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .padding(1.dp),
                tint = textColor,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TagChipPreview() {
    TagoraTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            TagChip(
                tag = Tag(id = "1", name = "工作", color = "#FFE17055"),
                modifier = Modifier.padding(end = 8.dp),
            )
            TagChip(
                tag = Tag(id = "2", name = "学习", color = "#FF52B788"),
                selected = true,
                modifier = Modifier.padding(end = 8.dp),
            )
            TagChip(
                tag = Tag(id = "3", name = "运动", color = "#FF6B9CE1"),
                onDelete = {},
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
