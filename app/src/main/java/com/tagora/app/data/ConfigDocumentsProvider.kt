package com.tagora.app.data

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import com.tagora.app.ai.prompt.AiPromptGenerator
import com.tagora.app.data.model.AppConfig
import com.tagora.app.data.model.TaskConditionSerializersModule
import com.tagora.app.data.model.isIncomplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

/**
 * 将 filesDir 中的 AI 提示词文件暴露给 SAF，使文件管理器可以直接浏览。
 *
 * ai_prompt.md 由 [requestPromptRefresh] 在每次配置数据写入磁盘后异步生成，
 * 通过 [openDocument] 直接提供，避免使用 openPipeHelper 带来的 broken pipe 崩溃风险。
 */
class ConfigDocumentsProvider : DocumentsProvider() {

    private lateinit var filesDir: File

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        filesDir = ctx.filesDir
        // 仅在文件不存在时同步生成（首次安装/清除数据场景），
        // 后续刷新由 JsonFileRepository.save() → requestPromptRefresh() 驱动
        val file = File(filesDir, VIRTUAL_PROMPT_FILE)
        if (!file.exists()) {
            try {
                refreshPromptFile(ctx)
            } catch (e: Exception) {
                Log.w(TAG, "初始化提示词文件失败", e)
            }
        }
        return true
    }

    // ── roots ──────────────────────────────────────────────────

    override fun queryRoots(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
            .add(Root.COLUMN_TITLE, "Tagora 配置")
            .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
        return cursor
    }

    // ── documents ──────────────────────────────────────────────

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (documentId == ROOT_DOC_ID) {
            cursor.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
                .add(Document.COLUMN_DISPLAY_NAME, "Tagora 配置")
                .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                .add(Document.COLUMN_FLAGS, 0)
                .add(Document.COLUMN_SIZE, null)
                .add(Document.COLUMN_LAST_MODIFIED, null)
        } else if (documentId == VIRTUAL_PROMPT_FILE) {
            val file = File(filesDir, documentId)
            if (file.exists()) addFileRow(cursor, file)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (parentDocumentId == ROOT_DOC_ID) {
            // 只暴露 ai_prompt.md，不暴露 JSON 配置文件
            val file = File(filesDir, VIRTUAL_PROMPT_FILE)
            if (file.exists()) addFileRow(cursor, file)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (documentId != VIRTUAL_PROMPT_FILE) {
            throw FileNotFoundException("不支持的文件: $documentId")
        }
        val file = File(filesDir, documentId)
        if (!file.exists()) {
            throw FileNotFoundException("文件不存在: $documentId")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getDocumentType(documentId: String): String = when (documentId) {
        ROOT_DOC_ID -> Document.MIME_TYPE_DIR
        else -> "text/markdown" // 当前仅暴露 ai_prompt.md
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_DOC_ID && documentId == VIRTUAL_PROMPT_FILE

    // ── helpers ────────────────────────────────────────────────

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val mimeType = if (file.name.endsWith(".md")) "text/markdown" else "application/json"
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, file.name)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, mimeType)
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    companion object {
        private const val TAG = "ConfigDocuments"
        private const val ROOT_ID = "tagora_config"
        private const val ROOT_DOC_ID = "root"
        private const val VIRTUAL_PROMPT_FILE = "ai_prompt.md"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        // ── 并发保护 ──────────────────────────────────────────

        /** 保护 [refreshPromptFile] 的读写操作，防止并发写入导致文件损坏 */
        private val promptLock = Any()

        /** 上次写入的提示词内容哈希，用于跳过无变化的反复写入 */
        @Volatile
        private var lastPromptContentHash: Int = 0

        // ── 防抖 ──────────────────────────────────────────────

        private var refreshJob: Job? = null
        private val refreshScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /**
         * 请求刷新提示词文件（带 300ms 防抖）。
         * 由 [JsonFileRepository.save] 在每次配置数据写入磁盘后调用，
         * 确保短时间内多次保存（如重置默认配置、AI 批量操作）只触发一次生成。
         */
        fun requestPromptRefresh(context: Context) {
            refreshJob?.cancel()
            refreshJob = refreshScope.launch {
                delay(300)
                refreshPromptFile(context)
            }
        }

        /**
         * 从当前 JSON 配置文件重新生成 ai_prompt.md 到 filesDir。
         *
         * 线程安全：使用 [promptLock] 同步，支持多线程并发调用。
         * 原子写入：先写临时文件再 rename，避免 SAF 客户端读到不完整内容。
         * 去重优化：内容未变且文件已存在时跳过写入。
         */
        fun refreshPromptFile(context: Context) {
            val filesDir = context.filesDir
            val json = Json {
                serializersModule = TaskConditionSerializersModule
                ignoreUnknownKeys = true
            }

            synchronized(promptLock) {
                try {
                    val config = readConfig<AppConfig>(filesDir, "config.json", json)
                    val tags = config?.tags ?: emptyList()
                    val daily = config?.periods ?: emptyList()
                    val weekly = config?.weeklyPeriods ?: emptyList()
                    val date = config?.datePeriods ?: emptyList()
                    val deadline = config?.deadlinePeriods ?: emptyList()
                    val tasks = config?.tasks?.filter { it.isIncomplete } ?: emptyList()
                    val completed = config?.completedTasks ?: emptyList()

                    val content = AiPromptGenerator.generate(
                        AiPromptGenerator.PromptData(tags, daily, weekly, date, deadline, tasks, completed),
                    )
                    val contentHash = content.hashCode()
                    val file = File(filesDir, VIRTUAL_PROMPT_FILE)

                    // 内容未变且文件存在 → 跳过写入
                    if (file.exists() && contentHash == lastPromptContentHash) return

                    // 先写临时文件再原子 rename，防止 openDocument 读到不完整内容
                    val tmpFile = File(filesDir, "$VIRTUAL_PROMPT_FILE.tmp")
                    tmpFile.writeText(content, Charsets.UTF_8)
                    tmpFile.renameTo(file)
                    lastPromptContentHash = contentHash
                } catch (e: Exception) {
                    Log.w(TAG, "刷新 ai_prompt.md 失败", e)
                }
            }
        }

        private inline fun <reified T> readConfig(dir: File, name: String, json: Json): T? {
            val file = File(dir, name)
            if (!file.exists()) return null
            return try {
                json.decodeFromString<T>(file.readText())
            } catch (_: Exception) {
                null
            }
        }
    }
}
