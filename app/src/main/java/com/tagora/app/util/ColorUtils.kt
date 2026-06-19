package com.tagora.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 根据背景颜色计算合适的前景文字颜色（黑色或白色）。
 * 使用 W3C 相对亮度算法。
 */
fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
