package com.tagora.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Dashboard 专用语义颜色常量。
 * 统一管理任务状态、时间段分类等场景的颜色，避免硬编码。
 */
object DashboardColors {
    /** 激活/完成状态 — 绿色 */
    val ActiveGreen = Color(0xFF4CAF50)

    /** 死线/超时状态 — 粉色 */
    val DeadlinePink = Color(0xFFE91E63)

    /** 当天任务 — 橙色 */
    val DailyOrange = Color(0xFFFF9800)

    /** 明日任务 — 青色 */
    val TomorrowCyan = Color(0xFF00BCD4)

    /** 本周任务 — 蓝色 */
    val WeeklyBlue = Color(0xFF2196F3)
}
