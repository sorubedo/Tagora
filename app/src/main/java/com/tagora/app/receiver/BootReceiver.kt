package com.tagora.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tagora.app.data.AppPreferences
import com.tagora.app.service.TagActivationForegroundService

/**
 * 开机自启动广播接收器
 *
 * 在设备开机完成后，检查用户设置，若两者均启用则启动前台服务。
 * 默认禁用（android:enabled="false"），由设置页开关动态启用。
 *
 * 监听广播：
 * - ACTION_BOOT_COMPLETED：设备完成开机
 * - ACTION_LOCKED_BOOT_COMPLETED：设备开机但尚未解锁（Android 7.0+）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val prefs = AppPreferences(context)

            // 两个开关都启用时才启动服务
            if (prefs.isAutoStartEnabled && prefs.isBackgroundRunningEnabled) {
                val serviceIntent = Intent(context, TagActivationForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
