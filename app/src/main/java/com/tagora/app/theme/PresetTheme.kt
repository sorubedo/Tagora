package com.tagora.app.theme

import androidx.compose.material3.ColorScheme
import com.tagora.app.theme.presets.DefaultTheme
import com.tagora.app.theme.presets.ForestTheme
import com.tagora.app.theme.presets.MonochromeTheme
import com.tagora.app.theme.presets.OceanTheme
import com.tagora.app.theme.presets.SunsetTheme

/**
 * 预设主题数据模型。
 *
 * 每个预设主题包含一套完整的亮色和暗色 ColorScheme，
 * 供用户在关闭动态颜色时选择使用。
 */
data class PresetTheme(
    val id: String,
    val name: String,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
) {
    /** 根据暗色模式返回对应的 ColorScheme */
    fun getColorScheme(dark: Boolean): ColorScheme =
        if (dark) darkScheme else lightScheme
}

/** 所有预设主题列表，按注册顺序排列 */
val PresetThemes: List<PresetTheme> = listOf(
    DefaultTheme,
    OceanTheme,
    ForestTheme,
    SunsetTheme,
    MonochromeTheme,
)

/** 根据 ID 查找预设主题，未找到时回退到默认主题 */
fun findPresetThemeById(id: String): PresetTheme =
    PresetThemes.firstOrNull { it.id == id } ?: DefaultTheme
