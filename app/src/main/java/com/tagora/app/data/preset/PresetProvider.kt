package com.tagora.app.data.preset

import kotlinx.serialization.Serializable

/**
 * 预设配置提供者接口。
 *
 * 每个 PresetProvider 代表一个预设配置的数据来源：
 * - [BuiltInAssetPresetProvider]：内置预设，数据来自 app/assets
 * - [ContentProviderPresetProvider]：外部插件预设，数据来自第三方 App 的 ContentProvider
 *
 * 通过统一接口，宿主 App 无需关心预设数据的具体来源。
 */
interface PresetProvider {
    /** 预设描述信息 */
    val metadata: PresetMetadata

    /**
     * 列出该预设包含的所有配置文件名。
     * 文件名不含路径前缀，如 "tags.json"、"periods.json"。
     */
    suspend fun listFiles(): List<String>

    /**
     * 读取指定文件的内容（纯文本 / JSON 字符串）。
     *
     * @param fileName 简单文件名，如 "tags.json"
     * @return 文件原始文本内容
     */
    suspend fun readFile(fileName: String): String
}

/**
 * 预设元数据，描述一个预设配置提供者的身份和属性。
 *
 * @param id 唯一标识符。内置预设格式为 "builtin:{key}"（如 "builtin:general"），
 *           插件预设格式为 "plugin:{包名}:{自定义id}"
 * @param name 预设显示名称（中文）
 * @param description 预设简要说明
 * @param author 作者名称
 * @param version 预设数据版本号
 * @param minAppVersion 最低兼容的 Tagora 应用版本号
 * @param iconUrl 预设图标 URL（可选）
 * @param source 来源类型："builtin"（内置）或 "plugin"（外部插件）
 */
@Serializable
data class PresetMetadata(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: Int,
    val minAppVersion: Int,
    val iconUrl: String? = null,
    val source: String,
)
