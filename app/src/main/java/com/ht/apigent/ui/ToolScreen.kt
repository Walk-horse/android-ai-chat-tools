package com.ht.apigent.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ht.apigent.curl.CurlViewModel

/** 工具 tab 的页面路由：列表页 / 各工具页面（值即工具 id） */
const val TOOL_PAGE_LIST = "list"
const val TOOL_ID_CURL = "curl"
const val TOOL_ID_BASE64 = "base64"

/** 工具页标题：列表页返回「工具」，子页面返回对应工具名 */
fun toolPageTitle(page: String): String = when (page) {
    TOOL_ID_CURL -> "curl 模拟调用"
    TOOL_ID_BASE64 -> "Base64 编解码"
    else -> "工具"
}

private data class ToolEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

// 工具清单：新增工具时在此追加一项，并在 ToolContent 的 when 里接入对应页面
private val toolEntries = listOf(
    ToolEntry(
        id = TOOL_ID_CURL,
        title = "curl 模拟调用",
        subtitle = "粘贴 curl 命令，自动解析并一键发送",
        icon = Icons.Default.Terminal,
    ),
    ToolEntry(
        id = TOOL_ID_BASE64,
        title = "Base64 编解码",
        subtitle = "文本与 Base64 互转，支持一键复制",
        icon = Icons.Default.Code,
    ),
)

/** 工具 tab 容器：page 为列表页时展示工具清单，否则展示对应工具页面 */
@Composable
fun ToolContent(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    vm: CurlViewModel,
    page: String,
    onOpenTool: (String) -> Unit,
) {
    when (page) {
        TOOL_ID_CURL -> CurlContent(
            modifier = modifier,
            snackbarHostState = snackbarHostState,
            vm = vm,
        )
        TOOL_ID_BASE64 -> Base64ToolContent(modifier = modifier)
        else -> ToolListContent(modifier = modifier, onOpen = onOpenTool)
    }
}

@Composable
private fun ToolListContent(
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "选择要使用的工具",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        toolEntries.forEachIndexed { index, tool ->
            ToolListItem(tool = tool, onClick = { onOpen(tool.id) })
            if (index < toolEntries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ToolListItem(tool: ToolEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            tool.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tool.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
