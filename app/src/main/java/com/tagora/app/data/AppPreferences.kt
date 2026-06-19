package com.tagora.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化存储
 *
 * 管理后台运行、开机自启动等用户偏好设置。
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否启用后台运行（ForegroundService） */
    var isBackgroundRunningEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_RUNNING, value).apply()

    /** 是否启用开机自启动（BootReceiver） */
    var isAutoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    /** 任务激活时是否发送通知 */
    var isNotificationTaskActivatedEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_TASK_ACTIVATED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_TASK_ACTIVATED, value).apply()

    /** 任务超时时是否发送通知 */
    var isNotificationTaskTimeoutEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_TASK_TIMEOUT, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_TASK_TIMEOUT, value).apply()

    /** 任务自动完成时是否发送通知 */
    var isNotificationTaskCompletedEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_TASK_COMPLETED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_TASK_COMPLETED, value).apply()

    /** WebDAV 配置（JSON 序列化字符串） */
    var webDavConfigJson: String
        get() = prefs.getString(KEY_WEBDAV_CONFIG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBDAV_CONFIG, value).apply()

    /** 是否启用预测性返回手势 */
    var isPredictiveBackEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREDICTIVE_BACK, true)
        set(value) = prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, value).apply()

    /** 是否启用页面切换淡入淡出动画 */
    var isFadeTransitionEnabled: Boolean
        get() = prefs.getBoolean(KEY_FADE_TRANSITION, true)
        set(value) = prefs.edit().putBoolean(KEY_FADE_TRANSITION, value).apply()

    /**
     * 当前选择的配置预设 key。
     * null 表示未选择预设，使用根级 `default_*.json`（向后兼容）。
     * 非 null 时对应 `presets/{key}/` 下的默认配置文件。
     */
    var selectedPreset: String?
        get() = prefs.getString(KEY_SELECTED_PRESET, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_PRESET, value).apply()

    /**
     * 注册偏好变更监听器。
     * 返回取消监听的 Runnable。
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        const val KEY_BACKGROUND_RUNNING = "background_running"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_NOTIFICATION_TASK_ACTIVATED = "notification_task_activated"
        const val KEY_NOTIFICATION_TASK_TIMEOUT = "notification_task_timeout"
        const val KEY_NOTIFICATION_TASK_COMPLETED = "notification_task_completed"
        const val KEY_WEBDAV_CONFIG = "webdav_config"
        const val KEY_PREDICTIVE_BACK = "predictive_back"
        const val KEY_FADE_TRANSITION = "fade_transition"
        const val KEY_SELECTED_PRESET = "selected_preset"
    }
}
