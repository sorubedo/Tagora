package com.tagora.app.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tagora.app.ai.executor.AiSheetExecutor
import com.tagora.app.ai.model.AiExecutionResult
import com.tagora.app.ai.parser.AiJsonParser
import com.tagora.app.data.RepositoryProvider
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 用于展示执行结果的 JSON 格式化器 */
private val resultJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

/** 示例 JSON */
val SAMPLE_JSON = """
{
  "operations": [
    {
      "action": "create",
      "target": "tag",
      "data": { "name": "空闲", "color": "#FF66BB6A" }
    },
    {
      "action": "create",
      "target": "tag",
      "data": { "name": "阅读", "color": "#FF3DD6D0" }
    },
    {
      "action": "create",
      "target": "period",
      "data": {
        "type": "daily",
        "name": "午后空闲",
        "startTime": "14:00",
        "endTime": "16:00",
        "color": "#FF66BB6A",
        "tags": ["空闲", "阅读"]
      }
    },
    {
      "action": "create",
      "target": "task",
      "data": {
        "name": "读书30分钟",
        "description": "午后空闲时间阅读",
        "type": "normal",
        "condition": "空闲 AND 阅读"
      }
    },
    {
      "action": "query",
      "target": "period",
      "data": { "type": "daily", "tags": ["空闲"] }
    },
    {
      "action": "query",
      "target": "task",
      "data": { "status": "incomplete" }
    }
  ]
}
""".trimIndent()

/**
 * AI 操作调试页面（仅 DEBUG 构建可用）。
 * 提供 JSON 输入框，解析并执行 AI 操作表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDebugPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var jsonInput by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isExecuting by remember { mutableStateOf(false) }

    val executor = remember {
        AiSheetExecutor(
            periodRepo = RepositoryProvider.get(context),
            taskRepo = RepositoryProvider.getTaskRepo(context),
            completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(context),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 操作调试") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // JSON 输入框
            OutlinedTextField(
                value = jsonInput,
                onValueChange = { jsonInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                label = { Text("JSON 操作表单") },
                placeholder = { Text("在此粘贴 JSON 操作表单...") },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = Int.MAX_VALUE,
            )

            // 按钮行
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 加载示例
                OutlinedButton(
                    onClick = { jsonInput = SAMPLE_JSON },
                    enabled = !isExecuting,
                ) {
                    Text("加载示例")
                }

                // 清空
                OutlinedButton(
                    onClick = {
                        jsonInput = ""
                        resultText = ""
                    },
                    enabled = !isExecuting,
                ) {
                    Text("清空")
                }
            }

            // 执行按钮
            Button(
                onClick = {
                    scope.launch {
                        isExecuting = true
                        isError = false
                        resultText = ""
                        try {
                            val sheet = AiJsonParser.parse(jsonInput)
                            val result = executor.execute(sheet)
                            isError = !result.success
                            resultText = formatResult(result)
                        } catch (e: Exception) {
                            isError = true
                            resultText = "解析错误：${e.message}"
                        }
                        isExecuting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExecuting && jsonInput.isNotBlank(),
            ) {
                Text(if (isExecuting) "执行中..." else "执行")
            }

            // 结果展示
            if (resultText.isNotBlank()) {
                Text(
                    text = if (isError) "执行结果（错误）" else "执行结果",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 格式化执行结果为 JSON 字符串。
 */
private fun formatResult(result: AiExecutionResult): String {
    return try {
        resultJson.encodeToString(AiExecutionResult.serializer(), result)
    } catch (e: Exception) {
        // 降级：手动格式化
        buildString {
            appendLine("success: ${result.success}")
            appendLine("executed: ${result.executed}")
            appendLine("errors: ${result.errors}")
            if (result.results != null) {
                appendLine("results:")
                appendLine("  tags.created: ${result.results.tags.created.size}")
                appendLine("  tags.updated: ${result.results.tags.updated.size}")
                appendLine("  tags.deleted: ${result.results.tags.deleted.size}")
                appendLine("  periods.created: ${result.results.periods.created.size}")
                appendLine("  periods.updated: ${result.results.periods.updated.size}")
                appendLine("  periods.deleted: ${result.results.periods.deleted.size}")
                appendLine("  tasks.created: ${result.results.tasks.created.size}")
                appendLine("  tasks.updated: ${result.results.tasks.updated.size}")
                appendLine("  tasks.deleted: ${result.results.tasks.deleted.size}")
                appendLine("  queries.tags: ${result.results.queries.tags.size} queries, ${result.results.queries.tags.sumOf { it.size }} results")
                appendLine("  queries.periods: ${result.results.queries.periods.size} queries, ${result.results.queries.periods.sumOf { it.size }} results")
                appendLine("  queries.tasks: ${result.results.queries.tasks.size} queries, ${result.results.queries.tasks.sumOf { it.size }} results")
            }
        }
    }
}
