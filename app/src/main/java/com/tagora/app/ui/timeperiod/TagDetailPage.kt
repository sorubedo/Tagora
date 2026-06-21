package com.tagora.app.ui.timeperiod

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.Tag
import com.tagora.app.domain.usecase.DeleteTagUseCase
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.FormItem
import com.tagora.app.ui.timeperiod.components.ColorPicker
import com.tagora.app.util.newId
import com.tagora.app.ui.timeperiod.components.PresetPeriodColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailPage(
    tagId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = RepositoryProvider.get(context)

    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PresetPeriodColors[0]) }
    var idInput by remember { mutableStateOf(newId()) }
    var loaded by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val tags = repository.tagsFlow.first()
        val tag = tags.find { it.id == tagId }
        if (tag != null) {
            name = tag.name
            selectedColor = tag.color
            idInput = tag.id
        }
        loaded = true
    }

    val isNew = tagId == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建标签" else "编辑标签") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup(title = { Text("标签 ID") }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isNew) "新建时可自定义 ID，保存后不可修改" else "编辑已有标签时 ID 不可修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idInput,
                        onValueChange = { newValue ->
                            idInput = newValue
                            idError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = !isNew,
                        isError = idError != null,
                        supportingText = idError?.let { { Text(it) } },
                        placeholder = { Text("例如：t-class-13、t-w21") },
                    )
                }
            }

            CardGroup {
                FormItem(
                    label = { Text("标签名") },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("例如：工作、学习、运动") },
                    )
                }
            }

            CardGroup(title = { Text("颜色") }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ColorPicker(
                        colors = PresetPeriodColors,
                        selectedColor = selectedColor,
                        onColorSelected = { selectedColor = it },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val tags = repository.tagsFlow.first()
                        // 新建时校验 ID 唯一性
                        if (isNew && tags.any { it.id == idInput }) {
                            idError = "此 ID 已被占用，请使用其他 ID"
                            return@launch
                        }
                        val tag = Tag(id = idInput, name = name.ifBlank { "未命名" }, color = selectedColor)
                        val newTags = if (isNew) tags + tag else tags.map { if (it.id == idInput) tag else it }
                        repository.saveTags(newTags)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && idInput.isNotBlank(),
            ) { Text("保存") }

            if (!isNew) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            DeleteTagUseCase(repository).execute(tagId)
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除此标签") }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
