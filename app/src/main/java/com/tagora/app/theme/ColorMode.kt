package com.tagora.app.theme

/**
 * 应用颜色模式。
 *
 * 控制亮色/暗色主题的切换策略。
 */
enum class ColorMode {
    /** 跟随系统设置 */
    SYSTEM,

    /** 强制亮色模式 */
    LIGHT,

    /** 强制暗色模式 */
    DARK;

    companion object {
        /** 根据字符串解析 ColorMode，无法识别时回退到 SYSTEM */
        fun fromString(value: String): ColorMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
