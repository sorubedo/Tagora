package com.tagora.app.util

import android.util.Xml
import com.tagora.app.data.model.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

class WebDavClient(private val config: WebDavConfig) {

    private val baseUrl: String get() = config.url.trim().trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val authHeader: String get() = Credentials.basic(config.username, config.password)

    private fun url(vararg segments: String): String {
        val parts = segments.map { it.trim('/') }.filter { it.isNotEmpty() }
        return if (parts.isEmpty()) baseUrl else "$baseUrl/${parts.joinToString("/")}"
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url())
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
                .header("Authorization", authHeader)
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 207) "连接成功"
            else throw WebDavException(mapHttpError(response.code))
        }
    }

    suspend fun listBackups(): Result<List<WebDavFileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url())
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
                .header("Authorization", authHeader)
                .header("Depth", "1")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 207)
                throw WebDavException(mapHttpError(response.code))
            val xml = response.body?.string() ?: throw WebDavException("响应为空")
            parsePropfindResponse(xml)
                .filter { it.isFile && it.name.startsWith(BACKUP_PREFIX) }
                .sortedByDescending { it.lastModified }
        }
    }

    suspend fun upload(fileName: String, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url(fileName))
                    .put(data.toRequestBody(JSON))
                    .header("Authorization", authHeader)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw WebDavException(mapHttpError(response.code))
            }
        }

    suspend fun download(fileName: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url(fileName))
                .get()
                .header("Authorization", authHeader)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw WebDavException(mapHttpError(response.code))
            response.body?.bytes() ?: throw WebDavException("响应为空")
        }
    }

    suspend fun delete(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url(fileName))
                .delete()
                .header("Authorization", authHeader)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw WebDavException(mapHttpError(response.code))
        }
    }

    private fun parsePropfindResponse(xml: String): List<WebDavFileInfo> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        val resources = mutableListOf<WebDavFileInfo>()
        var currentHref: String? = null
        var currentDisplayName: String? = null
        var currentContentLength: Long = 0
        var currentLastModified: Long = 0
        var currentIsCollection: Boolean = false
        var inProp = false
        var inPropstat = false
        var propstatOk = false
        var textContent = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.substringAfter("}") ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    textContent = StringBuilder()
                    when (tagName) {
                        "propstat" -> inPropstat = true
                        "prop" -> inProp = inPropstat
                        "collection" -> { if (inProp) currentIsCollection = true }
                    }
                }
                XmlPullParser.TEXT -> textContent.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val text = textContent.toString().trim()
                    when {
                        tagName == "href" && currentHref == null -> currentHref = text
                        inProp && tagName == "displayname" -> currentDisplayName = text
                        inProp && tagName == "getcontentlength" ->
                            currentContentLength = text.toLongOrNull() ?: 0
                        inProp && tagName == "getlastmodified" ->
                            currentLastModified = parseHttpDate(text)
                        tagName == "status" && inPropstat ->
                            propstatOk = text.contains("200 OK")
                        tagName == "propstat" -> inPropstat = false
                        tagName == "response" -> {
                            if (currentHref != null && propstatOk && currentHref != "/") {
                                val name = currentDisplayName
                                    ?: currentHref.trimEnd('/').substringAfterLast('/')
                                if (currentHref.removePrefix("/").isNotBlank()) {
                                    resources.add(WebDavFileInfo(
                                        name = name,
                                        href = currentHref,
                                        isFile = !currentIsCollection,
                                        contentLength = currentContentLength,
                                        lastModified = currentLastModified,
                                    ))
                                }
                            }
                            currentHref = null; currentDisplayName = null
                            currentContentLength = 0; currentLastModified = 0
                            currentIsCollection = false; propstatOk = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return resources
    }

    private fun parseHttpDate(dateStr: String): Long = try {
        val formats = listOf(
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
            java.time.format.DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss z"),
            java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy"),
        )
        for (fmt in formats) {
            try { return java.time.ZonedDateTime.parse(dateStr, fmt).toInstant().toEpochMilli() }
            catch (_: Exception) { continue }
        }
        0L
    } catch (_: Exception) { 0L }

    private fun mapHttpError(code: Int): String = when (code) {
        401 -> "认证失败（401）"
        403 -> "没有访问权限（403）"
        404 -> "路径不存在（404）"
        405 -> "操作不被允许（405）"
        507 -> "服务器存储空间不足（507）"
        else -> "请求失败（$code）"
    }

    companion object {
        const val BACKUP_PREFIX = "tagora_backup_"
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val JSON = "application/json".toMediaType()
        private val PROPFIND_BODY = """<?xml version="1.0" encoding="UTF-8"?>
            |<D:propfind xmlns:D="DAV:">
            |  <D:prop>
            |    <D:displayname/>
            |    <D:getcontentlength/>
            |    <D:getcontenttype/>
            |    <D:getlastmodified/>
            |    <D:resourcetype/>
            |  </D:prop>
            |</D:propfind>
        """.trimMargin()
    }
}

data class WebDavFileInfo(
    val name: String,
    val href: String,
    val isFile: Boolean,
    val contentLength: Long,
    val lastModified: Long,
) {
    val displaySize: String get() = when {
        contentLength < 1024 -> "${contentLength} B"
        contentLength < 1024 * 1024 -> "${contentLength / 1024} KB"
        else -> "${contentLength / (1024 * 1024)} MB"
    }
    val displayTime: String get() = if (lastModified > 0) {
        java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(lastModified))
    } else "未知"
}

class WebDavException(message: String) : Exception(message)
