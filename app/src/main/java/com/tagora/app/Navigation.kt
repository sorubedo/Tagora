package com.tagora.app

import android.content.SharedPreferences
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.tagora.app.data.AppPreferences
import com.tagora.app.ui.main.MainScreen
import com.tagora.app.ui.settings.AboutPage
import com.tagora.app.ui.settings.SettingsPage
import com.tagora.app.ui.settings.ThemeSettingsPage
import com.tagora.app.ui.settings.WebDavSettingsPage
import com.tagora.app.ui.task.TaskConditionPage
import com.tagora.app.ui.task.TaskDetailPage
import com.tagora.app.ui.task.TaskManagePage
import com.tagora.app.ai.ui.AiDebugPage
import com.tagora.app.ui.debug.DebugTagActivationPage
import com.tagora.app.ui.timeperiod.TagDetailPage
import com.tagora.app.ui.timeperiod.TagManagePage
import com.tagora.app.ui.timeperiod.TimePeriodDetailPage
import com.tagora.app.ui.timeperiod.TimelinePage
import com.tagora.app.ui.timeperiod.TimePeriodListFullPage

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val context = LocalContext.current
  val prefs = remember { AppPreferences(context.applicationContext) }

  // 动画偏好，通过 SharedPreferences 监听器实时响应变更
  var fadeEnabled by remember { mutableStateOf(prefs.isFadeTransitionEnabled) }
  var predictiveEnabled by remember { mutableStateOf(prefs.isPredictiveBackEnabled) }

  DisposableEffect(prefs) {
      val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
          when (key) {
              AppPreferences.KEY_FADE_TRANSITION -> fadeEnabled = prefs.isFadeTransitionEnabled
              AppPreferences.KEY_PREDICTIVE_BACK -> predictiveEnabled = prefs.isPredictiveBackEnabled
          }
      }
      prefs.registerListener(listener)
      onDispose { prefs.unregisterListener(listener) }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    transitionSpec = {
        val enter = slideInHorizontally { it } + scaleIn(initialScale = 0.7f)
        val exit = slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f)
        (if (fadeEnabled) enter + fadeIn() else enter) togetherWith
            (if (fadeEnabled) exit + fadeOut() else exit)
    },
    popTransitionSpec = {
        val enter = slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f)
        val exit = slideOutHorizontally { it }
        (if (fadeEnabled) enter + fadeIn() else enter) togetherWith
            (if (fadeEnabled) exit + fadeOut() else exit)
    },
    predictivePopTransitionSpec = {
        if (predictiveEnabled) {
            val enter = slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f)
            val exit = slideOutHorizontally { it }
            (if (fadeEnabled) enter + fadeIn() else enter) togetherWith
                (if (fadeEnabled) exit + fadeOut() else exit)
        } else {
            (if (fadeEnabled) fadeIn() else scaleIn(initialScale = 1f)) togetherWith
                (if (fadeEnabled) fadeOut() else scaleOut(targetScale = 1f))
        }
    },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Settings> {
          SettingsPage(
            onBack = { backStack.removeLastOrNull() },
            onWebDavSettings = { backStack.add(WebDavSettings) },
            onThemeSettings = { backStack.add(ThemeSettings) },
            onAbout = { backStack.add(About) },
            onDebugTagActivation = if (BuildConfig.DEBUG) {
              { backStack.add(DebugTagActivation) }
            } else null,
            onAiDebug = if (BuildConfig.DEBUG) {
              { backStack.add(AiDebug) }
            } else null,
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Timeline> {
          TimelinePage(
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TimePeriodList> {
          TimePeriodListFullPage(
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TimePeriodDetail> { key ->
          TimePeriodDetailPage(
            periodId = key.periodId,
            type = key.type,
            onBack = { backStack.removeLastOrNull() },
            onTagManage = { backStack.add(TagManage) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TagManage> {
          TagManagePage(
            onBack = { backStack.removeLastOrNull() },
            onTagEdit = { tagId -> backStack.add(TagDetail(tagId = tagId)) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TagDetail> { key ->
          TagDetailPage(
            tagId = key.tagId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TaskManage> {
          TaskManagePage(
            onBack = { backStack.removeLastOrNull() },
            onTaskEdit = { taskId -> backStack.add(TaskDetail(taskId = taskId)) },
            onConditionEdit = { taskId -> backStack.add(TaskConditionEdit(taskId = taskId)) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TaskDetail> { key ->
          TaskDetailPage(
            taskId = key.taskId,
            onBack = { backStack.removeLastOrNull() },
            onConditionEdit = { taskId -> backStack.add(TaskConditionEdit(taskId = taskId)) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<TaskConditionEdit> { key ->
          TaskConditionPage(
            taskId = key.taskId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<CompletedTasks> {
            com.tagora.app.ui.task.CompletedTasksPage(
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier.safeDrawingPadding(),
            )
        }
        entry<WebDavSettings> {
          WebDavSettingsPage(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<ThemeSettings> {
          ThemeSettingsPage(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<About> {
          AboutPage(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        if (BuildConfig.DEBUG) {
          entry<DebugTagActivation> {
            DebugTagActivationPage(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<AiDebug> {
            AiDebugPage(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
        }
      },
  )
}
