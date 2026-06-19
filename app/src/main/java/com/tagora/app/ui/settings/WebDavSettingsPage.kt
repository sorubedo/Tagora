package com.tagora.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tagora.app.data.model.WebDavConfig
import com.tagora.app.ui.components.CardGroup
import com.tagora.app.ui.components.CardGroupItem
import com.tagora.app.util.WebDavFileInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSettingsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: WebDavViewModel = viewModel {
        WebDavViewModel(context.applicationContext)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showRestoreDialog by remember { mutableStateOf<WebDavFileInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebDAV 备份") },
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
            // 服务器配置
            CardGroup(title = { Text("服务器配置") }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val config = uiState.webDavConfig
                    OutlinedTextField(
                        value = config.url,
                        onValueChange = { viewModel.updateConfig(config.copy(url = it)) },
                        label = { Text("服务器地址") },
                        placeholder = { Text("https://dav.example.com/backup") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.username,
                        onValueChange = { viewModel.updateConfig(config.copy(username = it)) },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = config.password,
                        onValueChange = { viewModel.updateConfig(config.copy(password = it)) },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 操作
            CardGroup(title = { Text("操作") }) {
                // 测试连接
                CardGroupItem(isLast = false) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("测试连接", style = MaterialTheme.typography.bodyLarge)
                            Text("验证服务器配置是否正确", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(onClick = { viewModel.testConnection() }) {
                                Icon(Icons.Filled.Wifi, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("测试")
                            }
                        }
                    }
                }
                // 测试结果
                uiState.testResult?.let { msg ->
                    CardGroupItem(isLast = false) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = if (msg == "连接成功") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                // 立即备份
                CardGroupItem(isLast = false) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("立即备份", style = MaterialTheme.typography.bodyLarge)
                            Text("将全部数据备份到 WebDAV 服务器", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(onClick = { viewModel.doBackup() }) {
                                Icon(Icons.Filled.CloudUpload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("备份")
                            }
                        }
                    }
                }
            }

            // 从备份恢复
            CardGroup(title = { Text("从备份恢复") }) {
                CardGroupItem(isLast = false) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("备份列表", style = MaterialTheme.typography.bodyLarge)
                            Text("从服务器获取备份文件列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.isListing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(onClick = { viewModel.listBackups() }) {
                                Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("获取")
                            }
                        }
                    }
                }

                if (uiState.backupFiles.isEmpty() && !uiState.isListing) {
                    CardGroupItem(isLast = true) {
                        Text(
                            text = "点击「获取」查看备份文件列表",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                uiState.backupFiles.forEachIndexed { index, file ->
                    CardGroupItem(
                        onClick = { showRestoreDialog = file },
                        isLast = index == uiState.backupFiles.lastIndex,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${file.displayTime} · ${file.displaySize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // 恢复确认弹窗
    showRestoreDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text("确认恢复？") },
            text = { Text("将从备份「${file.name}」恢复所有数据。\n\n当前数据将被覆盖，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.doRestore(file); showRestoreDialog = null },
                    enabled = !uiState.isRestoring,
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("确认恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) { Text("取消") }
            },
        )
    }
}
