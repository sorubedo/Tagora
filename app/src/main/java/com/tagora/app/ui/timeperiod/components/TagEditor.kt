package com.tagora.app.ui.timeperiod.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tagora.app.util.contentColorFor
import com.tagora.app.data.model.Tag
import com.tagora.app.theme.TagoraTheme
import com.tagora.app.ui.timeperiod.TimePeriodViewModel
import com.tagora.app.util.newId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    tag: Tag?,
    onSave: (Tag) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    allTagIds: List<String> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState()

    var name by remember(tag) { mutableStateOf(tag?.name ?: "") }
    var selectedColor by remember(tag) {
        mutableStateOf(tag?.color ?: PresetPeriodColors[0])
    }
    var idInput by remember(tag) { mutableStateOf(tag?.id ?: newId()) }
    var idError by remember { mutableStateOf<String?>(null) }
    var showCustomColor by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("") }

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
                text = if (tag != null) "编辑标签" else "新建标签",
                style = MaterialTheme.typography.titleLarge,
            )

            // 标签 ID
            val isNew = tag == null
            Text(
                text = if (isNew) "新建时可自定义 ID，保存后不可修改" else "编辑已有标签时 ID 不可修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = idInput,
                onValueChange = { newValue ->
                    idInput = newValue
                    idError = null
                },
                label = { Text("标签 ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = !isNew,
                isError = idError != null,
                supportingText = idError?.let { { Text(it) } },
                placeholder = { Text("例如：t-class-13、t-w21") },
            )

            // 标签名
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("标签名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

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

            // 自定义颜色
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
                        // 新建时校验 ID 唯一性
                        if (isNew && idInput in allTagIds) {
                            idError = "此 ID 已被占用，请使用其他 ID"
                            return@Button
                        }
                        onSave(
                            Tag(
                                id = idInput,
                                name = name.ifBlank { "未命名" },
                                color = selectedColor,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank() && idInput.isNotBlank(),
                ) {
                    Text("保存")
                }
            }

            if (tag != null) {
                OutlinedButton(
                    onClick = { onDelete(tag.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除此标签")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TagEditorPreview() {
    TagoraTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TagEditor 预览请在设备上查看")
        }
    }
}
