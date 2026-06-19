package com.tagora.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagora.app.data.AppPreferences
import com.tagora.app.data.RepositoryProvider
import com.tagora.app.data.model.MergedFullConfig
import com.tagora.app.data.model.WebDavConfig
import com.tagora.app.util.WebDavClient
import com.tagora.app.util.WebDavFileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WebDavUiState(
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isBackingUp: Boolean = false,
    val isListing: Boolean = false,
    val backupFiles: List<WebDavFileInfo> = emptyList(),
    val isRestoring: Boolean = false,
    val webDavConfig: WebDavConfig = WebDavConfig(),
)

class WebDavViewModel(
    private val appContext: Context,
) : ViewModel() {

    private val json = Json {
        serializersModule = com.tagora.app.data.model.TaskConditionSerializersModule
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val prefs = AppPreferences(appContext)

    private val _uiState = MutableStateFlow(
        WebDavUiState(
            webDavConfig = try {
                json.decodeFromString<WebDavConfig>(prefs.webDavConfigJson)
            } catch (_: Exception) {
                WebDavConfig()
            }
        )
    )
    val uiState: StateFlow<WebDavUiState> = _uiState.asStateFlow()

    fun updateConfig(config: WebDavConfig) {
        prefs.webDavConfigJson = json.encodeToString(config)
        _uiState.value = _uiState.value.copy(webDavConfig = config)
    }

    fun testConnection() {
        val config = _uiState.value.webDavConfig
        if (!config.isValid) {
            Toast.makeText(appContext, "请填写服务器地址、用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)
        viewModelScope.launch {
            val client = WebDavClient(config)
            val result = client.testConnection()
            _uiState.value = _uiState.value.copy(
                isTesting = false,
                testResult = result.getOrElse { it.message ?: "未知错误" },
            )
        }
    }

    fun doBackup() {
        val config = _uiState.value.webDavConfig
        if (!config.isValid) {
            Toast.makeText(appContext, "请先配置并测试连接", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.value = _uiState.value.copy(isBackingUp = true)
        viewModelScope.launch {
            try {
                val repository = RepositoryProvider.get(appContext)
                val taskRepo = RepositoryProvider.getTaskRepo(appContext)
                val completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(appContext)

                val data = MergedFullConfig(
                    tags = repository.tagsFlow.first(),
                    periods = repository.periodsFlow.first(),
                    weeklyPeriods = repository.weeklyPeriodsFlow.first(),
                    datePeriods = repository.datePeriodsFlow.first(),
                    deadlinePeriods = repository.deadlinePeriodsFlow.first(),
                    tasks = taskRepo.tasksFlow.first(),
                    completedTasks = completedTaskRepo.completedTasksFlow.first(),
                )
                val jsonStr = json.encodeToString(data)
                val timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val fileName = "${WebDavClient.BACKUP_PREFIX}${timestamp}.json"

                val client = WebDavClient(config)
                client.upload(fileName, jsonStr.toByteArray()).getOrThrow()

                // 清理旧备份（保留最近 10 个）
                val listResult = client.listBackups()
                listResult.onSuccess { files ->
                    if (files.size > 10) {
                        files.drop(10).forEach { old -> client.delete(old.name) }
                    }
                }
                Toast.makeText(appContext, "备份成功：$fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "备份失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
            _uiState.value = _uiState.value.copy(isBackingUp = false)
        }
    }

    fun listBackups() {
        val config = _uiState.value.webDavConfig
        if (!config.isValid) return
        _uiState.value = _uiState.value.copy(isListing = true, backupFiles = emptyList())
        viewModelScope.launch {
            val client = WebDavClient(config)
            val result = client.listBackups()
            _uiState.value = _uiState.value.copy(
                isListing = false,
                backupFiles = result.getOrDefault(emptyList()),
            )
            if (result.isFailure) {
                Toast.makeText(appContext, "获取备份列表失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun doRestore(file: WebDavFileInfo) {
        _uiState.value = _uiState.value.copy(isRestoring = true)
        viewModelScope.launch {
            try {
                val client = WebDavClient(_uiState.value.webDavConfig)
                val downloadResult = client.download(file.name)
                val jsonStr = String(downloadResult.getOrThrow())
                val config = json.decodeFromString<MergedFullConfig>(jsonStr)
                val repository = RepositoryProvider.get(appContext)
                val taskRepo = RepositoryProvider.getTaskRepo(appContext)
                val completedTaskRepo = RepositoryProvider.getCompletedTaskRepo(appContext)

                repository.saveTags(config.tags)
                repository.savePeriods(config.periods)
                repository.saveWeeklyPeriods(config.weeklyPeriods)
                repository.saveDatePeriods(config.datePeriods)
                repository.saveDeadlinePeriods(config.deadlinePeriods)
                taskRepo.saveTasks(config.tasks)
                completedTaskRepo.saveCompletedTasks(config.completedTasks)
                Toast.makeText(appContext, "恢复成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(appContext, "恢复失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
            _uiState.value = _uiState.value.copy(isRestoring = false)
        }
    }

    fun doDeleteBackup(file: WebDavFileInfo, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val client = WebDavClient(_uiState.value.webDavConfig)
                client.delete(file.name).getOrThrow()
                onDeleted()
            } catch (e: Exception) {
                Toast.makeText(appContext, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
