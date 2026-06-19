package com.tagora.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 多选 Chip 组件。
 *
 * 从 TimePeriodDetailPage 的星期选择器、标签选择器中提取。
 *
 * @param options 选项标签列表
 * @param selectedIds 当前选中的 ID 集合
 * @param idSelector 从选项提取唯一 ID
 * @param labelSelector 从选项提取显示标签
 * @param onSelectionChange 选中状态变化回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiSelectChipGroup(
    options: List<T>,
    selectedIds: Set<String>,
    idSelector: (T) -> String,
    labelSelector: (T) -> String,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val id = idSelector(option)
            val isSelected = id in selectedIds
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectionChange(
                        if (isSelected) selectedIds - id
                        else selectedIds + id
                    )
                },
                label = { Text(labelSelector(option)) },
            )
        }
    }
}
