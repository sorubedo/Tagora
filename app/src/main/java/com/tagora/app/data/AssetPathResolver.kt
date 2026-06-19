package com.tagora.app.data

/**
 * 集中管理默认配置 asset 文件路径解析。
 *
 * 所有需要读取默认配置文件的代码都应通过此类解析路径，
 * 以确保预设切换后能正确加载对应预设的 asset 文件。
 *
 * - [selectedPreset] 为 null 时，回退到根级 `default_<fileName>`（向后兼容）
 * - [selectedPreset] 非 null 时，解析为 `presets/<preset>/<fileName>`
 */
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
