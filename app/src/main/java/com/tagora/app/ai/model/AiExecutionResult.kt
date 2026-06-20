package com.tagora.app.ai.model

import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TimePeriod
import kotlinx.serialization.Serializable

/**
 * AI 操作表单的执行结果。
 */
@Serializable
data class AiExecutionResult(
    val success: Boolean,
    val executed: Int,
    val errors: List<String>,
    val results: AiOperationResults? = null,
) {
    companion object {
        fun error(errors: List<String>) = AiExecutionResult(
            success = false,
            executed = 0,
            errors = errors,
            results = null,
        )

        fun error(message: String) = AiExecutionResult(
            success = false,
            executed = 0,
            errors = listOf(message),
            results = null,
        )
    }
}

/**
 * 分实体的操作结果汇总。
 */
@Serializable
data class AiOperationResults(
    val tags: AiEntityResults<Tag>,
    val periods: AiEntityResults<TimePeriod>,
    val tasks: AiEntityResults<Task>,
    val queries: AiQueryResults,
)

/**
 * 单实体的 CRUD 结果。
 */
@Serializable
data class AiEntityResults<T>(
    val created: List<T> = emptyList(),
    val updated: List<T> = emptyList(),
    val deleted: List<T> = emptyList(),
)

/**
 * 查询结果。每个 query 操作对应一个列表（结果可能为空）。
 * 按操作顺序排列。
 */
@Serializable
data class AiQueryResults(
    val tags: List<List<Tag>> = emptyList(),
    val periods: List<List<TimePeriod>> = emptyList(),
    val tasks: List<List<Task>> = emptyList(),
)
