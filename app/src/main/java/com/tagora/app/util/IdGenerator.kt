package com.tagora.app.util

import java.util.UUID

/**
 * 统一 ID 生成器
 */
fun newId(): String = UUID.randomUUID().toString()
