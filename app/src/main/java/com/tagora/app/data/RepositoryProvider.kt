package com.tagora.app.data

import android.content.Context
import com.tagora.app.data.preset.PresetRegistry
import com.tagora.app.domain.engine.TagActivationEngine

/**
 * 全局共享 Repository 单例。
 * 使用单一的 [AppConfigRepository] 管理所有配置数据，
 * 同时实现 [TimePeriodRepository]、[TaskRepository]、[CompletedTaskRepository] 三个接口。
 */
object RepositoryProvider {

    @Volatile
    private var appConfigRepo: AppConfigRepository? = null

    @Volatile
    private var activationEngineInstance: TagActivationEngine? = null

    @Volatile
    private var prefs: AppPreferences? = null

    fun get(context: Context): AppConfigRepository {
        return appConfigRepo ?: synchronized(this) {
            ensureInitialized(context.applicationContext)
            appConfigRepo ?: AppConfigRepository(
                context.applicationContext,
                prefs = prefs,
            ).also {
                appConfigRepo = it
            }
        }
    }

    fun getTaskRepo(context: Context): AppConfigRepository = get(context)

    fun getCompletedTaskRepo(context: Context): AppConfigRepository = get(context)

    /**
     * 获取标签激活引擎单例。
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
