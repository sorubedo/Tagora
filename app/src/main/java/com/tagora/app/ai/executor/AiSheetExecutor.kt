package com.tagora.app.ai.executor

import com.tagora.app.ai.model.AiEntityResults
import com.tagora.app.ai.model.AiExecutionResult
import com.tagora.app.ai.model.AiOperation
import com.tagora.app.ai.model.AiOperationResults
import com.tagora.app.ai.model.AiOperationSheet
import com.tagora.app.ai.model.AiQueryResults
import com.tagora.app.ai.model.PeriodOp
import com.tagora.app.ai.model.TagOp
import com.tagora.app.ai.model.TaskOp
import com.tagora.app.ai.parser.AiJsonParser
import com.tagora.app.ai.parser.ConditionParser
import com.tagora.app.data.CompletedTaskRepository
import com.tagora.app.data.TaskRepository
import com.tagora.app.data.TimePeriodRepository
import com.tagora.app.data.model.AndCondition
import com.tagora.app.data.model.Tag
import com.tagora.app.data.model.Task
import com.tagora.app.data.model.TaskCondition
import com.tagora.app.data.model.TaskStatus
import com.tagora.app.data.model.TaskType
import com.tagora.app.data.model.TimePeriod
import com.tagora.app.data.model.collectTagIds
import com.tagora.app.domain.usecase.DeleteTagUseCase
import com.tagora.app.util.dayOfWeekLabels
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * AI 操作表单执行引擎。
 *
 * 执行流程：
 * 1. 收集现有标签，建立 name→ID 映射
 * 2. 处理标签操作，更新映射
 * 3. 验证所有 period/task 操作中的标签名
 * 4. 全部通过后，依次执行 period 操作 → task 操作 → query 操作
 * 5. 任何验证失败均全部回滚（不执行任何写操作）
 */
class AiSheetExecutor(
    private val periodRepo: TimePeriodRepository,
    private val taskRepo: TaskRepository,
    private val completedTaskRepo: CompletedTaskRepository,
) {
    private val deleteTagUseCase = DeleteTagUseCase(periodRepo)

    /**
     * 执行整个操作表单。
     *
     * 执行顺序：验证 → 标签 → 重新解析条件 → 时间段 → 任务 → 查询
     * 关键：条件表达式必须在标签创建**之后**重新解析，
     * 因为验证时新标签使用占位 ID，执行时才获得真实 ID。
     */
    suspend fun execute(sheet: AiOperationSheet): AiExecutionResult {
        // 1. 构建初始 name→ID 映射
        val nameToId = mutableMapOf<String, String>()
        val existingTags = periodRepo.tagsFlow.first()
        for (tag in existingTags) {
            nameToId[tag.name] = tag.id
        }

        // 2. 分类操作
        val tagOps = mutableListOf<TagOp>()
        val periodOps = mutableListOf<PeriodOp>()
        val taskOps = mutableListOf<TaskOp>()

        for (op in sheet.operations) {
            when (op) {
                is TagOp -> tagOps.add(op)
                is PeriodOp -> periodOps.add(op)
                is TaskOp -> taskOps.add(op)
            }
        }

        // 3. 验证阶段：只检查标签名是否存在（不解析条件表达式，避免占位 ID 问题）
        val validationErrors = mutableListOf<String>()

        // 3a. 检查标签操作的基本合法性 + 暂用占位 ID
        for ((i, op) in tagOps.withIndex()) {
            when (op.action) {
                "create" -> {
                    val name = op.data.name!!
                    if (nameToId.containsKey(name)) {
                        validationErrors.add("标签[${i}]：标签名 '$name' 已存在")
                    } else {
                        nameToId[name] = "__new_tag_${i}__" // 占位，仅用于验证阶段
                    }
                }
                "update" -> {
                    val id = op.data.id!!
                    val existingTag = existingTags.find { it.id == id }
                    if (existingTag == null) {
                        validationErrors.add("标签[${i}]：ID '$id' 不存在")
                    } else {
                        nameToId.remove(existingTag.name)
                        val newName = op.data.name ?: existingTag.name
                        if (nameToId.containsKey(newName) && nameToId[newName] != id) {
                            validationErrors.add("标签[${i}]：标签名 '$newName' 已被其他标签使用")
                        } else {
                            nameToId[newName] = id
                        }
                    }
                }
                "delete" -> {
                    val id = op.data.id!!
                    if (existingTags.none { it.id == id }) {
                        validationErrors.add("标签[${i}]：ID '$id' 不存在")
                    } else {
                        nameToId.remove(existingTags.find { it.id == id }!!.name)
                    }
                }
                "query" -> { /* 只读，不影响映射 */ }
            }
        }

        // 3b. 验证 period 操作中的标签名（只检查名称存在性）
        for ((i, op) in periodOps.withIndex()) {
            if (op.action == "query" || op.action == "delete") continue
            val tags = op.data.tags ?: continue
            for (tagName in tags) {
                if (tagName !in nameToId) {
                    validationErrors.add("时间段[${i}]：标签 '$tagName' 不存在，请先创建该标签")
                }
            }
        }

        // 3c. 验证 task 操作中的标签名（只检查名称存在性，不解析条件树）
        for ((i, op) in taskOps.withIndex()) {
            if (op.action == "query" || op.action == "delete") continue
            val condStr = op.data.condition
            if (condStr.isNullOrBlank()) continue
            val tagNames = collectTagNamesFromCondition(condStr)
            for (tagName in tagNames) {
                if (tagName !in nameToId) {
                    validationErrors.add("任务[${i}]：标签 '$tagName' 不存在，请先创建该标签")
                }
            }
        }

        // 如果有验证错误，全部回滚
        if (validationErrors.isNotEmpty()) {
            return AiExecutionResult.error(validationErrors)
        }

        // 4. 执行标签操作（获得真实 ID，更新 nameToId）
        // 需要重置新建标签的 nameToId 映射（去掉占位 ID）
        for ((i, op) in tagOps.withIndex()) {
            if (op.action == "create") {
                nameToId.remove(op.data.name!!) // 移除占位 ID
            }
        }
        // 恢复 update/delete 影响的映射到执行前的状态（用 existingTags 重建）
        for (tag in existingTags) {
            nameToId[tag.name] = tag.id
        }
        val tagResults = executeTagOps(tagOps, existingTags, nameToId)

        // 5. 在标签执行后重新解析条件表达式（此时 nameToId 包含真实 ID）
        val parsedConditions = mutableMapOf<Int, TaskCondition>()
        for ((i, op) in taskOps.withIndex()) {
            if (op.action == "query" || op.action == "delete") continue
            val condStr = op.data.condition
            if (condStr.isNullOrBlank()) {
                parsedConditions[i] = AndCondition(emptyList())
            } else {
                val parser = ConditionParser(nameToId)
                parsedConditions[i] = parser.parse(condStr)
            }
        }
        // task query 的条件也重新解析
        for ((i, op) in taskOps.withIndex()) {
            if (op.action != "query") continue
            val condStr = op.data.condition ?: continue
            val parser = ConditionParser(nameToId)
            parsedConditions[i] = parser.parse(condStr)
        }

        // 6. 执行时间段和任务操作
        val periodResults = executePeriodOps(periodOps, nameToId)
        val taskResults = executeTaskOps(taskOps, nameToId, parsedConditions)

        // 7. 执行查询（只读，在所有写操作之后）
        val queryResults = executeQueries(sheet.operations, nameToId)

        val totalExecuted = tagOps.count { it.action != "query" } +
            periodOps.count { it.action != "query" } +
            taskOps.count { it.action != "query" }

        return AiExecutionResult(
            success = true,
            executed = totalExecuted,
            errors = emptyList(),
            results = AiOperationResults(
                tags = tagResults,
                periods = periodResults,
                tasks = taskResults,
                queries = queryResults,
            ),
        )
    }

    // ── Tag 执行 ──

    private suspend fun executeTagOps(
        ops: List<TagOp>,
        existingTags: List<Tag>,
        nameToId: MutableMap<String, String>,
    ): AiEntityResults<Tag> {
        val created = mutableListOf<Tag>()
        val updated = mutableListOf<Tag>()
        val deleted = mutableListOf<Tag>()

        var tags = existingTags.toMutableList()

        for (op in ops) {
            when (op.action) {
                "create" -> {
                    val id = newId()
                    val tag = Tag(id = id, name = op.data.name!!, color = op.data.color!!)
                    tags.add(tag)
                    created.add(tag)
                    // 更新 nameToId 映射
                    nameToId[tag.name] = id
                }
                "update" -> {
                    val id = op.data.id!!
                    val idx = tags.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        val old = tags[idx]
                        val newTag = old.copy(
                            name = op.data.name ?: old.name,
                            color = op.data.color ?: old.color,
                        )
                        tags[idx] = newTag
                        updated.add(newTag)
                        // 更新 nameToId（如果改了名）
                        if (op.data.name != null && op.data.name != old.name) {
                            nameToId.remove(old.name)
                            nameToId[op.data.name] = id
                        }
                    }
                }
                "delete" -> {
                    val id = op.data.id!!
                    val target = tags.find { it.id == id }
                    if (target != null) {
                        deleted.add(target)
                        tags = tags.filter { it.id != id }.toMutableList()
                        deleteTagUseCase.execute(id)
                        nameToId.remove(target.name)
                        // 跳过 save，deleteTagUseCase 已经保存了所有变更
                        continue
                    }
                }
                "query" -> { /* handled in executeQueries */ }
            }
        }

        // 保存（delete 已跳过，因为 use case 内部保存了）
        if (ops.any { it.action != "delete" && it.action != "query" }) {
            periodRepo.saveTags(tags)
        }

        return AiEntityResults(created, updated, deleted)
    }

    // ── Period 执行 ──

    private suspend fun executePeriodOps(
        ops: List<PeriodOp>,
        nameToId: Map<String, String>,
    ): AiEntityResults<TimePeriod> {
        val created = mutableListOf<TimePeriod>()
        val updated = mutableListOf<TimePeriod>()
        val deleted = mutableListOf<TimePeriod>()

        for (op in ops) {
            when (op.action) {
                "create" -> {
                    val period = buildPeriod(op.data, nameToId)
                    created.add(period)
                    savePeriodsOfType(op.data.type!!, saveAction = { periods ->
                        periods + period
                    })
                }
                "update" -> {
                    val id = op.data.id!!
                    val type = op.data.type ?: findPeriodType(id)
                        ?: continue
                    savePeriodsOfType(type, saveAction = { periods ->
                        val idx = periods.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            val updated_ = mergePeriod(periods[idx], op.data, nameToId)
                            updated.add(updated_)
                            periods.toMutableList().apply { this[idx] = updated_ }
                        } else {
                            periods
                        }
                    })
                }
                "delete" -> {
                    val id = op.data.id!!
                    val type = op.data.type!!
                    savePeriodsOfType(type, saveAction = { periods ->
                        val target = periods.find { it.id == id }
                        if (target != null) deleted.add(target)
                        periods.filter { it.id != id }
                    })
                }
                "query" -> { /* handled in executeQueries */ }
            }
        }

        return AiEntityResults(created, updated, deleted)
    }

    private suspend fun savePeriodsOfType(
        type: String,
        saveAction: suspend (List<TimePeriod>) -> List<TimePeriod>,
    ) {
        val (flow, save: suspend (List<TimePeriod>) -> Unit) = when (type) {
            "daily" -> periodRepo.periodsFlow to periodRepo::savePeriods
            "weekly" -> periodRepo.weeklyPeriodsFlow to periodRepo::saveWeeklyPeriods
            "date" -> periodRepo.datePeriodsFlow to periodRepo::saveDatePeriods
            "deadline" -> periodRepo.deadlinePeriodsFlow to periodRepo::saveDeadlinePeriods
            else -> return
        }
        val current = flow.first()
        val newList = saveAction(current)
        save(newList)
    }

    private suspend fun findPeriodType(id: String): String? {
        for (type in listOf("daily", "weekly", "date", "deadline")) {
            val flow = when (type) {
                "daily" -> periodRepo.periodsFlow
                "weekly" -> periodRepo.weeklyPeriodsFlow
                "date" -> periodRepo.datePeriodsFlow
                "deadline" -> periodRepo.deadlinePeriodsFlow
                else -> continue
            }
            if (flow.first().any { it.id == id }) return type
        }
        return null
    }

    // ── Task 执行 ──

    private suspend fun executeTaskOps(
        ops: List<TaskOp>,
        nameToId: Map<String, String>,
        parsedConditions: Map<Int, TaskCondition>,
    ): AiEntityResults<Task> {
        val created = mutableListOf<Task>()
        val updated = mutableListOf<Task>()
        val deleted = mutableListOf<Task>()

        var tasks = taskRepo.tasksFlow.first().toMutableList()

        for ((i, op) in ops.withIndex()) {
            when (op.action) {
                "create" -> {
                    val condition = parsedConditions[i] ?: AndCondition(emptyList())
                    val task = Task(
                        id = newId(),
                        name = op.data.name!!,
                        description = op.data.description ?: "",
                        condition = condition,
                        type = op.data.type ?: TaskType.NORMAL,
                        status = TaskStatus.INCOMPLETE,
                    )
                    tasks.add(task)
                    created.add(task)
                }
                "update" -> {
                    val id = op.data.id!!
                    val idx = tasks.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        val old = tasks[idx]
                        val newCondition = if (op.data.condition != null) {
                            parsedConditions[i] ?: old.condition
                        } else {
                            old.condition
                        }
                        val newStatus = op.data.status ?: old.status
                        val newCompletedAt = if (op.data.status == TaskStatus.COMPLETED && old.status != TaskStatus.COMPLETED) {
                            System.currentTimeMillis()
                        } else {
                            old.completedAt
                        }

                        val newTask = old.copy(
                            name = op.data.name ?: old.name,
                            description = op.data.description ?: old.description,
                            condition = newCondition,
                            type = op.data.type ?: old.type,
                            status = newStatus,
                            completedAt = newCompletedAt,
                        )

                        // 如果状态变为 completed，从活跃列表移到已完成列表
                        if (newStatus == TaskStatus.COMPLETED && old.status != TaskStatus.COMPLETED) {
                            tasks.removeAt(idx)
                            completedTaskRepo.addCompletedTask(newTask)
                        } else {
                            tasks[idx] = newTask
                        }
                        updated.add(newTask)
                    }
                }
                "delete" -> {
                    val id = op.data.id!!
                    val target = tasks.find { it.id == id }
                    if (target != null) {
                        deleted.add(target)
                        tasks = tasks.filter { it.id != id }.toMutableList()
                    }
                }
                "query" -> { /* handled in executeQueries */ }
            }
        }

        taskRepo.saveTasks(tasks)
        return AiEntityResults(created, updated, deleted)
    }

    // ── Query 执行 ──

    private suspend fun executeQueries(
        allOps: List<AiOperation>,
        nameToId: Map<String, String>,
    ): AiQueryResults {
        val tagQueries = mutableListOf<List<Tag>>()
        val periodQueries = mutableListOf<List<TimePeriod>>()
        val taskQueries = mutableListOf<List<Task>>()

        for (op in allOps) {
            when (op) {
                is TagOp -> {
                    if (op.action != "query") continue
                    val allTags = periodRepo.tagsFlow.first()
                    tagQueries.add(allTags.filter { tag ->
                        val matchId = op.data.id == null || op.data.id == tag.id
                        val matchName = op.data.name == null || tag.name.contains(op.data.name, ignoreCase = true)
                        val matchColor = op.data.color == null || tag.color == op.data.color
                        matchId && matchName && matchColor
                    })
                }
                is PeriodOp -> {
                    if (op.action != "query") continue
                    val data = op.data
                    val type = data.type ?: continue
                    val allPeriods = when (type) {
                        "daily" -> periodRepo.periodsFlow.first()
                        "weekly" -> periodRepo.weeklyPeriodsFlow.first()
                        "date" -> periodRepo.datePeriodsFlow.first()
                        "deadline" -> periodRepo.deadlinePeriodsFlow.first()
                        else -> continue
                    }
                    periodQueries.add(allPeriods.filter { period ->
                        val matchName = data.name == null || period.name.contains(data.name, ignoreCase = true)
                        val matchTags = data.tags == null || data.tags.any { queryTagName ->
                            val queryTagId = nameToId[queryTagName]
                            queryTagId != null && queryTagId in period.tagIds
                        }
                        val matchDaysOfWeek = if (data.daysOfWeek != null) {
                            val queryDays = AiJsonParser.parseDaysOfWeek(data.daysOfWeek)
                            period.dayOfWeeks.any { it in queryDays }
                        } else true
                        val matchStartDate = data.startDate == null || period.startDate == data.startDate
                        val matchEndDate = data.endDate == null || period.endDate == data.endDate
                        val matchStartTime = if (data.startTime != null) {
                            period.startMinute == AiJsonParser.parseTimeToMinutes(data.startTime)
                        } else true
                        val matchEndTime = if (data.endTime != null) {
                            period.endMinute == AiJsonParser.parseTimeToMinutes(data.endTime)
                        } else true
                        matchName && matchTags && matchDaysOfWeek && matchStartDate &&
                            matchEndDate && matchStartTime && matchEndTime
                    })
                }
                is TaskOp -> {
                    if (op.action != "query") continue
                    val data = op.data
                    val allTasks = taskRepo.tasksFlow.first()
                    taskQueries.add(allTasks.filter { task ->
                        val matchName = data.name == null || task.name.contains(data.name, ignoreCase = true)
                        val matchDesc = data.description == null || task.description.contains(data.description, ignoreCase = true)
                        val matchType = data.type == null || task.type == data.type
                        val matchStatus = data.status == null || task.status == data.status
                        val matchCondition = if (data.condition != null) {
                            val queryTagIds = task.condition.collectTagIds()
                            // 解析查询条件收集标签名，检查任务的 condition 是否引用这些标签
                            val queryTagNames = collectTagNamesFromCondition(data.condition)
                            queryTagNames.all { qName ->
                                val qId = nameToId[qName]
                                qId != null && qId in queryTagIds
                            }
                        } else true
                        matchName && matchDesc && matchType && matchStatus && matchCondition
                    })
                }
            }
        }

        return AiQueryResults(tagQueries, periodQueries, taskQueries)
    }

    /**
     * 从条件表达式字符串中收集所有引用的标签名。
     * 简单实现：按 AND/OR/NOT/(/) 分割，提取标签名。
     */
    private fun collectTagNamesFromCondition(expression: String): Set<String> {
        if (expression.isBlank()) return emptySet()
        // 去掉括号，按 AND/OR/NOT 分割
        val cleaned = expression.replace("(", " ").replace(")", " ")
        val parts = cleaned.split(Regex("\\s+AND\\s+|\\s+OR\\s+|\\s+NOT\\s+|\\s+and\\s+|\\s+or\\s+|\\s+not\\s+"))
            .flatMap { it.split(Regex("\\s+")) }
            .map { it.trim().replace("\"", "") }
            .filter { it.isNotBlank() && it.uppercase() !in setOf("AND", "OR", "NOT") }
        return parts.toSet()
    }

    // ── Build helpers ──

    private fun buildPeriod(data: com.tagora.app.ai.model.PeriodOpData, nameToId: Map<String, String>): TimePeriod {
        val tagIds = data.tags?.map { tagName ->
            nameToId[tagName] ?: throw IllegalArgumentException("标签 '$tagName' 不存在")
        } ?: emptyList()

        return TimePeriod(
            id = newId(),
            name = data.name!!,
            type = data.type!!,
            startMinute = data.startTime?.let { AiJsonParser.parseTimeToMinutes(it) } ?: 0,
            endMinute = data.endTime?.let { AiJsonParser.parseTimeToMinutes(it) } ?: 0,
            color = data.color!!,
            tagIds = tagIds,
            dayOfWeeks = data.daysOfWeek?.let { AiJsonParser.parseDaysOfWeek(it) } ?: emptyList(),
            startDate = data.startDate,
            endDate = data.endDate,
        )
    }

    private fun mergePeriod(
        existing: TimePeriod,
        data: com.tagora.app.ai.model.PeriodOpData,
        nameToId: Map<String, String>,
    ): TimePeriod {
        val newTags = data.tags?.let { tagNames ->
            tagNames.map { name -> nameToId[name] ?: throw IllegalArgumentException("标签 '$name' 不存在") }
        }
        return existing.copy(
            name = data.name ?: existing.name,
            startMinute = data.startTime?.let { AiJsonParser.parseTimeToMinutes(it) } ?: existing.startMinute,
            endMinute = data.endTime?.let { AiJsonParser.parseTimeToMinutes(it) } ?: existing.endMinute,
            color = data.color ?: existing.color,
            tagIds = newTags ?: existing.tagIds,
            dayOfWeeks = data.daysOfWeek?.let { AiJsonParser.parseDaysOfWeek(it) } ?: existing.dayOfWeeks,
            startDate = data.startDate ?: existing.startDate,
            endDate = data.endDate ?: existing.endDate,
        )
    }

    private fun newId(): String = UUID.randomUUID().toString().take(8)
}
