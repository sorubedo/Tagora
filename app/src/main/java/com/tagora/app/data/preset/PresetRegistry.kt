package com.tagora.app.data.preset

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.util.Log
import com.tagora.app.data.AppPreferences
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 预设配置注册表（全局单例）。
 *
 * 管理所有已发现的 [PresetProvider] 实例：
 * - 内置预设（通过 [BuiltInAssetPresetProvider] 工厂方法注册）
 * - 外部插件预设（通过 PackageManager 发现已安装的预设提供者 App）
 *
 * ## 线程安全
 * 使用 [ConcurrentHashMap] 存储提供者，支持多线程并发读取。
 * [init] 方法通过 synchronized + @Volatile 保证幂等初始化。
 *
 * ## 向后兼容
 * [getProvider] 自动将旧的预设 key（"general"/"semester"）映射到新的 ID 格式。
 */
object PresetRegistry {

    private const val TAG = "PresetRegistry"

    /** 内置预设 ID 前缀 */
    private const val BUILTIN_PREFIX = "builtin:"

    // ── 存储 ──────────────────────────────────────────────────────

    private val providers = ConcurrentHashMap<String, PresetProvider>()

    @Volatile
    private var initialized = false

    // ── 初始化 ────────────────────────────────────────────────────

    /**
     * 初始化注册表。
     *
     * 幂等操作：多次调用仅首次生效。注册内置预设并扫描已安装的插件。
     *
     * @param context Android Context（自动转为 applicationContext）
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext

            // 注册内置预设（不依赖外部插件）
            registerBuiltIn(appContext)

            // 发现并注册外部插件
            discoverPlugins(appContext)

            initialized = true
            Log.d(TAG, "已注册 ${providers.size} 个预设提供者")
        }
    }

    // ── 查询接口 ──────────────────────────────────────────────────

    /**
     * 获取所有已注册的预设提供者。
     * 内置预设排在前面，插件预设排在后面。
     */
    fun getAllProviders(): List<PresetProvider> {
        ensureInitialized()
        return providers.values.toList().sortedBy { provider ->
            // 内置预设优先排序
            if (provider.metadata.source == "builtin") 0 else 1
        }
    }

    /**
     * 按 ID 查找预设提供者。
     *
     * 支持向后兼容的旧 key 映射：
     * - `null` / `""` → `"builtin:default"`
     * - `"general"` → `"builtin:general"`
     * - `"semester"` → `"builtin:semester"`
     *
     * @param id 预设标识符（可为 null）
     * @return 对应的 PresetProvider，如果未找到返回 null
     */
    fun getProvider(id: String?): PresetProvider? {
        ensureInitialized()
        val resolvedId = resolveId(id)
        return providers[resolvedId]
    }

    /**
     * 根据 AppPreferences 获取当前选中的预设提供者。
     *
     * @param prefs 应用偏好（可为 null）
     * @return 选中的 PresetProvider，如果未选择或未找到返回 null
     */
    fun getSelectedProvider(prefs: AppPreferences?): PresetProvider? {
        if (prefs == null) return getProvider(null)
        return getProvider(prefs.selectedPreset)
    }

    // ── 向后兼容 ID 解析 ──────────────────────────────────────────

    /**
     * 将旧的简短 key 解析为新的完整 ID。
     */
    private fun resolveId(raw: String?): String? = when (raw) {
        null, "" -> "${BUILTIN_PREFIX}default"
        "general" -> "${BUILTIN_PREFIX}general"
        "semester" -> "${BUILTIN_PREFIX}semester"
        else -> raw
    }

    // ── 内置预设注册 ──────────────────────────────────────────────

    private fun registerBuiltIn(context: Context) {
        val default = BuiltInAssetPresetProvider.defaultPreset(context)
        val general = BuiltInAssetPresetProvider.generalPreset(context)
        val semester = BuiltInAssetPresetProvider.semesterPreset(context)

        providers[default.metadata.id] = default
        providers[general.metadata.id] = general
        providers[semester.metadata.id] = semester
    }

    // ── 插件发现 ──────────────────────────────────────────────────

    /**
     * 扫描已安装的预设提供者插件 App。
     *
     * 使用 [PackageManager.queryIntentServices] 查找声明了
     * [PresetPluginContract.ACTION_PROVIDE_PRESETS] action 的 Service，
     * 从 Service 的 meta-data 中读取 provider authority，
     * 然后创建 [ContentProviderPresetProvider] 封装。
     */
    private fun discoverPlugins(context: Context) {
        val pm = context.packageManager
        val intent = Intent(PresetPluginContract.ACTION_PROVIDE_PRESETS)

        val services = runCatching {
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }.getOrElse { e ->
            Log.w(TAG, "插件发现失败", e)
            return
        }

        for (info in services) {
            try {
                val pkgName = info.serviceInfo.packageName
                // 跳过自身
                if (pkgName == context.packageName) continue

                val metaData = info.serviceInfo.metaData
                val authority = metaData?.getString(PresetPluginContract.META_KEY_AUTHORITY)
                    ?: PresetPluginContract.buildAuthority(pkgName)

                // 签名验证
                if (!verifySignature(context, pkgName)) {
                    Log.w(TAG, "插件 $pkgName 签名验证失败，已跳过")
                    continue
                }

                val provider = ContentProviderPresetProvider(context, authority)
                providers[provider.metadata.id] = provider
                Log.d(TAG, "已发现插件预设: ${provider.metadata.name} (来自 $pkgName)")
            } catch (e: Exception) {
                Log.w(TAG, "注册插件预设失败: ${info.serviceInfo.packageName}", e)
            }
        }
    }

    // ── 签名验证 ──────────────────────────────────────────────────

    /**
     * 验证插件 App 的签名证书是否与本 App 一致。
     *
     * 仅允许使用相同签名密钥的插件提供预设数据，防止恶意插件注入伪造配置。
     *
     * @return true 表示签名匹配，插件可信
     */
    private fun verifySignature(context: Context, pluginPackage: String): Boolean {
        return runCatching {
            val pm = context.packageManager

            val hostInfo = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val pluginInfo = pm.getPackageInfo(
                pluginPackage,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )

            val hostSigners = hostInfo.signingInfo?.apkContentsSigners ?: return false
            val pluginSigners = pluginInfo.signingInfo?.apkContentsSigners ?: return false

            if (hostSigners.isEmpty() || pluginSigners.isEmpty()) return false

            val sha256 = MessageDigest.getInstance("SHA-256")
            val hostHashes = hostSigners.map { sha256.digest(it.toByteArray()).toHexString() }.toSet()
            val pluginHashes = pluginSigners.map { sha256.digest(it.toByteArray()).toHexString() }.toSet()

            // 任一签名匹配即可（支持多签名场景）
            hostHashes.any { it in pluginHashes }
        }.getOrElse { e ->
            Log.w(TAG, "签名验证异常: $pluginPackage", e)
            false
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // ── 内部工具 ──────────────────────────────────────────────────

    private fun ensureInitialized() {
        if (!initialized) {
            Log.w(TAG, "PresetRegistry 尚未初始化，请先调用 init()")
        }
    }
}
