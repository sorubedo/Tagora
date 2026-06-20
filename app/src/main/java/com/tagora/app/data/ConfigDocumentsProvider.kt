package com.tagora.app.data

import android.content.ContentProvider
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.tagora.app.ai.prompt.AiPromptGenerator
import com.tagora.app.data.model.isIncomplete
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * 将 filesDir 中的 JSON 配置文件暴露给 SAF，使文件管理器可以直接浏览和编辑。
 *
 * ai_prompt.md 为虚拟文件：不落磁盘，在外部应用通过 openDocument 读取时，
 * 通过 openPipeHelper 动态读取当前 Flow 数据并合成内容，再流式传回。
 */
class ConfigDocumentsProvider : DocumentsProvider() {

    private lateinit var filesDir: File
    private val authority: String by lazy { context?.packageName + ".documents" }

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        filesDir = ctx.filesDir
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
            addVirtualFileRow(cursor, documentId)
        } else {
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
            // 真实 JSON 文件
            filesDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".json") }
                .sortedBy { it.name }
                .forEach { addFileRow(cursor, it) }
            // 虚拟提示词文件
            addVirtualFileRow(cursor, VIRTUAL_PROMPT_FILE)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(documentId != ROOT_DOC_ID) { "Cannot open root" }

        // 虚拟文件：通过 pipe 按需合成内容
        if (documentId == VIRTUAL_PROMPT_FILE) {
            return openVirtualPromptFile()
        }

        // 真实文件
        val file = File(filesDir, documentId)
        require(file.exists()) { "File not found: $documentId" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getDocumentType(documentId: String): String =
        if (documentId == ROOT_DOC_ID) Document.MIME_TYPE_DIR
        else if (documentId.endsWith(".md")) "text/markdown"
        else "application/json"

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_DOC_ID && documentId != ROOT_DOC_ID

    // ── 虚拟文件：openPipeHelper 按需合成 ──────────────────────

    private fun openVirtualPromptFile(): ParcelFileDescriptor {
        val uri = Uri.parse("content://$authority/document/$VIRTUAL_PROMPT_FILE")

        val writer = object : ContentProvider.PipeDataWriter<Any> {
            override fun writeDataToPipe(
                output: ParcelFileDescriptor,
                uri: Uri,
                mimeType: String,
                opts: Bundle?,
                args: Any?,
            ) {
                val content = generatePromptContent()
                FileOutputStream(output.fileDescriptor).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                }
            }
        }

        return openPipeHelper(uri, "text/markdown", null, null, writer)
    }

    /**
     * 从当前 Flow 数据中读取并合成提示词内容。
     * 使用 runBlocking 是因为 openPipeHelper 的回调在后台线程执行，同步读取 Flow 即可。
     */
    private fun generatePromptContent(): String {
        val ctx = context ?: return "Error: context not available"
        val periodRepo = RepositoryProvider.get(ctx)
        val taskRepo = RepositoryProvider.getTaskRepo(ctx)
        val completedRepo = RepositoryProvider.getCompletedTaskRepo(ctx)

        val data = runBlocking {
            AiPromptGenerator.PromptData(
                tags = periodRepo.tagsFlow.first(),
                dailyPeriods = periodRepo.periodsFlow.first(),
                weeklyPeriods = periodRepo.weeklyPeriodsFlow.first(),
                datePeriods = periodRepo.datePeriodsFlow.first(),
                deadlinePeriods = periodRepo.deadlinePeriodsFlow.first(),
                tasks = taskRepo.tasksFlow.first().filter { it.isIncomplete },
                completedTasks = completedRepo.completedTasksFlow.first(),
            )
        }

        return AiPromptGenerator.generate(data)
    }

    // ── helpers ────────────────────────────────────────────────

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val mimeType = if (file.name.endsWith(".md")) "text/markdown" else "application/json"
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, file.name)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, mimeType)
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun addVirtualFileRow(cursor: MatrixCursor, documentId: String) {
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, documentId)
            .add(Document.COLUMN_MIME_TYPE, "text/markdown")
            .add(Document.COLUMN_FLAGS, 0) // 虚拟文件只读
            .add(Document.COLUMN_SIZE, null) // 虚拟文件无固定大小
            .add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
    }

    companion object {
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
    }
}
