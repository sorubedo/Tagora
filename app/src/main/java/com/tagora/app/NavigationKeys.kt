package com.tagora.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Timeline : NavKey
@Serializable data object TimePeriodList : NavKey
@Serializable data class TimePeriodDetail(val periodId: String? = null, val type: String = "daily") : NavKey
@Serializable data object TagManage : NavKey
@Serializable data class TagDetail(val tagId: String? = null) : NavKey
@Serializable data object TaskManage : NavKey
@Serializable data class TaskDetail(val taskId: String? = null) : NavKey
@Serializable data class TaskConditionEdit(val taskId: String) : NavKey
@Serializable data object CompletedTasks : NavKey
@Serializable data object DebugTagActivation : NavKey
@Serializable data object WebDavSettings : NavKey
@Serializable data object ThemeSettings : NavKey
