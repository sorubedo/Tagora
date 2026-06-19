package com.tagora.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * 标签条件密封接口，支持 AND/OR/NOT 逻辑组合。
 * 通过多态序列化存储到 JSON，evaluate 方法根据当前激活的标签集合计算结果。
 */
@Serializable
sealed interface TaskCondition {
    /** 根据当前激活的标签 ID 集合，计算条件是否满足 */
    fun evaluate(activeTagIds: Set<String>): Boolean
}

/** 多个标签中任一激活即可 */
@Serializable
@SerialName("multi")
data class MultiTagCondition(val tagIds: List<String> = emptyList()) : TaskCondition {
    override fun evaluate(activeTagIds: Set<String>) =
        if (tagIds.isEmpty()) true
        else tagIds.any { it in activeTagIds }
}

/** 所有子条件必须同时满足 */
@Serializable
@SerialName("and")
data class AndCondition(val conditions: List<TaskCondition> = emptyList()) : TaskCondition {
    override fun evaluate(activeTagIds: Set<String>) =
        conditions.all { it.evaluate(activeTagIds) }
}

/** 任一子条件满足即可 */
@Serializable
@SerialName("or")
data class OrCondition(val conditions: List<TaskCondition> = emptyList()) : TaskCondition {
    override fun evaluate(activeTagIds: Set<String>) =
        if (conditions.isEmpty()) true
        else conditions.any { it.evaluate(activeTagIds) }
}

/** 子条件取反 */
@Serializable
@SerialName("not")
data class NotCondition(val condition: TaskCondition) : TaskCondition {
    override fun evaluate(activeTagIds: Set<String>) =
        !condition.evaluate(activeTagIds)
}

/** 递归收集条件树中所有引用的标签 ID */
fun TaskCondition.collectTagIds(): Set<String> = when (this) {
    is MultiTagCondition -> tagIds.toSet()
    is AndCondition -> conditions.flatMap { it.collectTagIds() }.toSet()
    is OrCondition -> conditions.flatMap { it.collectTagIds() }.toSet()
    is NotCondition -> condition.collectTagIds()
}

/** 多态序列化模块，需注册到 Json 实例 */
val TaskConditionSerializersModule = SerializersModule {
    polymorphic(TaskCondition::class) {
        subclass(MultiTagCondition::class, MultiTagCondition.serializer())
        subclass(AndCondition::class, AndCondition.serializer())
        subclass(OrCondition::class, OrCondition.serializer())
        subclass(NotCondition::class, NotCondition.serializer())
    }
}

@Serializable
data class Task(
    val id: String,
    val name: String,
    val description: String = "",
    val condition: TaskCondition = AndCondition(emptyList()),
    val type: String = "normal",         // "normal" 普通事件 / "fixed" 固定事件
    val status: String = "incomplete",   // "incomplete" 未完成 / "completed" 已完成 / "timeout" 超时
    val completedAt: Long? = null,       // 完成/超时的时间戳（epoch millis）
)

@Serializable
data class TaskConfig(
    val version: Int = 2,
    val tasks: List<Task> = emptyList(),
)
