package com.tagora.app.ui.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tagora.app.data.AppPreferences
import com.tagora.app.theme.ColorMode
import com.tagora.app.theme.PresetTheme
import com.tagora.app.theme.PresetThemes
import com.tagora.app.theme.findPresetThemeById
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.ui.components.SwitchSettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context.applicationContext) }

    var selectedColorMode by remember { mutableStateOf(ColorMode.fromString(prefs.colorMode)) }
    var dynamicColorEnabled by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
    var selectedThemeId by remember { mutableStateOf(prefs.themeId) }
    var amoledDarkEnabled by remember { mutableStateOf(prefs.isAmoledDarkEnabled) }

    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isCurrentlyDark = when (selectedColorMode) {
        ColorMode.DARK -> true
        ColorMode.LIGHT -> false
        ColorMode.SYSTEM -> false // 主题预览使用亮色方案
    }
    val showAmoledSwitch = selectedColorMode != ColorMode.LIGHT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 返回")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 颜色模式 ──
            CardGroup(title = { Text("颜色模式") }) {
                ColorMode.entries.forEachIndexed { index, mode ->
                    val isLast = index == ColorMode.entries.lastIndex
                    val label = when (mode) {
                        ColorMode.SYSTEM -> "跟随系统"
                        ColorMode.LIGHT -> "亮色模式"
                        ColorMode.DARK -> "暗色模式"
                    }
                    val description = when (mode) {
                        ColorMode.SYSTEM -> "根据系统设置自动切换亮色/暗色主题"
                        ColorMode.LIGHT -> "始终使用亮色主题"
                        ColorMode.DARK -> "始终使用暗色主题"
                    }
                    CardGroupItem(
                        onClick = {
                            selectedColorMode = mode
                            prefs.colorMode = mode.name
                        },
                        isLast = isLast,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = selectedColorMode == mode,
                                onClick = null,
                            )
                        }
                    }
                }
            }

            // ── 动态颜色 ──
            if (supportsDynamicColor) {
                CardGroup(title = { Text("动态颜色") }) {
                    CardGroupItem(isLast = true) {
                        SwitchSettingsItem(
                            label = "动态颜色",
                            description = "从壁纸自动提取配色方案，仅 Android 12+ 可用",
                            checked = dynamicColorEnabled,
                            onCheckedChange = { checked ->
                                dynamicColorEnabled = checked
                                prefs.isDynamicColorEnabled = checked
                            },
                        )
                    }
                }
            }

            // ── 主题预设 ──
            if (!dynamicColorEnabled || !supportsDynamicColor) {
                CardGroup(title = { Text("主题预设") }) {
                    CardGroupItem(isLast = true) {
                        PresetThemeSelector(
                            selectedThemeId = selectedThemeId,
                            isDark = isCurrentlyDark,
                            onThemeSelected = { themeId ->
                                selectedThemeId = themeId
                                prefs.themeId = themeId
                            },
                        )
                    }
                }
            }

            // ── AMOLED 暗色模式 ──
            if (showAmoledSwitch) {
                CardGroup(title = { Text("AMOLED") }) {
                    CardGroupItem(isLast = true) {
                        SwitchSettingsItem(
                            label = "AMOLED 暗色模式",
                            description = "在暗色模式下使用纯黑背景，节省 OLED 屏幕电量",
                            checked = amoledDarkEnabled,
                            onCheckedChange = { checked ->
                                amoledDarkEnabled = checked
                                prefs.isAmoledDarkEnabled = checked
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 预设主题选择器 — 水平滚动的主题预览按钮组。
 *
 * 参照 RikkaHub 的 PresetThemeButtonGroup 实现。
 */
@Composable
private fun PresetThemeSelector(
    selectedThemeId: String,
    isDark: Boolean,
    onThemeSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PresetThemes.forEach { theme ->
            val isSelected = theme.id == selectedThemeId
            PresetThemePreviewButton(
                theme = theme,
                selected = isSelected,
                isDark = isDark,
                onClick = { onThemeSelected(theme.id) },
            )
        }
    }
}

/**
 * 单个预设主题预览按钮。
 *
 * 圆形颜色预览（三色分区 + 中心主色圆）+ 主题名称。
 */
@Composable
private fun PresetThemePreviewButton(
    theme: PresetTheme,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = theme.getColorScheme(dark = isDark)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp),
            ) {
                // 三色分区背景
                drawRect(
                    color = scheme.primaryContainer,
                    size = size,
                )
                drawRect(
                    color = scheme.secondaryContainer,
                    size = size,
                    topLeft = Offset(x = size.width / 2, y = 0f),
                )
                drawRect(
                    color = scheme.tertiaryContainer,
                    size = size,
                    topLeft = Offset(x = size.width / 2, y = size.height / 2),
                )
                // 中心主色圆
                drawCircle(
                    color = scheme.primary,
                    radius = if (selected) 14.dp.toPx() else 10.dp.toPx(),
                    center = Offset(x = size.width / 2, y = size.height / 2),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = contentColorFor(scheme.primary),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

