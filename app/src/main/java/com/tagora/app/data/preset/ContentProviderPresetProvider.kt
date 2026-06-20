package com.tagora.app.data.preset

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 外部 ContentProvider 预设配置提供者。
 *
 * 通过 ContentProvider URI 从第三方插件 App 获取预设数据。
 * 插件 App 的 ContentProvider 需遵循 [PresetPluginContract] 规范。
 *
 * ## URI 约定
 * - `content://{authority}/metadata`  → 返回 [PresetMetadata] JSON
 * - `content://{authority}/files`     → 返回 `List<String>` JSON
 * - `content://{authority}/files/{name}` → 返回文件内容
 *
 * @param context Android Context（自动转为 applicationContext 避免泄漏）
 * @param authority 插件 ContentProvider 的 authority
 */
class ContentProviderPresetProvider(
    context: Context,
    private val authority: String,
) : PresetProvider {

    private val contentResolver: ContentResolver =
        context.applicationContext.contentResolver

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 从 ContentProvider 延迟加载元数据 */
    override val metadata: PresetMetadata by lazy {
        runCatching {
            readFromProvider(PresetPluginContract.PATH_METADATA)
                .let { json.decodeFromString<PresetMetadata>(it) }
        }.getOrElse { e ->
            Log.w(TAG, "无法从 $authority 加载元数据", e)
            PresetMetadata(
                id = "plugin:unknown",
                name = "未知插件预设",
                description = "无法加载预设描述",
                author = "未知",
                version = 0,
                minAppVersion = Int.MAX_VALUE,
                source = "plugin",
            )
        }
    }

    /**
     * 列出插件提供的所有配置文件。
     * 如果 ContentProvider 响应异常，返回空列表并记录日志。
     */
    override suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            readFromProvider(PresetPluginContract.PATH_FILES)
                .let { json.decodeFromString<List<String>>(it) }
        }.getOrElse { e ->
            Log.w(TAG, "无法从 $authority 获取文件列表", e)
            emptyList()
        }
    }

    /**
     * 读取指定文件内容。
     * @throws IOException 如果 ContentProvider 无响应或文件不存在
     */
    override suspend fun readFile(fileName: String): String = withContext(Dispatchers.IO) {
        val path = "${PresetPluginContract.PATH_FILES}/$fileName"
        readFromProvider(path)
    }

    /**
     * 从 ContentProvider 读取指定路径的内容。
     * 优先使用 openInputStream 流式读取（避免 cursor 大小限制）。
     */
    private fun readFromProvider(path: String): String {
        val uri = PresetPluginContract.buildUri(authority, path)
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("无法从 ContentProvider 读取: $uri")
    }

    companion object {
        private const val TAG = "ContentProviderPreset"
    }
}
