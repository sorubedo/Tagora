package com.tagora.app.data.model

/**
 * 配置预设类型枚举。
 *
 * 每种预设对应 [assets/presets/{key}/] 下的一组默认 JSON 配置文件。
 * 切换预设后，通过"重置为默认"功能应用新的默认配置。
 */
enum class PresetType(val key: String, val displayName: String) {
    /** 通用预设：基础日/周时段，无学期周次 */
    GENERAL("general", "通用"),

    /** 学生学期课程预设：完整课程表 + 学期周次 */
    SEMESTER("semester", "学生学期课程");

    companion object {
        /** 根据存储的 key 查找预设类型，未找到时默认返回 GENERAL */
        fun fromKey(key: String?): PresetType =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}
