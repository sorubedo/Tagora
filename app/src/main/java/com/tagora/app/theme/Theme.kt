package com.tagora.app.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmoledDarkBackground = Color(0xFF000000)

@Composable
fun TagoraTheme(
    colorMode: ColorMode = ColorMode.SYSTEM,
    dynamicColor: Boolean = true,
    themeId: String = "default",
    amoledDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val theme = findPresetThemeById(themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }

    val finalScheme = if (darkTheme && amoledDark) {
        colorScheme.copy(background = AmoledDarkBackground, surface = AmoledDarkBackground)
    } else {
        colorScheme
    }

    // 更新状态栏和导航栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = finalScheme, typography = Typography, content = content)
}
