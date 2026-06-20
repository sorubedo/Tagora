package com.tagora.app.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.tagora.app.CompletedTasks
import com.tagora.app.Schedule
import com.tagora.app.Settings
import com.tagora.app.TagManage
import com.tagora.app.TaskDetail
import com.tagora.app.TaskManage
import com.tagora.app.Timeline
import com.tagora.app.TimePeriodList
import com.tagora.app.ai.executor.AiSheetExecutor
import com.tagora.app.ai.parser.AiJsonParser
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.ConfigDocumentsProvider
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.service.TagActivationForegroundService
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = RepositoryProvider.getActivationEngine(context)

    val prefs = remember { AppPreferences(context.applicationContext) }

    // AI 表单相关状态
    var pendingAiPaste by remember { mutableStateOf(false) }
    var showAiPasteDialog by remember { mutableStateOf(false) }
    var aiPasteJson by remember { mutableStateOf("") }
    val aiExecutor = remember {
        AiSheetExecutor(
            periodRepo = RepositoryProvider.get(context),
            taskRepo = RepositoryProvider.getTaskRepo(context),
            completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(context),
        )
    }

    // 引擎生命周期：MainScreen 存在期间一直运行。
    DisposableEffect(Unit) {
        engine.start()
        TagActivationForegroundService.startIfEnabled(context)
        onDispose {
            if (!prefs.isBackgroundRunningEnabled) {
                engine.stop()
            }
        }
    }

    // 从后台恢复时立即刷新激活标签；检测 AI 粘贴返回
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                engine.refresh()
                if (pendingAiPaste) {
                    pendingAiPaste = false
                    aiPasteJson = ""
                    showAiPasteDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val viewModel: DashboardViewModel = viewModel {
        DashboardViewModel(
            engine = engine,
            taskRepo = RepositoryProvider.getTaskRepo(context),
            repository = RepositoryProvider.get(context),
            completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(context),
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onPageSelect = { scope.launch { drawerState.close() } },
                onTimelineClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(Timeline)
                },
                onTimePeriodListClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(TimePeriodList)
                },
                onTaskListClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(TaskManage)
                },
                onScheduleClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(Schedule)
                },
                onTagManageClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(TagManage)
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(Settings)
                },
                onCompletedTasksClick = {
                    scope.launch { drawerState.close() }
                    onNavigate(CompletedTasks)
                },
                onDeepSeekClick = {
                    scope.launch { drawerState.close() }
                    // 确保提示词文件是最新的
                    ConfigDocumentsProvider.refreshPromptFile(context)
                    val file = File(context.filesDir, "ai_prompt.md")
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        pendingAiPaste = true
                        context.startActivity(Intent.createChooser(shareIntent, "发送提示词到 AI"))
                    }
                },
            )
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("控制面板") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "菜单",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                DashboardContent(
                    activeTagIds = uiState.activeTagIds,
                    dailyTagIds = uiState.dailyTagIds,
                    dailyPeriodCount = uiState.dailyPeriodCount,
                    weeklyTagIds = uiState.weeklyTagIds,
                    weeklyPeriodCount = uiState.weeklyPeriodCount,
                    tomorrowTagIds = uiState.tomorrowTagIds,
                    deadlineTagIds = uiState.deadlineTagIds,
                    tasks = uiState.tasks,
                    allTags = uiState.allTags,
                    tagsMap = uiState.allTags.associateBy { it.id },
                    onTaskClick = { taskId ->
                        onNavigate(TaskDetail(taskId = taskId))
                    },
                    onTaskComplete = { taskId ->
                        viewModel.completeTask(taskId)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // AI 操作粘贴对话框
    if (showAiPasteDialog) {
        AlertDialog(
            onDismissRequest = { showAiPasteDialog = false },
            title = { Text("粘贴 AI 操作表单") },
            text = {
                Column {
                    Text(
                        "将 AI 生成的 JSON 操作表单粘贴到下方：",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiPasteJson,
                        onValueChange = { aiPasteJson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("在此粘贴 JSON 操作表单...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val sheet = AiJsonParser.parse(aiPasteJson)
                                aiExecutor.execute(sheet)
                            } catch (_: Exception) {
                                // 解析或执行错误，静默关闭对话框
                            }
                            showAiPasteDialog = false
                        }
                    },
                    enabled = aiPasteJson.isNotBlank(),
                ) {
                    Text("执行")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiPasteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
