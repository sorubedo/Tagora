package com.tagora.app.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tagora.app.BuildConfig
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.preset.PresetRegistry
import com.tagora.app.domain.usecase.CleanupUnusedUseCase
import com.tagora.app.domain.usecase.ResetToDefaultUseCase
import com.tagora.app.service.TagActivationForegroundService
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.ui.components.SettingsItem
import com.tagora.app.ui.components.SwitchSettingsItem
import com.tagora.app.util.AutoStartHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onBack: () -> Unit,
    onWebDavSettings: () -> Unit,
    onThemeSettings: () -> Unit,
    onAbout: () -> Unit,
    onDebugTagActivation: (() -> Unit)?,
    onAiDebug: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = RepositoryProvider.get(context)
    val taskRepo = RepositoryProvider.getTaskRepo(context)
    val completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(context)

    var showResetDialog by remember { mutableStateOf(false) }
    var showWeekDatePicker by remember { mutableStateOf(false) }

    // 后台运行设置
    val prefs = remember { AppPreferences(context.applicationContext) }
    var backgroundRunning by remember { mutableStateOf(prefs.isBackgroundRunningEnabled) }
    var autoStart by remember { mutableStateOf(prefs.isAutoStartEnabled) }

    // 通知设置
    var notificationActivated by remember { mutableStateOf(prefs.isNotificationTaskActivatedEnabled) }
    var notificationTimeout by remember { mutableStateOf(prefs.isNotificationTaskTimeoutEnabled) }
    var notificationCompleted by remember { mutableStateOf(prefs.isNotificationTaskCompletedEnabled) }

    // 交互设置
    var predictiveBack by remember { mutableStateOf(prefs.isPredictiveBackEnabled) }
    var fadeTransition by remember { mutableStateOf(prefs.isFadeTransitionEnabled) }

    // 配置预设
    var currentPreset by remember { mutableStateOf(prefs.selectedPreset) }
    var showPresetSwitchDialog by remember { mutableStateOf(false) }
    var pendingProviderId by remember { mutableStateOf<String?>(null) }
    var pendingProviderName by remember { mutableStateOf("") }
    val resetUseCase = remember { ResetToDefaultUseCase(repository, taskRepo, completedTaskRepo, context) }

    // 旧 key 规范化：将 "general"/"semester" 映射为新的 ID 格式
    fun normalizePresetKey(key: String?): String? = when (key) {
        null -> null
        "general" -> "builtin:general"
        "semester" -> "builtin:semester"
        else -> key
    }

    // 通知权限请求（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            backgroundRunning = true
            prefs.isBackgroundRunningEnabled = true
            TagActivationForegroundService.startIfEnabled(context)
        } else {
            Toast.makeText(context, "需要通知权限才能后台运行", Toast.LENGTH_SHORT).show()
        }
    }

    // 从现有数据中读取当前第一周第一天
    var weekFirstDate by remember { mutableStateOf("2026-03-02") }
    LaunchedEffect(Unit) {
        val datePeriods = repository.datePeriodsFlow.first()
        val week1 = datePeriods.find { it.tagIds.contains("t-w1") }
        weekFirstDate = week1?.startDate ?: "2026-03-02"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val tasks = taskRepo.tasksFlow.first()
                val completedTasks = completedTaskRepo.completedTasksFlow.first()
                repository.exportAllToUri(it, tasks, completedTasks)
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val result = repository.importAllFromUri(it)
                    taskRepo.saveTasks(result.tasks)
                    completedTaskRepo.saveCompletedTasks(result.completedTasks)
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 返回")
                    }
                },
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
            // 后台运行
            CardGroup(title = { Text("后台运行") }) {
                // 后台运行开关
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "后台运行",
                        description = "在状态栏显示通知，持续检测时间段匹配",
                        checked = backgroundRunning,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Android 13+ 需要通知权限
                                if (Build.VERSION.SDK_INT >= 33) {
                                    if (ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@SwitchSettingsItem
                                    }
                                }
                                backgroundRunning = true
                                prefs.isBackgroundRunningEnabled = true
                                TagActivationForegroundService.startIfEnabled(context)
                            } else {
                                backgroundRunning = false
                                prefs.isBackgroundRunningEnabled = false
                                TagActivationForegroundService.stop(context)
                            }
                        },
                    )
                }
                // 开机自启动开关
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "开机自启动",
                        description = "设备重启后自动恢复后台检测",
                        checked = autoStart,
                        onCheckedChange = { checked ->
                            autoStart = checked
                            prefs.isAutoStartEnabled = checked
                            // 动态启用/禁用 BootReceiver
                            val receiver = ComponentName(context, com.tagora.app.receiver.BootReceiver::class.java)
                            context.packageManager.setComponentEnabledSetting(
                                receiver,
                                if (checked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP,
                            )
                        },
                    )
                }
                // 电池优化
                CardGroupItem(
                    onClick = {
                        val powerManager = context.getSystemService(PowerManager::class.java)
                        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                            // 已豁免，跳转到电池优化设置页
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // 请求豁免
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // 降级：打开电池优化设置页
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    isLast = false,
                ) {
                    SettingsItem(
                        label = "电池优化",
                        description = "关闭省电限制，防止后台服务被系统暂停",
                    )
                }
                // 自启动管理（厂商页面）
                CardGroupItem(
                    onClick = { AutoStartHelper.openAutoStartSettings(context) },
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "自启动管理",
                        description = "前往系统设置，允许应用在设备启动时自动运行",
                    )
                }
            }

            // 通知设置
            CardGroup(title = { Text("通知设置") }) {
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "任务激活通知",
                        description = "标签条件满足时发送通知",
                        checked = notificationActivated,
                        onCheckedChange = { checked ->
                            notificationActivated = checked
                            prefs.isNotificationTaskActivatedEnabled = checked
                        },
                    )
                }
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "任务超时通知",
                        description = "任务超时时发送通知",
                        checked = notificationTimeout,
                        onCheckedChange = { checked ->
                            notificationTimeout = checked
                            prefs.isNotificationTaskTimeoutEnabled = checked
                        },
                    )
                }
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "任务完成通知",
                        description = "固定事件自动完成时发送通知",
                        checked = notificationCompleted,
                        onCheckedChange = { checked ->
                            notificationCompleted = checked
                            prefs.isNotificationTaskCompletedEnabled = checked
                        },
                    )
                }
                CardGroupItem(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // 降级：打开应用详情页
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "通知管理",
                        description = "前往系统设置管理通知渠道",
                    )
                }
            }

            // 交互设置
            CardGroup(title = { Text("交互") }) {
                CardGroupItem(isLast = false) {
                    SwitchSettingsItem(
                        label = "预测性返回手势",
                        description = "从屏幕边缘滑动时可预览上一页动画。重启应用后生效",
                        checked = predictiveBack,
                        onCheckedChange = { checked ->
                            predictiveBack = checked
                            prefs.isPredictiveBackEnabled = checked
                        },
                    )
                }
                CardGroupItem(isLast = true) {
                    SwitchSettingsItem(
                        label = "淡入淡出动画",
                        description = "页面切换时伴随透明度渐变效果。重启应用后生效",
                        checked = fadeTransition,
                        onCheckedChange = { checked ->
                            fadeTransition = checked
                            prefs.isFadeTransitionEnabled = checked
                        },
                    )
                }
            }

            // 外观设置
            CardGroup(title = { Text("外观") }) {
                CardGroupItem(
                    onClick = onThemeSettings,
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "主题设置",
                        description = "颜色模式、动态颜色、预设主题",
                    )
                }
            }

            // 关于
            CardGroup(title = { Text("关于") }) {
                CardGroupItem(
                    onClick = onAbout,
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "关于",
                        description = "版本信息、项目链接与开源许可",
                    )
                }
            }

            // 配置预设
            val allProviders = remember { PresetRegistry.getAllProviders() }
            val builtinProviders = allProviders.filter { it.metadata.source == "builtin" }
            val pluginProviders = allProviders.filter { it.metadata.source == "plugin" }

            // 内置预设
            CardGroup(title = { Text("内置预设") }) {
                builtinProviders.forEachIndexed { index, provider ->
                    val metadata = provider.metadata
                    CardGroupItem(
                        onClick = {
                            if (normalizePresetKey(currentPreset) != metadata.id) {
                                pendingProviderId = metadata.id
                                pendingProviderName = metadata.name
                                showPresetSwitchDialog = true
                            }
                        },
                        isLast = index == builtinProviders.lastIndex && pluginProviders.isEmpty(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metadata.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = metadata.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = normalizePresetKey(currentPreset) == metadata.id,
                                onClick = null, // CardGroupItem onClick 处理
                            )
                        }
                    }
                }
            }

            // 插件预设（仅在有外部插件时显示）
            if (pluginProviders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                CardGroup(title = { Text("插件预设") }) {
                    pluginProviders.forEachIndexed { index, provider ->
                        val metadata = provider.metadata
                        CardGroupItem(
                            onClick = {
                                if (normalizePresetKey(currentPreset) != metadata.id) {
                                    pendingProviderId = metadata.id
                                    pendingProviderName = metadata.name
                                    showPresetSwitchDialog = true
                                }
                            },
                            isLast = index == pluginProviders.lastIndex,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = metadata.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = metadata.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "作者：${metadata.author}  |  版本：${metadata.version}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                RadioButton(
                                    selected = normalizePresetKey(currentPreset) == metadata.id,
                                    onClick = null,
                                )
                            }
                        }
                    }
                }
            }

            // 数据管理
            CardGroup(title = { Text("数据管理") }) {
                CardGroupItem(
                    onClick = {
                        // 通过 SAF 打开配置文件夹供外部编辑器修改
                        val uri = DocumentsContract.buildRootUri(
                            "${context.packageName}.documents", "tagora_config"
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setData(uri)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "未找到支持 SAF 的文件管理器", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isLast = false,
                ) {
                    SettingsItem(label = "打开配置文件夹", description = "在文件管理器中浏览和编辑所有配置文件")
                }
                CardGroupItem(
                    onClick = { exportLauncher.launch("time_periods.json") },
                    isLast = false,
                ) {
                    SettingsItem(label = "导出 JSON", description = "将当前配置导出为 JSON 文件")
                }
                CardGroupItem(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    isLast = false,
                ) {
                    SettingsItem(label = "导入 JSON", description = "从 JSON 文件导入配置")
                }
                CardGroupItem(
                    onClick = onWebDavSettings,
                    isLast = false,
                ) {
                    SettingsItem(label = "WebDAV 备份", description = "配置 WebDAV 服务器，备份和恢复数据")
                }
                CardGroupItem(
                    onClick = {
                        scope.launch {
                            val useCase = CleanupUnusedUseCase(repository, taskRepo, context, prefs)
                            val result = useCase.execute()
                            if (result.isEmpty) {
                                Toast.makeText(context, "没有可清理的无用配置", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "已清理 ${result.removedTagCount} 个标签，${result.removedPeriodCount} 个时间段",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    isLast = false,
                ) {
                    SettingsItem(label = "清理无用配置", description = "删除未被任何任务引用的标签和空标签时间段，预设配置中的标签受保护")
                }
                CardGroupItem(
                    onClick = { showResetDialog = true },
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "重置为默认",
                        description = "恢复为默认时间段配置，所有自定义数据将丢失",
                        labelColor = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 周次设置
            CardGroup(title = { Text("周次设置") }) {
                CardGroupItem(
                    onClick = { showWeekDatePicker = true },
                    isLast = true,
                ) {
                    SettingsItem(
                        label = "第一周第一天",
                        description = "当前：$weekFirstDate，修改后将重置所有内置周段",
                    )
                }
            }

            // 调试功能（仅 debug 编译可见）
            if (BuildConfig.DEBUG) {
                CardGroup(title = { Text("调试") }) {
                    if (onDebugTagActivation != null) {
                        CardGroupItem(
                            onClick = onDebugTagActivation,
                            isLast = onAiDebug == null,
                        ) {
                            SettingsItem(
                                label = "标签激活测试",
                                description = "手动切换标签激活状态，测试任务条件逻辑",
                            )
                        }
                    }
                    if (onAiDebug != null) {
                        CardGroupItem(
                            onClick = onAiDebug,
                            isLast = true,
                        ) {
                            SettingsItem(
                                label = "AI 操作调试",
                                description = "输入 JSON 操作表单，执行标签/时间段/任务的批量增删查改",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        val resolvedId = normalizePresetKey(currentPreset)
        val presetDisplayName = if (resolvedId != null) {
            PresetRegistry.getProvider(resolvedId)?.metadata?.name ?: "默认"
        } else {
            "默认"
        }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置为默认配置？") },
            text = {
                Text("将使用「${presetDisplayName}」预设的默认配置覆盖当前所有自定义的时间段、标签和任务，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            resetUseCase.execute(includeTasks = true)
                            // 更新第一周日期显示
                            val datePeriods = repository.loadDefaultDatePeriods()
                            val week1 = datePeriods.find { it.tagIds.contains("t-w1") }
                            weekFirstDate = week1?.startDate ?: "2026-03-02"
                            Toast.makeText(context, "已重置为「${presetDisplayName}」预设", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsPage", "重置预设失败", e)
                            Toast.makeText(context, "重置失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showResetDialog = false
                }) {
                    Text("确认重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 预设切换确认对话框
    if (showPresetSwitchDialog && pendingProviderId != null) {
        AlertDialog(
            onDismissRequest = { showPresetSwitchDialog = false },
            title = { Text("切换配置预设？") },
            text = {
                Text("切换到「${pendingProviderName}」后，当前数据不会自动改变。\n\n如需应用新预设的默认配置，请点击「重置为默认」。")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.selectedPreset = pendingProviderId
                    currentPreset = pendingProviderId
                    showPresetSwitchDialog = false
                    Toast.makeText(context, "已切换到「${pendingProviderName}」预设", Toast.LENGTH_SHORT).show()
                }) {
                    Text("切换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetSwitchDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 第一周第一天日期选择器
    if (showWeekDatePicker) {
        val initialDate = try {
            LocalDate.parse(weekFirstDate)
        } catch (_: Exception) {
            LocalDate.of(2026, 3, 2)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showWeekDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        weekFirstDate = newDate.toString()
                        scope.launch {
                            repository.resetWeekPeriods(newDate.toString())
                            Toast.makeText(context, "周次已重置", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showWeekDatePicker = false
                }) { Text("确定并重置") }
            },
            dismissButton = {
                TextButton(onClick = { showWeekDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}


