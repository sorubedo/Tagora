package com.tagora.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.ui.graphics.vector.ImageVector

/** 抽屉内的主页面选项 — 仅保留控制面板，其余已提升为独立 NavKey 页面 */
enum class MainPage(val label: String, val icon: ImageVector) {
    DASHBOARD("控制面板", Icons.Filled.SpaceDashboard),
}
