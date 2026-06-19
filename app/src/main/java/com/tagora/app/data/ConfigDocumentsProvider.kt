package com.tagora.app.data

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * 将 filesDir 中的 JSON 配置文件暴露给 SAF，使文件管理器可以直接浏览和编辑。
 * 参照 RikkaHub WorkspaceDocumentsProvider 的结构，简化为单一固定工作区。
 */
class ConfigDocumentsProvider : DocumentsProvider() {

    private lateinit var filesDir: File

    override fun onCreate(): Boolean {
        filesDir = context?.filesDir ?: return false
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
            filesDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".json") }
                .sortedBy { it.name }
                .forEach { addFileRow(cursor, it) }
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(documentId != ROOT_DOC_ID) { "Cannot open root" }
        val file = File(filesDir, documentId)
        require(file.exists()) { "File not found: $documentId" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getDocumentType(documentId: String): String =
        if (documentId == ROOT_DOC_ID) Document.MIME_TYPE_DIR else "application/json"

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_DOC_ID && documentId != ROOT_DOC_ID

    // ── helpers ────────────────────────────────────────────────

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, file.name)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, "application/json")
            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    companion object {
        private const val ROOT_ID = "tagora_config"
        private const val ROOT_DOC_ID = "root"

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
