package com.tagora.app.data

import android.content.Context
import com.tagora.app.domain.engine.TagActivationEngine

/**
 * 全局共享 Repository 单例
 * 确保所有页面使用同一个 Repository 实例
 * 从而共享同一个 MutableStateFlow，任何页面的修改都能即时反映到所有页面
 */
object RepositoryProvider {

    @Volatile
    private var instance: DefaultTimePeriodRepository? = null

    @Volatile
    private var taskInstance: DefaultTaskRepository? = null

    @Volatile
    private var activationEngineInstance: TagActivationEngine? = null

    @Volatile
    private var completedTaskInstance: DefaultCompletedTaskRepository? = null

    fun get(context: Context): DefaultTimePeriodRepository {
        return instance ?: synchronized(this) {
            instance ?: DefaultTimePeriodRepository(
                context.applicationContext,
                prefs = AppPreferences(context.applicationContext),
            ).also {
                instance = it
            }
        }
    }

    fun getTaskRepo(context: Context): DefaultTaskRepository {
        return taskInstance ?: synchronized(this) {
            taskInstance ?: DefaultTaskRepository(context.applicationContext).also {
                taskInstance = it
            }
        }
    }

    /**
     * 获取标签激活引擎单例。
     * 引擎依赖 TimePeriodRepository，因此通过 [get] 获取 repo 后传入。
     */
    fun getActivationEngine(context: Context): TagActivationEngine {
        return activationEngineInstance ?: synchronized(this) {
            activationEngineInstance ?: TagActivationEngine(
                get(context),
                getTaskRepo(context),
                getCompletedTaskRepo(context),
                context.applicationContext,
            ).also {
                activationEngineInstance = it
            }
        }
    }

    fun getCompletedTaskRepo(context: Context): DefaultCompletedTaskRepository {
        return completedTaskInstance ?: synchronized(this) {
            completedTaskInstance ?: DefaultCompletedTaskRepository(context.applicationContext).also {
                completedTaskInstance = it
            }
        }
    }
}
