package com.tagora.app.ui.timeperiod

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tagora.app.TimePeriodDetail
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.ui.timeperiod.components.DayTimeline
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey

/**
 * 时间线独立页面（从抽屉进入，与 CompletedTasks 同级）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePage(
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = RepositoryProvider.get(context)
    val engine = RepositoryProvider.getActivationEngine(context)
    val taskRepo = RepositoryProvider.getTaskRepo(context)
    val viewModel: TimePeriodViewModel = viewModel {
        TimePeriodViewModel(repository, engine, taskRepo)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时间线") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when (val s = state) {
            TimePeriodUiState.Loading -> { /* 短暂加载由 Flow 驱动 */ }
            is TimePeriodUiState.Error -> {
                Text(
                    text = "加载失败：${s.throwable.message}",
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is TimePeriodUiState.Success -> {
                DayTimeline(
                    periods = s.periodsSorted,
                    tags = s.tagsMap,
                    activeTagIds = s.activeTagIds,
                    tasks = s.tasks,
                    onPeriodClick = { period ->
                        onNavigate(TimePeriodDetail(periodId = period.id))
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                )
            }
        }
    }
}
