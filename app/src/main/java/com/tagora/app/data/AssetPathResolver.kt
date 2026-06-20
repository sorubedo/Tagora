package com.tagora.app.data

/**
 * 集中管理默认配置 asset 文件路径解析。
 *
 * @deprecated 请使用 [com.tagora.app.data.preset.PresetRegistry.getSelectedProvider]
 *             及 [com.tagora.app.data.preset.BuiltInAssetPresetProvider] 替代。
 *             此类保留仅用于向后兼容的回退路径。
 *
 * - [selectedPreset] 为 null 时，回退到根级 `default_<fileName>`（向后兼容）
 * - [selectedPreset] 非 null 时，解析为 `presets/<preset>/<fileName>`
 */
@Deprecated(
    message = "使用 PresetRegistry.getSelectedProvider() 及 BuiltInAssetPresetProvider 替代",
    replaceWith = ReplaceWith(
        "PresetRegistry.getSelectedProvider(prefs)",
        "com.tagora.app.data.preset.PresetRegistry",
    ),
)
object AssetPathResolver {

    private const val PRESETS_DIR = "presets"

    /**
     * 根据预设解析 asset 路径。
     *
     * @param preset 预设 key，null 表示使用根级默认文件
     * @param fileName 简单文件名，如 "tags.json"、"periods.json"
     * @return 完整的 asset 路径，如 "presets/semester/tags.json" 或 "default_tags.json"
     */
    fun resolve(preset: String?, fileName: String): String {
        return if (preset != null) {
            "$PRESETS_DIR/$preset/$fileName"
        } else {
            "default_$fileName"
        }
    }
}
