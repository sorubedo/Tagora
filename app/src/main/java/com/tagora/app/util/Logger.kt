package com.tagora.app.util

import android.util.Log

/**
 * 统一日志工具。
 *
 * 使用 TAG = "Tagora" 方便 logcat 过滤：
 *   adb logcat -s Tagora
 */
object Logger {
    private const val TAG = "Tagora"

    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String, e: Throwable? = null) = Log.w(TAG, msg, e)
    fun e(msg: String, e: Throwable? = null) = Log.e(TAG, msg, e)
}
