package com.tagora.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tagora.app.data.AppPreferences
import com.tagora.app.theme.ColorMode
import com.tagora.app.theme.TagoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val prefs = remember { AppPreferences(applicationContext) }

            // 主题偏好状态，通过 SharedPreferences 监听器实时响应变更
            var colorMode by remember { mutableStateOf(ColorMode.fromString(prefs.colorMode)) }
            var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
            var themeId by remember { mutableStateOf(prefs.themeId) }
            var amoledDark by remember { mutableStateOf(prefs.isAmoledDarkEnabled) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        AppPreferences.KEY_COLOR_MODE -> colorMode = ColorMode.fromString(prefs.colorMode)
                        AppPreferences.KEY_DYNAMIC_COLOR -> dynamicColor = prefs.isDynamicColorEnabled
                        AppPreferences.KEY_THEME_ID -> themeId = prefs.themeId
                        AppPreferences.KEY_AMOLED_DARK -> amoledDark = prefs.isAmoledDarkEnabled
                    }
                }
                prefs.registerListener(listener)
                onDispose { prefs.unregisterListener(listener) }
            }

            TagoraTheme(
                colorMode = colorMode,
                dynamicColor = dynamicColor,
                themeId = themeId,
                amoledDark = amoledDark,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainNavigation()
                }
            }
        }
    }
}
