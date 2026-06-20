package com.tagora.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tagora.app.MainActivity
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.descriptionWithoutLocation
import com.tagora.app.data.model.locationText

/**
 * 通知管理工具
 *
 * 统一管理任务通知渠道的创建和通知发送。
 * 在应用启动时调用 [createChannels] 初始化渠道。
 */
object NotificationHelper {

    /** 任务激活通知渠道 */
    const val CHANNEL_TASK_ACTIVATED = "task_activated"

    /** 任务超时/完成通知渠道 */
    const val CHANNEL_TASK_TIMEOUT = "task_timeout"

    /**
     * 创建所有通知渠道（幂等操作）。
     * 应在 Application.onCreate() 或 Service.onCreate() 中调用。
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 任务激活渠道：高重要性，弹出横幅
        val activatedChannel = android.app.NotificationChannel(
            CHANNEL_TASK_ACTIVATED,
            "任务激活",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "当任务标签条件被满足时通知"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(activatedChannel)

        // 任务超时/完成渠道：默认重要性，静默显示
        val timeoutChannel = android.app.NotificationChannel(
            CHANNEL_TASK_TIMEOUT,
            "任务超时与完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "当任务超时或自动完成时通知"
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(timeoutChannel)
    }

    /**
     * 发送任务激活通知。
     * notificationId 基于 task.id 的 hashCode，同一任务多次激活会更新同一通知。
     */
    fun sendTaskActivatedNotification(context: Context, task: Task) {
        val notificationId = "activated_${task.id}".hashCode()
        val conditionDesc = describeCondition(task)

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_ACTIVATED)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("任务激活：「${task.name}」")
            .setContentText(conditionDesc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(conditionDesc))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildMainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * 发送任务超时通知。
     */
    fun sendTaskTimeoutNotification(context: Context, task: Task) {
        val notificationId = "timeout_${task.id}".hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_TIMEOUT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("任务超时：「${task.name}」")
            .setContentText("所需标签在未来不再出现，任务已自动超时")
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * 发送任务完成通知（fixed 类型自动完成）。
     */
    fun sendTaskCompletedNotification(context: Context, task: Task) {
        val notificationId = "completed_${task.id}".hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_TIMEOUT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("任务完成：「${task.name}」")
            .setContentText("固定事件已自动完成")
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    // ── 内部工具方法 ────────────────────────────────────────────────

    private fun buildMainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
                ),
        )
    }

    /**
     * 生成任务条件的简短中文描述。
     */
    private fun describeCondition(task: Task): String {
        return if (task.description.isNotBlank()) {
            val cleanDesc = task.descriptionWithoutLocation
            val locationPart = task.locationText?.let { " ($it)" } ?: ""
            "「$cleanDesc」$locationPart\n当前该任务的标签条件已满足"
        } else {
            "当前该任务的标签条件已满足"
        }
    }
}
