package com.tagora.app.ui.timeperiod

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.PeriodType
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.typeEnum
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.ClickableReadOnlyField
import com.tagora.app.ui.components.FormItem
import com.tagora.app.ui.components.MultiSelectChipGroup
import com.tagora.app.ui.components.TopAppBarWithBack
import com.tagora.app.ui.components.rememberUnsavedChangesGuard
import com.tagora.app.util.dayOfWeekLabels
import com.tagora.app.util.newId
import com.tagora.app.ui.timeperiod.components.ColorPicker
import com.tagora.app.ui.timeperiod.components.PresetPeriodColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// 将 LocalDate 转换为 auto-saveable 类型的辅助方法
private fun LocalDate.toSaveable(): Long = toEpochDay()
private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimePeriodDetailPage(
    periodId: String?,
    type: String = "daily",
    onBack: () -> Unit,
    onTagManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = RepositoryProvider.get(context)
    val isWeekly = type == "weekly"
    val isDate = type == "date"
    val isDeadline = type == "deadline"

    // 编辑状态（rememberSaveable 确保导航到 TagManage 返回后不丢失）
    // 非 saveable 类型转为 auto-saveable 类型：LocalDate→Long, Set→List
    var name by rememberSaveable { mutableStateOf("") }
    // 日时段字段
    var startHour by rememberSaveable { mutableIntStateOf(8) }
    var startMin by rememberSaveable { mutableIntStateOf(0) }
    var endHour by rememberSaveable { mutableIntStateOf(12) }
    var endMin by rememberSaveable { mutableIntStateOf(0) }
    // 周时段字段（List<Int> 是 auto-saveable）
    var selectedDayOfWeeks by rememberSaveable { mutableStateOf(listOf(1)) }
    // 日期段字段（Long 是 auto-saveable，存储 epochDay）
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toSaveable()) }
    var endDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(6).toSaveable()) }
    // 死线字段
    var deadlineDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toSaveable()) }
    // 通用
    var selectedColor by rememberSaveable { mutableStateOf(PresetPeriodColors[0]) }
    // selectedTagIds 存储为 List<String>（auto-saveable）
    var selectedTagIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var loaded by rememberSaveable { mutableStateOf(false) }

    // 保存初始状态用于比对（rememberSaveable 保证返回后不丢失）
    var initialName by rememberSaveable { mutableStateOf("") }
    var initialStartHour by rememberSaveable { mutableIntStateOf(8) }
    var initialStartMin by rememberSaveable { mutableIntStateOf(0) }
    var initialEndHour by rememberSaveable { mutableIntStateOf(12) }
    var initialEndMin by rememberSaveable { mutableIntStateOf(0) }
    var initialDayOfWeeks by rememberSaveable { mutableStateOf(listOf(1)) }
    var initialStartDate by rememberSaveable { mutableStateOf(LocalDate.now().toSaveable()) }
    var initialEndDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(6).toSaveable()) }
    var initialDeadlineDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toSaveable()) }
    var initialColor by rememberSaveable { mutableStateOf(PresetPeriodColors[0]) }
    var initialTagIds by rememberSaveable { mutableStateOf(listOf<String>()) }

    // 日期选择器弹窗状态
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    // 时间选择器弹窗状态
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    // 加载数据并初始化编辑状态
    LaunchedEffect(Unit) {
        // 始终加载最新标签列表（以获取 TagManage 中新建的标签）
        allTags = repository.tagsFlow.first()

        // 只在首次进入时从仓库加载初始数据，防止从 TagManage 返回后覆盖编辑
        if (!loaded) {
            val periods = when {
                isDeadline -> repository.deadlinePeriodsFlow.first()
                isDate -> repository.datePeriodsFlow.first()
                isWeekly -> repository.weeklyPeriodsFlow.first()
                else -> repository.periodsFlow.first()
            }
            val period = periods.find { it.id == periodId }
            if (period != null) {
                name = period.name
                when {
                    isDeadline -> {
                        period.endDate?.let { deadlineDate = LocalDate.parse(it).toSaveable() }
                    }
                    isDate -> {
                        period.startDate?.let { startDate = LocalDate.parse(it).toSaveable() }
                        period.endDate?.let { endDate = LocalDate.parse(it).toSaveable() }
                    }
                    isWeekly -> {
                        selectedDayOfWeeks = period.dayOfWeeks.ifEmpty { listOf(1) }
                    }
                    else -> {
                        startHour = period.startMinute / 60
                        startMin = period.startMinute % 60
                        endHour = period.endMinute / 60
                        endMin = period.endMinute % 60
                    }
                }
                selectedColor = period.color
                selectedTagIds = period.tagIds
            }
            loaded = true
            // 保存初始状态
            initialName = name
            initialStartHour = startHour
            initialStartMin = startMin
            initialEndHour = endHour
            initialEndMin = endMin
            initialDayOfWeeks = selectedDayOfWeeks
            initialStartDate = startDate
            initialEndDate = endDate
            initialColor = selectedColor
            initialTagIds = selectedTagIds
            initialDeadlineDate = deadlineDate
        }
    }

    // 是否有未保存的变更
    val hasChanges = loaded && (
        name != initialName ||
        (isDeadline && deadlineDate != initialDeadlineDate) ||
        (isDate && (startDate != initialStartDate || endDate != initialEndDate)) ||
        (isWeekly && selectedDayOfWeeks != initialDayOfWeeks) ||
        (!isWeekly && !isDate && !isDeadline && (startHour != initialStartHour || startMin != initialStartMin ||
            endHour != initialEndHour || endMin != initialEndMin)) ||
        selectedColor != initialColor ||
        selectedTagIds != initialTagIds
    )

    val isNew = periodId == null

    val triggerUnsavedGuard = rememberUnsavedChangesGuard(
        hasChanges = hasChanges,
        title = "放弃更改？",
        message = "有未保存的修改，确定要返回吗？",
        onDiscard = onBack,
    )

    // 处理返回：有未保存变更时拦截
    fun handleBack() {
        if (hasChanges) triggerUnsavedGuard() else {
            onBack()
        }
    }

    val pageTitle = when {
        isNew && isDeadline -> "新建死线时间段"
        isNew && isDate -> "新建日期时间段"
        isNew && isWeekly -> "新建周时间段"
        isNew -> "新建日时间段"
        isDeadline -> "编辑死线时间段"
        isDate -> "编辑日期时间段"
        isWeekly -> "编辑周时间段"
        else -> "编辑日时间段"
    }

    Scaffold(
        topBar = {
            TopAppBarWithBack(
                title = pageTitle,
                onBack = onBack,
                hasUnsavedChanges = hasChanges,
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
                    label = { Text("名称") },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                when {
                                    isDeadline -> "例如：期末作业截止、项目交付"
                                    isDate -> "例如：第1周、三月第一周"
                                    isWeekly -> "例如：周一、工作日"
                                    else -> "例如：晨间、工作时间"
                                }
                            )
                        },
                    )
                }
            }

            // 时间范围 — 根据类型展示不同表单
            when {
                isDeadline -> {
                    // 死线：仅截止日期
                    CardGroup(title = { Text("截止日期") }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "在此之前标签始终激活，无起始时间限制",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            ClickableReadOnlyField(
                                value = deadlineDate.toLocalDate().toString(),
                                label = "截止日期",
                                icon = Icons.Filled.DateRange,
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                isDate -> {
                    // 日期段：日期范围选择器
                    CardGroup(title = { Text("日期范围") }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "选择时间段的起止日期",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ClickableReadOnlyField(
                                    value = startDate.toLocalDate().toString(),
                                    label = "开始日期",
                                    icon = Icons.Filled.DateRange,
                                    onClick = { showStartDatePicker = true },
                                    modifier = Modifier.weight(1f),
                                )
                                ClickableReadOnlyField(
                                    value = endDate.toLocalDate().toString(),
                                    label = "结束日期",
                                    icon = Icons.Filled.DateRange,
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                isWeekly -> {
                    // 周时段：星期多选
                    CardGroup(title = { Text("星期") }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "可选多个，如选周一~周五表示工作日",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            MultiSelectChipGroup(
                                options = dayOfWeekLabels.mapIndexed { index, label -> index + 1 to label },
                                selectedIds = selectedDayOfWeeks.map { it.toString() }.toSet(),
                                idSelector = { (day, _) -> day.toString() },
                                labelSelector = { (_, label) -> label },
                                onSelectionChange = { newIds ->
                                    selectedDayOfWeeks = newIds.map { it.toInt() }
                                },
                            )
                        }
                    }
                }
                else -> {
                    // 日时段：时间选择器
                    CardGroup(title = { Text("时间范围") }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ClickableReadOnlyField(
                                value = "${startHour.toString().padStart(2, '0')}:${startMin.toString().padStart(2, '0')}",
                                label = "开始",
                                icon = Icons.Filled.Schedule,
                                onClick = { showStartTimePicker = true },
                                modifier = Modifier.weight(1f),
                            )
                            ClickableReadOnlyField(
                                value = "${endHour.toString().padStart(2, '0')}:${endMin.toString().padStart(2, '0')}",
                                label = "结束",
                                icon = Icons.Filled.Schedule,
                                onClick = { showEndTimePicker = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 颜色
            CardGroup(title = { Text("颜色") }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ColorPicker(
                        colors = PresetPeriodColors,
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it },
                    )
                }
            }

            // 标签
            CardGroup(title = { Text("标签") }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        allTags.forEach { tag ->
                            val isSelected = tag.id in selectedTagIds
                            InputChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagIds = if (isSelected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text(tag.name) },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "移除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else null,
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            tonalElevation = 2.dp,
                            onClick = onTagManage,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "新建标签",
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (allTags.isEmpty()) {
                        Text(
                            "暂无标签，点击 + 创建",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 保存按钮
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    // 日期段校验：结束日期不能早于开始日期
                    if (isDate && endDate < startDate) {
                        Toast.makeText(context, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        val id = periodId ?: newId()
                        val period = TimePeriod(
                            id = id,
                            name = name.ifBlank { "未命名" },
                            type = type,
                            startMinute = if (isWeekly || isDate || isDeadline) 0 else startHour * 60 + startMin,
                            endMinute = if (isWeekly || isDate || isDeadline) 0 else endHour * 60 + endMin,
                            color = selectedColor,
                            tagIds = selectedTagIds.toList(),
                            dayOfWeeks = if (isWeekly) selectedDayOfWeeks.sorted() else emptyList(),
                            startDate = if (isDate) startDate.toLocalDate().toString() else null,
                            endDate = if (isDate) endDate.toLocalDate().toString()
                                else if (isDeadline) deadlineDate.toLocalDate().toString() else null,
                        )

                        when {
                            isDeadline -> {
                                val periods = repository.deadlinePeriodsFlow.first()
                                val newPeriods = if (isNew) periods + period
                                else periods.map { if (it.id == id) period else it }
                                repository.saveDeadlinePeriods(newPeriods)
                            }
                            isDate -> {
                                val periods = repository.datePeriodsFlow.first()
                                val newPeriods = if (isNew) periods + period
                                else periods.map { if (it.id == id) period else it }
                                repository.saveDatePeriods(newPeriods)
                            }
                            isWeekly -> {
                                val periods = repository.weeklyPeriodsFlow.first()
                                val newPeriods = if (isNew) periods + period
                                else periods.map { if (it.id == id) period else it }
                                repository.saveWeeklyPeriods(newPeriods)
                            }
                            else -> {
                                val periods = repository.periodsFlow.first()
                                val newPeriods = if (isNew) periods + period
                                else periods.map { if (it.id == id) period else it }
                                repository.savePeriods(newPeriods)
                            }
                        }
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
                            when {
                                isDeadline -> {
                                    val periods = repository.deadlinePeriodsFlow.first()
                                    repository.saveDeadlinePeriods(periods.filter { it.id != periodId })
                                }
                                isDate -> {
                                    val periods = repository.datePeriodsFlow.first()
                                    repository.saveDatePeriods(periods.filter { it.id != periodId })
                                }
                                isWeekly -> {
                                    val periods = repository.weeklyPeriodsFlow.first()
                                    repository.saveWeeklyPeriods(periods.filter { it.id != periodId })
                                }
                                else -> {
                                    val periods = repository.periodsFlow.first()
                                    repository.savePeriods(periods.filter { it.id != periodId })
                                }
                            }
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除此时间段")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // 开始日期选择器
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toSaveable()
                    }
                    showStartDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 结束/截止日期选择器
    if (showEndDatePicker) {
        val pickerInitialDate = if (isDeadline) deadlineDate.toLocalDate() else endDate.toLocalDate()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pickerInitialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toSaveable()
                        if (isDeadline) deadlineDate = picked else endDate = picked
                    }
                    showEndDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 开始时间选择器
    if (showStartTimePicker) {
        val startTimeState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMin,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("选择开始时间") },
            text = { TimePicker(state = startTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    startHour = startTimeState.hour
                    startMin = startTimeState.minute
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
            initialMinute = endMin,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("选择结束时间") },
            text = { TimePicker(state = endTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    endHour = endTimeState.hour
                    endMin = endTimeState.minute
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("取消") }
            },
        )
    }

}

