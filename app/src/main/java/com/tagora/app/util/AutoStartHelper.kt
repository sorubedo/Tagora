package com.tagora.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * 厂商自启动管理页面跳转工具
 *
 * 各厂商对后台运行有严格的电源管理限制，需要引导用户到厂商特定设置页面
 * 开启自启动权限。每个厂商跳转均包裹 try-catch，防止 ActivityNotFoundException。
 */
object AutoStartHelper {

    private const val TAG = "AutoStartHelper"

    /**
     * 尝试跳转到厂商自启动管理页面。
     * 如果无法匹配厂商或跳转失败，降级到应用详情设置页。
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        try {
            val success = when {
                // 小米 / 红米 / POCO / 黑鲨
                manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                    manufacturer.contains("redmi") || brand.contains("redmi") -> {
                    tryOpen(context, "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")
                        || tryOpen(context, "com.miui.securitycenter",
                            "com.miui.powercenter.PowerSettings")
                }

                // 华为 / 荣耀
                manufacturer.contains("huawei") || brand.contains("huawei") ||
                    brand.contains("honor") || manufacturer.contains("honor") -> {
                    tryOpen(context, "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")
                        || tryOpen(context, "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                        || tryOpen(context, "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity")
                }

                // OPPO
                manufacturer.contains("oppo") || brand.contains("oppo") -> {
                    tryOpen(context, "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                        || tryOpen(context, "com.oppo.safe",
                            "com.oppo.safe.permission.startup.StartupAppListActivity")
                        || tryOpen(context, "com.coloros.oppoguardelf",
                            "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity")
                }

                // VIVO / iQOO
                manufacturer.contains("vivo") || brand.contains("vivo") -> {
                    tryOpen(context, "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                        || tryOpen(context, "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                        || tryOpen(context, "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                }

                // 一加
                manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                    tryOpen(context, "com.oneplus.security",
                        "com.oneplus.security.selfstart.SelfStartActivity")
                }

                // 魅族
                manufacturer.contains("meizu") || brand.contains("meizu") -> {
                    tryOpen(context, "com.meizu.safe",
                        "com.meizu.safe.permission.SmartBGActivity")
                }

                // 三星
                manufacturer.contains("samsung") || brand.contains("samsung") -> {
                    tryOpen(context, "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity")
                }

                else -> false
            }

            if (!success) {
                // 降级：打开应用详情页
                openAppDetails(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "跳转自启动设置失败", e)
            openAppDetails(context)
        }
    }

    /**
     * 尝试打开指定 ComponentName。
     * @return true 如果成功打开
     */
    private fun tryOpen(context: Context, packageName: String, className: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 降级方案：打开应用详情设置页
     */
    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // 最终兜底：打开系统设置
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Log.e(TAG, "无法打开任何设置页面")
            }
        }
    }
}
