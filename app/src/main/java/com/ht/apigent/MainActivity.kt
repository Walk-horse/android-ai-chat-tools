package com.ht.apigent

import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ht.apigent.agent.ChatViewModel
import com.ht.apigent.agent.ResponsesClient
import com.ht.apigent.curl.CurlViewModel
import com.ht.apigent.ui.ChatContent
import com.ht.apigent.ui.ConfigEditorDialog
import com.ht.apigent.ui.ConfigManagerDialog
import com.ht.apigent.ui.CurlContent
import com.ht.apigent.ui.CurlHistoryDialog
import com.ht.apigent.ui.ToolContent
import com.ht.apigent.ui.toolPageTitle
import com.ht.apigent.ui.TOOL_ID_CURL
import com.ht.apigent.ui.TOOL_PAGE_LIST
import com.ht.apigent.ui.WebContent
import com.ht.apigent.ui.MineContent
import com.ht.apigent.ui.theme.ApiAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApiAgentTheme {
                MainScaffold(startTab = intent.getIntExtra("start_tab", 0), startUrl = intent.getStringExtra("start_url"))
            }
        }
    }
}

/**
 * 用 ViewTreeObserver + getWindowVisibleDisplayFrame 检测键盘是否弹起。
 * 该方案不拦截 Compose 的 WindowInsets 链路，不会破坏 adjustResize。
 */
@Composable
private fun rememberImeVisible(): Boolean {
    val view = LocalView.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keyboardHeight = screenHeight - rect.bottom
            state.value = keyboardHeight > screenHeight * 0.15
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return state.value
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(startTab: Int = 0, startUrl: String? = null) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var showConfigManager by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ResponsesClient.Config?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val imeVisible = rememberImeVisible()
    val chatVm: ChatViewModel = viewModel()
    val curlVm: CurlViewModel = viewModel()
    var showCurlHistory by remember { mutableStateOf(false) }
    // 工具 tab 的当前子页面：列表页 / 具体工具页（提升到此处，便于 TopAppBar 联动标题与按钮）
    var toolPage by rememberSaveable { mutableStateOf(TOOL_PAGE_LIST) }

    Scaffold(
        topBar = {
            // 浏览器 tab 自带地址栏，不显示 TopAppBar
            if (tab != 1) {
                TopAppBar(
                    title = {
                        Text(
                            when (tab) {
                                0 -> "Chat 对话"
                                2 -> toolPageTitle(toolPage)
                                3 -> "我的"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    // 工具子页面提供返回列表的入口
                    navigationIcon = {
                        if (tab == 2 && toolPage != TOOL_PAGE_LIST) {
                            IconButton(onClick = { toolPage = TOOL_PAGE_LIST }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { chatVm.clearChat() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空对话")
                        }
                        IconButton(onClick = { showConfigManager = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "配置")
                        }
                    } else if (tab == 2 && toolPage == TOOL_ID_CURL) {
                        IconButton(onClick = { curlVm.fillExample() }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "示例")
                        }
                        IconButton(onClick = { showCurlHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = "历史")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            }
        },
        bottomBar = {
            // 键盘弹起时隐藏底部 Tab，让输入框直接贴着键盘，消除二者之间的空白
            if (!imeVisible) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = {
                                Text(
                                    "Chat",
                                    fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = navItemColors(),
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = {
                                Text(
                                    "浏览器",
                                    fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = navItemColors(),
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = {
                                Text(
                                    "工具",
                                    fontWeight = if (tab == 2) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = navItemColors(),
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = {
                                Text(
                                    "我的",
                                    fontWeight = if (tab == 3) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = navItemColors(),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            0 -> ChatContent(modifier = Modifier.padding(padding), vm = chatVm)
            1 -> WebContent(modifier = Modifier.padding(padding), startUrl = startUrl)
            2 -> ToolContent(
                modifier = Modifier.padding(padding),
                snackbarHostState = snackbar,
                vm = curlVm,
                page = toolPage,
                onOpenTool = { toolPage = it },
            )
            3 -> MineContent(modifier = Modifier.padding(padding))
        }
    }

    if (showConfigManager) {
        ConfigManagerDialog(
            configs = chatVm.configs.value,
            activeId = chatVm.activeId.value,
            onDismiss = { showConfigManager = false },
            onSelect = { id ->
                chatVm.setActive(id)
                showConfigManager = false
            },
            onAdd = {
                editingConfig = ResponsesClient.Config(
                    name = "新配置",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "",
                    model = "gpt-4o-mini",
                )
                editingIsNew = true
                showConfigManager = false
            },
            onEdit = {
                editingConfig = it
                editingIsNew = false
            },
            onDelete = { chatVm.deleteConfig(it) },
        )
    }

    editingConfig?.let { cfg ->
        ConfigEditorDialog(
            config = cfg,
            isNew = editingIsNew,
            onDismiss = { editingConfig = null },
            onSave = {
                chatVm.saveConfig(it, setActive = editingIsNew)
                editingConfig = null
            },
        )
    }

    if (showCurlHistory) {
        CurlHistoryDialog(
            history = curlVm.history,
            onDismiss = { showCurlHistory = false },
            onLoad = { item ->
                curlVm.command = item
                curlVm.parse()
                showCurlHistory = false
            },
            onDelete = { curlVm.removeHistory(it) },
            onClear = { curlVm.clearHistory() },
        )
    }
}
