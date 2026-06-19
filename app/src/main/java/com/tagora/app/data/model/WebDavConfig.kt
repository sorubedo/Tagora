package com.tagora.app.data.model

import kotlinx.serialization.Serializable

/**
 * WebDAV 服务器配置
 */
@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
) {
    /** 是否已填写必要信息 */
    val isValid: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 去掉首尾空白字符和末尾斜杠的规范化 URL */
    val normalizedUrl: String
        get() = url.trim().trimEnd('/')
}
