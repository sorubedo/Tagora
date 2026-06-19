package com.tagora.app.ui.timeperiod.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.tagora.app.util.contentColorFor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.theme.TagoraTheme
import com.tagora.app.ui.timeperiod.TimePeriodViewModel
import com.tagora.app.util.newId

/** 15 种预设颜色，参考 RikkaHub 的 Material You 调色板 */
val PresetPeriodColors = listOf(
    "#FFF2C94C", // amber
    "#FFE17055", // coral
    "#FF52B788", // sage
    "#FF6B9CE1", // sky blue
    "#FF9B51E0", // purple
    "#FFF07B72", // salmon
    "#FF3DD6D0", // teal
    "#FFFFA726", // orange
    "#FF66BB6A", // green
    "#FF42A5F5", // blue
    "#FFAB47BC", // medium purple
    "#FFEC407A", // pink
    "#FF8D6E63", // brown
    "#FF78909C", // blue grey
    "#FF546E7A", // dark blue grey
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimePeriodEditor(
    period: TimePeriod?,
    allTags: List<Tag>,
    onSave: (TimePeriod) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateNewTag: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    var name by remember(period) { mutableStateOf(period?.name ?: "") }
    var startHour by remember(period) {
        mutableIntStateOf(period?.startMinute?.div(60) ?: 8)
    }
    var startMinute by remember(period) {
        mutableIntStateOf(period?.startMinute?.rem(60) ?: 0)
    }
    var endHour by remember(period) {
        mutableIntStateOf(period?.endMinute?.div(60) ?: 12)
    }
    var endMinute by remember(period) {
        mutableIntStateOf(period?.endMinute?.rem(60) ?: 0)
    }
    var selectedColor by remember(period) {
        mutableStateOf(period?.color ?: PresetPeriodColors[0])
    }
    var selectedTagIds by remember(period) {
        mutableStateOf(period?.tagIds?.toSet() ?: emptySet())
    }
    var showCustomColor by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (period != null) "编辑时间段" else "新建时间段",
                style = MaterialTheme.typography.titleLarge,
            )

            // 名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // 时间选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = "${startHour.toString().padStart(2, '0')}:${startMinute.toString().padStart(2, '0')}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("开始") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "选择开始时间",
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(showStartTimePicker) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (upEvent != null) {
                                    showStartTimePicker = true
                                }
                            }
                        },
                    singleLine = true,
                )
                Text("—", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = "${endHour.toString().padStart(2, '0')}:${endMinute.toString().padStart(2, '0')}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("结束") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "选择结束时间",
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(showEndTimePicker) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (upEvent != null) {
                                    showEndTimePicker = true
                                }
                            }
                        },
                    singleLine = true,
                )
            }

            // 颜色选择
            Text("颜色", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetPeriodColors.forEach { colorHex ->
                    val color = Color(AndroidColor.parseColor(colorHex))
                    val isSelected = selectedColor == colorHex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { selectedColor = colorHex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选",
                                modifier = Modifier.size(18.dp),
                                tint = contentColorFor(color),
                            )
                        }
                    }
                }
            }

            // 自定义颜色输入
            TextButton(onClick = { showCustomColor = !showCustomColor }) {
                Text(if (showCustomColor) "隐藏自定义颜色" else "自定义颜色...")
            }
            if (showCustomColor) {
                OutlinedTextField(
                    value = customColorHex,
                    onValueChange = { hex ->
                        customColorHex = hex
                        if (hex.length == 9 && hex.startsWith("#")) {
                            try {
                                AndroidColor.parseColor(hex)
                                selectedColor = hex
                            } catch (_: IllegalArgumentException) { /* 无效颜色值，忽略 */ }
                        }
                    },
                    label = { Text("#FFRRGGBB") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            HorizontalDivider()

            // 标签选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("标签", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onCreateNewTag) {
                    Text("+ 新建标签")
                }
            }
            if (allTags.isEmpty()) {
                Text(
                    "暂无标签，点击上方新建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    allTags.forEach { tag ->
                        val isSelected = tag.id in selectedTagIds
                        TagChip(
                            tag = tag,
                            selected = isSelected,
                            onClick = {
                                selectedTagIds = if (isSelected) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val id = period?.id ?: newId()
                        val startMin = startHour * 60 + startMinute
                        val endMin = endHour * 60 + endMinute
                        onSave(
                            TimePeriod(
                                id = id,
                                name = name.ifBlank { "未命名" },
                                startMinute = startMin,
                                endMinute = endMin,
                                color = selectedColor,
                                tagIds = selectedTagIds.toList(),
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存")
                }
            }

            if (period != null) {
                OutlinedButton(
                    onClick = { onDelete(period.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除此时间段")
                }
            }
        }
    }

    // 开始时间选择器
    if (showStartTimePicker) {
        val startTimeState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("选择开始时间") },
            text = { TimePicker(state = startTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    startHour = startTimeState.hour
                    startMinute = startTimeState.minute
                    showStartTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("取消") }
            },
        )
    }

    // 结束时间选择器
    if (showEndTimePicker) {
        val endTimeState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("选择结束时间") },
            text = { TimePicker(state = endTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    endHour = endTimeState.hour
                    endMinute = endTimeState.minute
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("取消") }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimePeriodEditorPreview() {
    TagoraTheme {
        // 预览用占位
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TimePeriodEditor 预览请在设备上查看")
        }
    }
}
