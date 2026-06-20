package com.tagora.app.data.preset

import android.net.Uri

/**
 * 预设插件契约常量。
 *
 * 定义宿主 App 与第三方预设提供者插件之间的通信协议：
 * - 插件通过 Service + intent-filter 声明自己提供预设数据
 * - 插件在 Service 的 meta-data 中指明其 ContentProvider authority
 * - 宿主 App 通过 ContentProvider 查询元数据、文件列表和文件内容
 *
 * ## 插件 App 需要实现的内容：
 *
 * ### 1. AndroidManifest.xml
 * ```xml
 * <service android:name=".PresetDiscoveryService"
 *          android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.tagora.action.PROVIDE_PRESETS" />
 *     </intent-filter>
 *     <meta-data android:name="preset_authority"
 *                android:value="${applicationId}.presets" />
 * </service>
 *
 * <provider android:name=".PresetContentProvider"
 *           android:authorities="${applicationId}.presets"
 *           android:exported="true"
 *           android:permission="com.tagora.permission.PROVIDE_PRESETS" />
 * ```
 *
 * ### 2. ContentProvider URI 响应规范
 * - `content://{authority}/metadata`  → 返回 PresetMetadata 的 JSON
 * - `content://{authority}/files`     → 返回 `List<String>` 的 JSON（文件名列表）
 * - `content://{authority}/files/{name}` → 返回文件原始内容（文本）
 */
object PresetPluginContract {

    /** 插件发现 Intent Action */
    const val ACTION_PROVIDE_PRESETS = "com.tagora.action.PROVIDE_PRESETS"

    /** Service meta-data 中声明 provider authority 的 key */
    const val META_KEY_AUTHORITY = "preset_authority"

    /** 宿主 App 定义的签名级权限（插件 Provider 必须受此权限保护） */
    const val PERMISSION_PROVIDE_PRESETS = "com.tagora.permission.PROVIDE_PRESETS"

    /** Provider authority 后缀（完整 authority = {packageName}.presets） */
    const val AUTHORITY_SUFFIX = ".presets"

    // ── URI 路径 ──────────────────────────────────────────────────

    /** 预设元数据端点 */
    const val PATH_METADATA = "metadata"

    /** 文件列表端点 */
    const val PATH_FILES = "files"

    /**
     * 构建插件 ContentProvider 的 authority。
     * @param packageName 插件 App 的包名
     */
    fun buildAuthority(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"

    /**
     * 构建 ContentProvider URI。
     * @param authority provider authority
     * @param path URI 路径（不含前导 "/"），如 "metadata"、"files/tags.json"
     */
    fun buildUri(authority: String, path: String): Uri =
        Uri.parse("content://$authority/$path")
}
