package com.tagora.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.tagora.app.CompletedTasks
import com.tagora.app.Settings
import com.tagora.app.TagManage
import com.tagora.app.TaskDetail
import com.tagora.app.TaskManage
import com.tagora.app.Timeline
import com.tagora.app.TimePeriodList
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.service.TagActivationForegroundService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = RepositoryProvider.getActivationEngine(context)

    val prefs = remember { AppPreferences(context.applicationContext) }

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

    // 从后台恢复时立即刷新激活标签
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                engine.refresh()
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
}
