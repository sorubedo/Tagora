package com.tagora.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 通用 JSON 文件仓库基类。
 *
 * 封装了"从 filesDir 读取 JSON → MutableStateFlow 暴露 → 保存时同时写文件和更新 Flow"的通用模式。
 * 子类只需指定文件名、序列化器和默认 asset 资源名即可。
 *
 * @param T 持久化的顶层数据类型（如 TaskConfig、TagConfig、PeriodConfig）
 */
open class JsonFileRepository<T>(
    protected val context: Context,
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
    private val defaultAssetName: String = fileName,
) {
    private val file get() = File(context.filesDir, fileName)

    private val _flow = MutableStateFlow<T?>(null)

    /** 响应式数据流（过滤掉初始 null） */
    val flow: Flow<T> = _flow.filterNotNull()

    /** 当前值（仅在 init 后有效） */
    val value: T get() = _flow.value ?: error("JsonFileRepository($fileName) 尚未初始化")

    init {
        val data = if (file.exists()) {
            try {
                json.decodeFromString(serializer, file.readText())
            } catch (e: Exception) {
                Log.w("JsonFileRepo", "无法加载 $fileName，回退到默认配置", e)
                loadAndWriteDefault()
            }
        } else {
            loadAndWriteDefault()
        }
        _flow.value = data
    }

    /** 保存数据（同时写文件和更新 Flow） */
    internal suspend fun save(data: T) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(serializer, data))
        _flow.value = data
    }

    /** 读取默认配置并写入文件（首次启动或数据损坏时）。子类可覆写以定制回退行为。 */
    internal open fun loadAndWriteDefault(): T = loadAndWriteDefault(defaultAssetName)

    /**
     * 从指定 asset 文件读取默认配置并写入 filesDir。
     * 用于预设切换时加载不同预设的默认配置。
     *
     * @param assetName 完整的 asset 路径，如 "presets/semester/tags.json"
     */
    internal open fun loadAndWriteDefault(assetName: String): T {
        val text = context.assets.open(assetName).bufferedReader().readText()
        val data = json.decodeFromString(serializer, text)
        file.writeText(json.encodeToString(serializer, data))
        return data
    }
}
