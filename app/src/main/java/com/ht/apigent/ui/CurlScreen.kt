package com.ht.apigent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ht.apigent.curl.CurlRequest
import com.ht.apigent.curl.CurlResponse
import com.ht.apigent.curl.CurlViewModel
import com.ht.apigent.util.JsonFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurlContent(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    vm: CurlViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    var resultTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "粘贴 curl 命令，自动解析后一键发送",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = vm.command,
            onValueChange = { vm.command = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 12,
            placeholder = { Text("curl -X POST \"https://...\" \\\n  -H \"Authorization: Bearer xxx\" \\\n  -d '{\"key\":\"value\"}'") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = { vm.parse() },
                enabled = vm.command.isNotBlank() && !vm.executing,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("解析")
            }
            FilledTonalButton(
                onClick = {
                    vm.parse()
                    if (vm.parsed != null) vm.execute()
                },
                enabled = vm.command.isNotBlank() && !vm.executing,
            ) {
                if (vm.executing) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text("解析并发送")
            }
        }

        vm.parseError?.let { err ->
            ErrorCard(err)
        }

        vm.parseError?.let { err ->
            ErrorCard(err)
        }

        vm.parsed?.let { req ->
            RequestCard(req)
        }

        vm.response?.let { resp ->
            Spacer(Modifier.height(4.dp))
            Text("响应结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            TabRow(selectedTabIndex = resultTab) {
                Tab(
                    selected = resultTab == 0,
                    onClick = { resultTab = 0 },
                    text = { Text("Body") },
                )
                Tab(
                    selected = resultTab == 1,
                    onClick = { resultTab = 1 },
                    text = { Text("Headers") },
                )
            }

            ResponseCard(resp, resultTab)

            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("response", resp.body))
                    scope.launch { snackbarHostState.showSnackbar("响应已复制到剪贴板") }
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制响应")
            }
        }
    }

}

@Composable
fun CurlHistoryDialog(
    history: List<String>,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("历史记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("清空") }
                }
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    "暂无历史记录。发送成功的 curl 命令会自动保存到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(history) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(item) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                item.lines().first().trim().take(80),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.width(32.dp).height(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.width(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun ErrorCard(err: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "解析失败：$err",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun RequestCard(req: CurlRequest) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("解析结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodChip(req.method)
                Spacer(Modifier.width(8.dp))
                SelectionContainer {
                    Text(
                        req.url,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 12.sp,
                    )
                }
            }

            if (req.headers.isNotEmpty()) {
                Text("Headers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    req.headers.forEach { (k, v) ->
                        SelectionContainer {
                            Text(
                                "$k: $v",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            if (req.body != null) {
                Text("Body", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        JsonFormat.pretty(req.body),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodChip(method: String) {
    val color = when (method) {
        "GET" -> MaterialTheme.colorScheme.secondary
        "POST" -> MaterialTheme.colorScheme.primary
        "PUT", "PATCH" -> MaterialTheme.colorScheme.tertiary
        "DELETE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        method,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ResponseCard(resp: CurlResponse, tab: Int) {
    val statusColor = when {
        resp.statusCode != null && resp.statusCode in 200..299 -> MaterialTheme.colorScheme.secondary
        resp.statusCode != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HTTP ${resp.statusCode ?: "—"} · ${resp.durationMs}ms",
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            if (resp.error != null) {
                Text(
                    "请求失败：${resp.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            } else if (tab == 0) {
                val body = JsonFormat.pretty(resp.body).ifEmpty { "（空响应体）" }
                SelectionContainer {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                    )
                }
            } else {
                if (resp.headers.isEmpty()) {
                    Text("（无响应头）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        resp.headers.forEach { (k, v) ->
                            SelectionContainer {
                                Text(
                                    "$k: $v",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
