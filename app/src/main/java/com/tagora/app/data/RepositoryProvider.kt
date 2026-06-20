package com.tagora.app.data

import android.content.Context
import com.tagora.app.data.preset.PresetRegistry
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

    @Volatile
    private var prefs: AppPreferences? = null

    fun get(context: Context): DefaultTimePeriodRepository {
        return instance ?: synchronized(this) {
            // 确保 PresetRegistry 和 AppPreferences 初始化
            ensureInitialized(context.applicationContext)
            instance ?: DefaultTimePeriodRepository(
                context.applicationContext,
                prefs = prefs,
            ).also {
                instance = it
            }
        }
    }

    fun getTaskRepo(context: Context): DefaultTaskRepository {
        return taskInstance ?: synchronized(this) {
            ensureInitialized(context.applicationContext)
            taskInstance ?: DefaultTaskRepository(
                context.applicationContext,
                prefs = prefs,
            ).also {
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
            ensureInitialized(context.applicationContext)
            completedTaskInstance ?: DefaultCompletedTaskRepository(
                context.applicationContext,
                prefs = prefs,
            ).also {
                completedTaskInstance = it
            }
        }
    }

    /**
     * 获取 AppPreferences 单例。
     */
    fun getPrefs(context: Context): AppPreferences {
        ensureInitialized(context.applicationContext)
        return prefs!!
    }

    /**
     * 懒初始化 PresetRegistry 和 AppPreferences。
     * 幂等操作，多次调用仅首次生效。
     */
    private fun ensureInitialized(appContext: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = AppPreferences(appContext)
                    PresetRegistry.init(appContext)
                }
            }
        }
    }
}
