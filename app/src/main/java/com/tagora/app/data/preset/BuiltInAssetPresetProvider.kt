package com.tagora.app.data.preset

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置 Assets 预设配置提供者。
 *
 * 将 app/assets 下的预设目录封装为 [PresetProvider]，
 * 对外提供与外部插件一致的接口，上层代码无需区分数据来源。
 *
 * ## 数据来源
 * - [assetDir] 非空时：读取 `assets/{assetDir}/{fileName}`，如 `assets/presets/semester/tags.json`
 * - [assetDir] 为空时：读取 `assets/default_{fileName}`，如 `assets/default_tags.json`（根级回退）
 */
class BuiltInAssetPresetProvider(
    private val context: Context,
    override val metadata: PresetMetadata,
    private val assetDir: String,
) : PresetProvider {

    override suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        if (assetDir.isEmpty()) {
            // 根级：返回所有 default_*.json 文件，去掉 "default_" 前缀
            context.assets.list("")
                ?.filter { it.startsWith("default_") && it.endsWith(".json") }
                ?.map { it.removePrefix("default_") }
                ?.sorted()
                ?: emptyList()
        } else {
            context.assets.list(assetDir)
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                ?: emptyList()
        }
    }

    override suspend fun readFile(fileName: String): String = withContext(Dispatchers.IO) {
        val fullPath = if (assetDir.isEmpty()) {
            "default_$fileName"
        } else {
            "$assetDir/$fileName"
        }
        context.assets.open(fullPath).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val BUILTIN_ID_PREFIX = "builtin:"

        /**
         * 根级默认预设提供者（未选择任何预设时的回退）。
         * 对应 assets/ 根目录下的 `default_*.json` 文件。
         */
        fun defaultPreset(context: Context) = BuiltInAssetPresetProvider(
            context = context.applicationContext,
            metadata = PresetMetadata(
                id = "${BUILTIN_ID_PREFIX}default",
                name = "默认配置",
                description = "Tagora 基础默认配置，包含日常作息时段和通用标签",
                author = "Tagora",
                version = 1,
                minAppVersion = 1,
                source = "builtin",
            ),
            assetDir = "", // 空字符串 → 读取根级 default_*.json
        )

        /**
         * 通用预设提供者。
         * 对应 `assets/presets/general/` 目录。
         */
        fun generalPreset(context: Context) = BuiltInAssetPresetProvider(
            context = context.applicationContext,
            metadata = PresetMetadata(
                id = "${BUILTIN_ID_PREFIX}general",
                name = "通用",
                description = "基础日/周时段，不含学期周次",
                author = "Tagora",
                version = 1,
                minAppVersion = 1,
                source = "builtin",
            ),
            assetDir = "presets/general",
        )

        /**
         * 学生学期课程预设提供者。
         * 对应 `assets/presets/semester/` 目录。
         */
        fun semesterPreset(context: Context) = BuiltInAssetPresetProvider(
            context = context.applicationContext,
            metadata = PresetMetadata(
                id = "${BUILTIN_ID_PREFIX}semester",
                name = "学生学期课程",
                description = "完整课程表 + 学期周次，适合学生使用",
                author = "Tagora",
                version = 1,
                minAppVersion = 1,
                source = "builtin",
            ),
            assetDir = "presets/semester",
        )
    }
}
