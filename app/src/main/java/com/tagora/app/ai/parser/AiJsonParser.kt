package com.tagora.app.ai.parser

import com.tagora.app.ai.model.AiOperation
import com.tagora.app.ai.model.AiOperationSheet
import com.tagora.app.ai.model.PeriodOp
import com.tagora.app.ai.model.PeriodOpData
import com.tagora.app.ai.model.TagOp
import com.tagora.app.ai.model.TagOpData
import com.tagora.app.ai.model.TaskOp
import com.tagora.app.ai.model.TaskOpData
import com.tagora.app.data.model.PeriodType
import com.tagora.app.data.model.TaskStatus
import com.tagora.app.data.model.TaskType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * 将 JSON 字符串解析为 AiOperationSheet，并进行验证。
 *
 * 验证包括：
 * 1. JSON 结构正确性
 * 2. action/target 值合法性
 * 3. 各 target 的必填字段检查
 * 4. 字段值合法性（type, status, 日期格式等）
 *
 * 标签名称→ID 的解析不在本类进行（需要查询现有标签数据），
 * 由 AiSheetExecutor 在解析后统一处理。
 */
object AiJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 解析 JSON 字符串为 AiOperationSheet。
     *
     * @throws IllegalArgumentException 当 JSON 格式错误或验证失败时
     */
    fun parse(jsonString: String): AiOperationSheet {
        val root = try {
            json.parseToJsonElement(jsonString).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 解析失败：${e.message}")
        }

        val opsArray = root["operations"]?.jsonArray
            ?: throw IllegalArgumentException("缺少顶层 'operations' 数组")

        val operations = opsArray.mapIndexed { index, element ->
            parseOperation(index, element.jsonObject)
        }

        return AiOperationSheet(operations)
    }

    private fun parseOperation(index: Int, obj: JsonObject): AiOperation {
        val action = obj["action"]?.jsonPrimitive?.content
            ?: throw errorAt(index, "缺少 'action' 字段")
        val target = obj["target"]?.jsonPrimitive?.content
            ?: throw errorAt(index, "缺少 'target' 字段")
        val data = obj["data"]?.jsonObject
            ?: throw errorAt(index, "缺少 'data' 字段")

        validateAction(index, action)

        return when (target.lowercase()) {
            "tag" -> TagOp(action = action, data = parseTagData(index, data))
            "period" -> PeriodOp(action = action, data = parsePeriodData(index, action, data))
            "task" -> TaskOp(action = action, data = parseTaskData(index, action, data))
            else -> throw errorAt(index, "未知的 target '$target'，应为 tag/period/task")
        }
    }

    // ── Tag data ──

    private fun parseTagData(index: Int, data: JsonObject): TagOpData {
        return TagOpData(
            id = data["id"]?.jsonPrimitive?.content,
            name = data["name"]?.jsonPrimitive?.content,
            color = data["color"]?.jsonPrimitive?.content,
        ).also { validateTagData(index, it) }
    }

    private fun validateTagData(index: Int, data: TagOpData) {
        val action = "" // 从外部传入会更清晰，但这里通过字段间接判断
    }

    // ── Period data ──

    private fun parsePeriodData(index: Int, action: String, data: JsonObject): PeriodOpData {
        val daysOfWeek = when (val d = data["daysOfWeek"]) {
            null -> null
            else -> d.jsonArray.map { it.jsonPrimitive.content }
        }
        val tags = when (val t = data["tags"]) {
            null -> null
            else -> t.jsonArray.map { it.jsonPrimitive.content }
        }

        return PeriodOpData(
            id = data["id"]?.jsonPrimitive?.content,
            type = data["type"]?.jsonPrimitive?.content,
            name = data["name"]?.jsonPrimitive?.content,
            startTime = data["startTime"]?.jsonPrimitive?.content,
            endTime = data["endTime"]?.jsonPrimitive?.content,
            startDate = data["startDate"]?.jsonPrimitive?.content,
            endDate = data["endDate"]?.jsonPrimitive?.content,
            daysOfWeek = daysOfWeek,
            color = data["color"]?.jsonPrimitive?.content,
            tags = tags,
        ).also { validatePeriodData(index, action, it) }
    }

    private fun validatePeriodData(index: Int, action: String, data: PeriodOpData) {
        when (action) {
            "create", "update" -> {
                if (action == "update" && data.id.isNullOrBlank()) {
                    throw errorAt(index, "更新操作缺少 'id'")
                }
            }
            "delete" -> {
                if (data.id.isNullOrBlank())
                    throw errorAt(index, "删除操作缺少 'id'")
                if (data.type.isNullOrBlank())
                    throw errorAt(index, "删除操作缺少 'type'（需指定子类型以定位数据）")
            }
            "query" -> {
                if (data.type.isNullOrBlank())
                    throw errorAt(index, "查询操作缺少 'type'（需指定查哪个子类型）")
            }
        }

        // 验证 type 值
        val subType = data.type
        if (subType != null && subType !in setOf("daily", "weekly", "date", "deadline")) {
            throw errorAt(index, "无效的子类型 '$subType'，应为 daily/weekly/date/deadline")
        }

        // create 和 update 的字段验证
        if (action == "create") {
            if (data.name.isNullOrBlank())
                throw errorAt(index, "创建时间段缺少 'name'")
            if (data.color.isNullOrBlank())
                throw errorAt(index, "创建时间段缺少 'color'")

            when (subType) {
                "daily" -> {
                    if (data.startTime.isNullOrBlank())
                        throw errorAt(index, "daily 类型缺少 'startTime'")
                    if (data.endTime.isNullOrBlank())
                        throw errorAt(index, "daily 类型缺少 'endTime'")
                    validateTimeFormat(index, data.startTime)
                    validateTimeFormat(index, data.endTime)
                }
                "weekly" -> {
                    if (data.daysOfWeek.isNullOrEmpty())
                        throw errorAt(index, "weekly 类型缺少 'daysOfWeek'")
                    validateDaysOfWeek(index, data.daysOfWeek)
                }
                "date" -> {
                    if (data.startDate.isNullOrBlank())
                        throw errorAt(index, "date 类型缺少 'startDate'")
                    if (data.endDate.isNullOrBlank())
                        throw errorAt(index, "date 类型缺少 'endDate'")
                    validateDateFormat(index, data.startDate)
                    validateDateFormat(index, data.endDate)
                    if (data.endDate < data.startDate)
                        throw errorAt(index, "结束日期不能早于开始日期")
                }
                "deadline" -> {
                    if (data.endDate.isNullOrBlank())
                        throw errorAt(index, "deadline 类型缺少 'endDate'")
                    validateDateFormat(index, data.endDate)
                }
            }
        }

        // update 的部分字段验证
        if (action == "update") {
            if (subType == "daily") {
                data.startTime?.let { validateTimeFormat(index, it) }
                data.endTime?.let { validateTimeFormat(index, it) }
            }
            if (subType == "weekly") {
                data.daysOfWeek?.let { validateDaysOfWeek(index, it) }
            }
            if (subType == "date" || subType == "deadline") {
                data.startDate?.let { validateDateFormat(index, it) }
                data.endDate?.let { validateDateFormat(index, it) }
            }
        }
    }

    // ── Task data ──

    private fun parseTaskData(index: Int, action: String, data: JsonObject): TaskOpData {
        return TaskOpData(
            id = data["id"]?.jsonPrimitive?.content,
            name = data["name"]?.jsonPrimitive?.content,
            description = data["description"]?.jsonPrimitive?.content,
            type = data["type"]?.jsonPrimitive?.content,
            status = data["status"]?.jsonPrimitive?.content,
            condition = data["condition"]?.jsonPrimitive?.content,
        ).also { validateTaskData(index, action, it) }
    }

    private fun validateTaskData(index: Int, action: String, data: TaskOpData) {
        when (action) {
            "create" -> {
                if (data.name.isNullOrBlank())
                    throw errorAt(index, "创建任务缺少 'name'")
            }
            "update", "delete" -> {
                if (data.id.isNullOrBlank())
                    throw errorAt(index, "${action}操作缺少 'id'")
            }
        }

        data.type?.let {
            if (it !in setOf(TaskType.NORMAL, TaskType.FIXED))
                throw errorAt(index, "无效的任务类型 '$it'，应为 normal/fixed")
        }
        data.status?.let {
            if (it !in setOf(TaskStatus.INCOMPLETE, TaskStatus.COMPLETED, TaskStatus.TIMEOUT))
                throw errorAt(index, "无效的任务状态 '$it'，应为 incomplete/completed/timeout")
        }
    }

    // ── Field validation helpers ──

    private fun validateAction(index: Int, action: String) {
        if (action !in setOf("create", "query", "update", "delete")) {
            throw errorAt(index, "无效的 action '$action'，应为 create/query/update/delete")
        }
    }

    private fun validateTimeFormat(index: Int, time: String) {
        if (!time.matches(Regex("^\\d{2}:\\d{2}$"))) {
            throw errorAt(index, "时间格式错误 '$time'，应为 HH:mm（如 06:30）")
        }
        val (h, m) = time.split(":").map { it.toInt() }
        if (h !in 0..23 || m !in 0..59) {
            throw errorAt(index, "时间值超出范围 '$time'（小时 0-23，分钟 0-59）")
        }
    }

    private fun validateDateFormat(index: Int, date: String) {
        if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            throw errorAt(index, "日期格式错误 '$date'，应为 yyyy-MM-dd（如 2026-06-23）")
        }
    }

    private fun validateDaysOfWeek(index: Int, days: List<String>) {
        val valid = setOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (day in days) {
            if (day !in valid) {
                throw errorAt(index, "无效的星期 '$day'，应为 周一~周日")
            }
        }
    }

    // ── Utility ──

    private fun errorAt(index: Int, message: String): IllegalArgumentException {
        return IllegalArgumentException("操作[${index}]：$message")
    }

    /**
     * 将 HH:mm 格式的时间转换为分钟数（0-1439）。
     */
    fun parseTimeToMinutes(time: String): Int {
        val (h, m) = time.split(":").map { it.toInt() }
        return h * 60 + m
    }

    /**
     * 将中文星期名数组转换为 dayOfWeeks 数值列表（1=周一, 7=周日）。
     */
    fun parseDaysOfWeek(days: List<String>): List<Int> {
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return days.map { day -> labels.indexOf(day) + 1 }
    }
}
