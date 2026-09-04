package com.ht.apigent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ht.apigent.agent.ResponsesClient
import kotlinx.coroutines.launch

/**
 * 配置管理弹窗：列出所有配置，点击即设为启用，支持新增/编辑/删除。
 */

/** API Key 脱敏：保留前 3 后 4 位，中间以 **** 代替；过短则全部打码 */
internal fun maskApiKey(key: String): String {
    val k = key.trim()
    if (k.isEmpty()) return ""
    return if (k.length <= 8) {
        "*".repeat(k.length)
    } else {
        k.take(3) + "****" + k.takeLast(4)
    }
}

@Composable
fun ConfigManagerDialog(
    configs: List<ResponsesClient.Config>,
    activeId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ResponsesClient.Config) -> Unit,
    onDelete: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.88f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "连接配置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "当前启用：${configs.firstOrNull { it.id == activeId }?.name ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(configs, key = { it.id }) { cfg ->
                        val isActive = cfg.id == activeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(cfg.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    cfg.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append("${cfg.model} · ${cfg.baseUrl}")
                                        val masked = maskApiKey(cfg.apiKey)
                                        if (masked.isNotEmpty()) append(" · Key: $masked")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onEdit(cfg) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDelete(cfg.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }

                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("新增配置")
                }
            }
        }
    }
}

/**
 * 单个配置编辑弹窗。isNew=true 保存时同时设为启用。
 */
@Composable
fun ConfigEditorDialog(
    config: ResponsesClient.Config,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ResponsesClient.Config) -> Unit,
) {
    var name by remember { mutableStateOf(config.name) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var systemPrompt by remember { mutableStateOf(config.systemPrompt) }

    val responsesClient = remember { ResponsesClient() }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }

    fun fetchModelsAction() {
        val b = baseUrl.trim()
        val k = apiKey.trim()
        if (b.isBlank()) {
            modelError = "请先填写 Base URL"
            return
        }
        loadingModels = true
        modelError = null
        models = null
        scope.launch {
            responsesClient.fetchModels(b, k)
                .onSuccess { list ->
                    loadingModels = false
                    if (list.isEmpty()) {
                        modelError = "未获取到模型（端点或权限可能不匹配）"
                        models = null
                    } else {
                        models = list
                        showModelPicker = true
                    }
                }
                .onFailure { e ->
                    loadingModels = false
                    modelError = (e.message ?: "获取失败").take(200)
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增配置" else "编辑配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    singleLine = true,
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    supportingText = { Text("默认 https://api.openai.com/v1，兼容任意网关", fontSize = 11.sp) },
                    singleLine = true,
                    trailingIcon = {
                        if (baseUrl.isNotEmpty()) {
                            IconButton(onClick = { baseUrl = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    // 始终脱敏显示：密文（圆点）展示，不提供明文切换按钮，避免 Key 泄露
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("已保存的 Key 以密文显示；需更换请先点 ✕ 清空再粘贴新 Key", fontSize = 11.sp) },
                    trailingIcon = {
                        if (apiKey.isNotEmpty()) {
                            IconButton(onClick = { apiKey = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                )
                // 模型获取：拉取模型列表，点击即可填入模型字段（位于模型框上方）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { fetchModelsAction() },
                        enabled = !loadingModels,
                    ) {
                        if (loadingModels) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("获取模型列表")
                        }
                    }
                    if (modelError != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            modelError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!models.isNullOrEmpty()) {
                    Text(
                        "已获取 ${models!!.size} 个模型，可在弹窗中选择",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 模型输入框：普通输入框，拉取的模型在弹窗中选择回填
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    supportingText = { Text("如 gpt-4o-mini / gpt-4o / deepseek-chat", fontSize = 11.sp) },
                    singleLine = true,
                    trailingIcon = {
                        if (model.isNotEmpty()) {
                            IconButton(onClick = { model = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    minLines = 2,
                    maxLines = 4,
                    trailingIcon = {
                        if (systemPrompt.isNotEmpty()) {
                            IconButton(onClick = { systemPrompt = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    config.copy(
                        name = name.ifBlank { "未命名配置" },
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim().ifBlank { "gpt-4o-mini" },
                        systemPrompt = systemPrompt,
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    // 获取模型列表结果弹窗：选择某一模型后自动回填到输入框
    if (showModelPicker && !models.isNullOrEmpty()) {
        ModelPickerDialog(
            models = models!!,
            currentModel = model,
            onDismiss = { showModelPicker = false },
            onSelect = { m ->
                model = m
                showModelPicker = false
            },
        )
    }
}

/**
 * 模型选择弹窗：列出拉取到的模型，点击即回填到模型输入框。
 */
@Composable
fun ModelPickerDialog(
    models: List<String>,
    currentModel: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(models) { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(m) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (m == currentModel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (m == currentModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(m, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
