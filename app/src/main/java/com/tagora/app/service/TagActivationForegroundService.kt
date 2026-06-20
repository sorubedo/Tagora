package com.tagora.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tagora.app.MainActivity
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.domain.engine.TagActivationEngine
import com.tagora.app.domain.engine.TaskEvent
import com.tagora.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 标签激活前台服务
 *
 * 在后台持续运行 [TagActivationEngine]，通过状态栏通知告知用户。
 * 引擎通过 [RepositoryProvider] 获取全局单例，与 MainScreen 共享同一实例。
 *
 * 启动/停止由 [AppPreferences.isBackgroundRunningEnabled] 控制，
 * 设置页开关即时响应。
 */
class TagActivationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // 支持通过 action 停止服务
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // 检查设置开关，若关闭则停止自身
        val prefs = AppPreferences(this)
        if (!prefs.isBackgroundRunningEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        val engine = RepositoryProvider.getActivationEngine(this)

        // 先启动引擎（如已运行则为 no-op），再读取当前激活标签数构建通知
        engine.start()
        val notification = buildNotification(engine.activeTagIds.value.size)

        // Android 14+ 需要明确指定 foregroundServiceType
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 监听激活标签变化，更新通知内容
        serviceScope.launch {
            engine.activeTagIds.collectLatest { tagIds ->
                updateNotification(tagIds.size)
            }
        }

        // 监听任务状态变化，发送任务通知
        serviceScope.launch {
            engine.taskEvents.collect { event ->
                when (event) {
                    is TaskEvent.Activated -> {
                        if (prefs.isNotificationTaskActivatedEnabled) {
                            NotificationHelper.sendTaskActivatedNotification(
                                this@TagActivationForegroundService, event.task
                            )
                        }
                    }
                    is TaskEvent.TimedOut -> {
                        if (prefs.isNotificationTaskTimeoutEnabled) {
                            NotificationHelper.sendTaskTimeoutNotification(
                                this@TagActivationForegroundService, event.task
                            )
                        }
                    }
                    is TaskEvent.Completed -> {
                        if (prefs.isNotificationTaskCompletedEnabled) {
                            NotificationHelper.sendTaskCompletedNotification(
                                this@TagActivationForegroundService, event.task
                            )
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 注意：不在此处停止引擎，因为应用可能仍在前台。
        // 引擎生命周期由 MainScreen 的 DisposableEffect 管理：
        // - 若用户已关闭"后台运行"，MainScreen 离开组合时会停止引擎
        // - 若用户仍开启"后台运行"，引擎应继续运行
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Android 15+ 前台服务超时回调。
     * dataSync 类型每 24 小时限运行 6 小时，超时后系统调用此方法。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    // ── 通知相关 ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "标签激活检测",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示标签自动激活检测的运行状态"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(activeTagCount: Int): Notification {
        val text = if (activeTagCount > 0) {
            "当前激活 $activeTagCount 个标签"
        } else {
            "当前无活跃标签"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("标签激活检测运行中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or (
                        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
                        ),
                )
            )
            .build()
    }

    private fun updateNotification(activeTagCount: Int) {
        val notification = buildNotification(activeTagCount)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "tag_activation"
        private const val NOTIFICATION_ID = 1

        /** 通过此 action 停止服务 */
        const val ACTION_STOP = "com.tagora.app.action.STOP_SERVICE"

        /**
         * 启动服务（检查设置开关）。
         * 若用户已启用后台运行，启动前台服务；否则不启动。
         */
        fun startIfEnabled(context: Context) {
            val prefs = AppPreferences(context)
            if (prefs.isBackgroundRunningEnabled) {
                val intent = Intent(context, TagActivationForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /**
         * 停止服务。
         */
        fun stop(context: Context) {
            val intent = Intent(context, TagActivationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent) // 发送停止指令
        }
    }
}
