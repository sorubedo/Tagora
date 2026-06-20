package com.tagora.app.ai.model

/**
 * AI 操作表单的顶层容器。
 *
 * JSON 格式：
 * {
 *   "operations": [
 *     { "action": "create", "target": "tag", "data": { ... } },
 *     { "action": "query", "target": "period", "data": { ... } }
 *   ]
 * }
 */
data class AiOperationSheet(
    val operations: List<AiOperation>,
)

/**
 * 单个操作，由 action + target + data 组成。
 * action: create | query | update | delete
 * target: tag | period | task
 */
sealed interface AiOperation {
    val action: String
    val target: String
}

// ── Tag 操作 ──

data class TagOp(
    override val action: String,
    val data: TagOpData,
) : AiOperation {
    override val target: String = "tag"
}

/**
 * Tag 操作的 data 字段。
 *
 * create: name (必填), color (必填)
 * query:  id/name/color 全可选，全部留空 = 列出所有
 * update: id (必填), name/color 可选
 * delete: id (必填)
 */
data class TagOpData(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,
)

// ── Period 操作 ──

data class PeriodOp(
    override val action: String,
    val data: PeriodOpData,
) : AiOperation {
    override val target: String = "period"
}

/**
 * Period 操作的 data 字段。
 *
 * 四种 subType 的字段集合不同，通过 type 字段区分。
 *
 * create/update 字段：
 *   daily:    type, name, startTime, endTime, color, tags
 *   weekly:   type, name, daysOfWeek, color, tags
 *   date:     type, name, startDate, endDate, color, tags
 *   deadline: type, name, endDate, color, tags
 *
 * query 字段：
 *   type (必填), name/tags/daysOfWeek/startDate/endDate/startTime/endTime 可选
 *
 * delete 字段：
 *   id (必填), type (必填)
 */
data class PeriodOpData(
    val id: String? = null,
    val type: String? = null,          // "daily" | "weekly" | "date" | "deadline"
    val name: String? = null,
    val startTime: String? = null,     // HH:mm, only for daily
    val endTime: String? = null,       // HH:mm, only for daily
    val startDate: String? = null,     // yyyy-MM-dd, only for date
    val endDate: String? = null,       // yyyy-MM-dd, for date and deadline
    val daysOfWeek: List<String>? = null, // only for weekly, e.g. ["周一","周三"]
    val color: String? = null,
    val tags: List<String>? = null,    // tag names, not IDs
)

// ── Task 操作 ──

data class TaskOp(
    override val action: String,
    val data: TaskOpData,
) : AiOperation {
    override val target: String = "task"
}

/**
 * Task 操作的 data 字段。
 *
 * create: name (必填), description/type/condition 可选
 * query:  name/description/type/status/condition 全可选
 * update: id (必填), 其余字段可选
 * delete: id (必填)
 */
data class TaskOpData(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,          // "normal" | "fixed"
    val status: String? = null,        // "incomplete" | "completed" | "timeout"
    val condition: String? = null,     // 条件表达式字符串，如 "运动 AND 休息"
)
