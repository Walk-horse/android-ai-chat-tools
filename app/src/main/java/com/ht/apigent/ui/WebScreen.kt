package com.ht.apigent.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Message
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private const val HOME_URL = "https://www.baidu.com"
private const val PREFS_NAME = "web_history_prefs"
private const val HISTORY_KEY = "history_items"
private const val MAX_HISTORY = 50
private const val MAX_BOOKMARKS = 100
private const val BOOKMARKS_KEY = "bookmark_items"
private const val DEFAULT_HOME_KEY = "default_home_url"

// 桌面版 User-Agent：使网页以 PC 版样式加载（请求桌面站点）
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

// 移动版 User-Agent：兼容按 UA 分支渲染的站点（部分站点桌面分支在 WebView 渲染异常）
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

private fun normalizeUrl(raw: String): String {
    val r = raw.trim()
    if (r.isEmpty()) return r
    if (!r.contains("://")) return "https://$r"
    return r
}

// 用 Chrome Custom Tabs 打开（完整 Chrome 引擎，兼容性最佳）；无可用浏览器时回退系统浏览器
private fun openInCustomTabs(context: Context, url: String) {
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(normalizeUrl(url)))
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url))))
        }
    }
}

private fun loadHistory(context: Context): MutableList<WebHistoryItem> {
    val list = mutableListOf<WebHistoryItem>()
    try {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(HISTORY_KEY, null) ?: return list
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(WebHistoryItem(obj.getString("url"), obj.getString("title"), obj.getLong("time")))
        }
    } catch (_: Exception) {}
    return list
}

private fun saveHistory(context: Context, items: List<WebHistoryItem>) {
    try {
        val arr = JSONArray()
        items.take(MAX_HISTORY).forEach { item ->
            arr.put(JSONObject().apply {
                put("url", item.url)
                put("title", item.title)
                put("time", item.time)
            }
        )}
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, arr.toString()).apply()
    } catch (_: Exception) {}
}

data class WebHistoryItem(val url: String, val title: String, val time: Long)

// ---------- 收藏夹 ----------

private fun loadBookmarks(context: Context): MutableList<WebBookmarkItem> {
    val list = mutableListOf<WebBookmarkItem>()
    try {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(BOOKMARKS_KEY, null) ?: return list
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(WebBookmarkItem(obj.getString("url"), obj.getString("title"), obj.getLong("time")))
        }
    } catch (_: Exception) {}
    return list
}

private fun saveBookmarks(context: Context, items: List<WebBookmarkItem>) {
    try {
        val arr = JSONArray()
        items.take(MAX_BOOKMARKS).forEach { item ->
            arr.put(JSONObject().apply {
                put("url", item.url)
                put("title", item.title)
                put("time", item.time)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(BOOKMARKS_KEY, arr.toString()).apply()
    } catch (_: Exception) {}
}

private fun loadDefaultHome(context: Context): String {
    val saved = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(DEFAULT_HOME_KEY, null)
    }.getOrNull()
    return if (!saved.isNullOrBlank()) saved else HOME_URL
}

private fun saveDefaultHome(context: Context, url: String) {
    runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(DEFAULT_HOME_KEY, url).apply()
    }
}

data class WebBookmarkItem(val url: String, val title: String, val time: Long)

@Composable
fun WebContent(modifier: Modifier = Modifier, startUrl: String? = null) {
    val context = LocalContext.current
    var defaultHome by remember { mutableStateOf(loadDefaultHome(context)) }
    val initialUrl = startUrl?.takeIf { it.isNotBlank() } ?: defaultHome

    var urlInput by remember { mutableStateOf(TextFieldValue(initialUrl)) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val historyItems = remember { mutableStateListOf<WebHistoryItem>().also { it.addAll(loadHistory(context)) } }
    var showHistory by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(false) }
    var showBlankHint by remember { mutableStateOf(false) }
    // 收藏夹
    val bookmarkItems = remember { mutableStateListOf<WebBookmarkItem>().also { it.addAll(loadBookmarks(context)) } }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    // 调试模式：开启 WebView 远程调试，可在电脑端 Chrome 的 chrome://inspect/#devices 打开与 PC 一致的 DevTools
    var debugMode by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var addressBarHeight by remember { mutableStateOf(0.dp) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 键盘展开/收起状态（用于切换清空/刷新按钮的显隐）
    val view = LocalView.current
    var isKeyboardVisible by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            isKeyboardVisible = keypadHeight > screenHeight * 0.15
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            saveHistory(context, historyItems.toList())
            saveBookmarks(context, bookmarkItems.toList())
            webViewState.value?.destroy()
            webViewState.value = null
        }
    }

    // 调试模式：动态开关 WebView 远程调试（影响应用内所有 WebView）
    LaunchedEffect(debugMode) {
        WebView.setWebContentsDebuggingEnabled(debugMode)
    }

    fun navigate(url: String) {
        val u = normalizeUrl(url)
        if (u.isNotEmpty()) {
            currentUrl = u
            urlInput = TextFieldValue(u)
            webViewState.value?.loadUrl(u)
            val existingIdx = historyItems.indexOfFirst { it.url == u }
            if (existingIdx >= 0) historyItems.removeAt(existingIdx)
            historyItems.add(0, WebHistoryItem(u, "", System.currentTimeMillis()))
            if (historyItems.size > MAX_HISTORY) historyItems.removeAt(historyItems.lastIndex)
        }
    }

    fun toggleDesktopMode() {
        desktopMode = !desktopMode
        webViewState.value?.settings?.apply {
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
            loadWithOverviewMode = desktopMode
            useWideViewPort = desktopMode
        }
        webViewState.value?.reload()
    }

    /** 收藏开关：当前页已收藏则取消收藏，否则加入收藏 */
    fun toggleBookmark() {
        val u = currentUrl
        if (u.isBlank()) return
        val idx = bookmarkItems.indexOfFirst { it.url == u }
        if (idx >= 0) {
            bookmarkItems.removeAt(idx)
            saveBookmarks(context, bookmarkItems.toList())
            Toast.makeText(context, "已取消收藏", Toast.LENGTH_SHORT).show()
            return
        }
        val title = webViewState.value?.title ?: ""
        bookmarkItems.add(0, WebBookmarkItem(u, title, System.currentTimeMillis()))
        if (bookmarkItems.size > MAX_BOOKMARKS) bookmarkItems.removeAt(bookmarkItems.lastIndex)
        saveBookmarks(context, bookmarkItems.toList())
        Toast.makeText(context, "已加入收藏", Toast.LENGTH_SHORT).show()
    }

    fun setDefaultHome() {
        val u = currentUrl
        if (u.isBlank()) return
        defaultHome = u
        saveDefaultHome(context, u)
        Toast.makeText(context, "已设为默认页", Toast.LENGTH_SHORT).show()
    }

    // 清空浏览数据：按勾选项清除浏览记录 / 输入历史 / Cookie / 缓存 / 站点数据 / 前进后退 / 收藏
    fun clearBrowseData(opts: ClearDataOptions) {
        val wv = webViewState.value
        var cleared = 0
        if (opts.history) {
            historyItems.clear()
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(HISTORY_KEY).apply()
            }
            cleared++
        }
        if (opts.formData) {
            runCatching { wv?.clearFormData() }
            cleared++
        }
        if (opts.cookies) {
            runCatching {
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
            }
            cleared++
        }
        if (opts.cache) {
            runCatching {
                wv?.clearCache(true)
                context.cacheDir?.deleteRecursively()
            }
            cleared++
        }
        if (opts.siteData) {
            runCatching { WebStorage.getInstance().deleteAllData() }
            cleared++
        }
        if (opts.backForward) {
            runCatching { wv?.clearHistory() }
            cleared++
        }
        if (opts.bookmarks) {
            bookmarkItems.clear()
            saveBookmarks(context, emptyList())
            cleared++
        }
        // 清掉 Cookie / 缓存 / 站点数据后重载当前页，使登录态与数据立即失效
        if (opts.cookies || opts.cache || opts.siteData) {
            wv?.reload()
        }
        Toast.makeText(
            context,
            if (cleared > 0) "已清空 $cleared 项浏览数据" else "未选择要清空的数据",
            Toast.LENGTH_SHORT,
        ).show()
    }

    // 根容器用 Box：WebView 全屏填充，地址栏浮在顶部（避免 AndroidView 破坏 Column 布局测量）
    Box(modifier = modifier.fillMaxSize()) {
        // WebView 全屏底层
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        // 桌面模式才开启 wide viewport + overview mode；移动端关闭，避免响应式页面把 100vh 解析成异常值
                        loadWithOverviewMode = desktopMode
                        useWideViewPort = desktopMode
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        defaultTextEncodingName = "UTF-8"
                        loadsImagesAutomatically = true
                        allowFileAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        userAgentString = if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                        allowContentAccess = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let {
                                currentUrl = it
                                urlInput = TextFieldValue(it)
                            }
                            isLoading = true
                            showBlankHint = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            url?.let { u ->
                                val idx = historyItems.indexOfFirst { it.url == u }
                                if (idx >= 0 && idx < historyItems.size) {
                                    val title = view?.title ?: ""
                                    if (title.isNotBlank()) {
                                        historyItems[idx] = historyItems[idx].copy(title = title)
                                    }
                                }
                            }
                            // 空白页检测：延迟 1.5s 等 SPA 渲染完成，检查 body 是否真有内容
                            view?.postDelayed({
                                try {
                                    view.evaluateJavascript(
                                        "(function(){var b=document.body;if(!b)return 'false';var t=(b.innerText||'').replace(/\\s/g,'').length;var i=b.getElementsByTagName('img').length;var h=b.scrollHeight||0;return (t>0||i>0||h>40)?'true':'false';})()"
                                    ) { result ->
                                        if (result.contains("false")) showBlankHint = true
                                    }
                                } catch (_: Exception) {}
                            }, 1500L)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            isLoading = false
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        // 支持 target="_blank" / 弹窗：把新窗口目标 URL 收回主 WebView 加载
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                            val popup = WebView(view?.context ?: context).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                        val u = request?.url?.toString() ?: return false
                                        webViewState.value?.loadUrl(u)
                                        return true
                                    }
                                }
                            }
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    // 横向滑动手势：右滑返回上一页，左滑前进（替代被移除的前进/后退按钮）
                    val swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                            if (e1 == null) return false
                            val dx = e2.x - e1.x
                            val dy = e2.y - e1.y
                            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 120) {
                                if (dx > 0 && canGoBack()) { goBack(); return true }
                                if (dx < 0 && canGoForward()) { goForward(); return true }
                            }
                            return false
                        }
                    })
                    setOnTouchListener { _, event ->
                        // 点击页面其他位置：输入框失去焦点，清空按钮隐藏、刷新按钮展示
                        if (event?.action == MotionEvent.ACTION_DOWN && isInputFocused) {
                            focusRequester.freeFocus()
                            keyboardController?.hide()
                        }
                        swipeDetector.onTouchEvent(event)
                        false // 不消费事件，WebView 仍正常处理滚动/缩放
                    }
                    // 等 WebView 完成 layout、宽高非零后再加载 URL：避免 CSS 100vh/100dvh 在初始视口高度为 0 时解析为 0，导致页面整体高度塌陷空白
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    var loaded = false
                    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (!loaded && width > 0 && height > 0) {
                                loaded = true
                                loadUrl(initialUrl)
                                viewTreeObserver.removeOnGlobalLayoutListener(this)
                            }
                        }
                    })
                }.also { webViewState.value = it }
            },
            modifier = Modifier.fillMaxSize().padding(top = addressBarHeight),
        )

        // 地址栏 + 进度条：独占顶部条（WebView 已 inset 到其下方，不再遮挡网页内容）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .onSizeChanged { size -> addressBarHeight = with(density) { size.height.toDp() } },
        ) {
            // 地址栏行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆角输入区
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isInputFocused) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                val focused = state.isFocused
                                // 清空后未输入新地址、键盘收起时还原原地址
                                if (!focused && urlInput.text.isBlank()) {
                                    urlInput = TextFieldValue(currentUrl)
                                }
                                isInputFocused = focused
                            },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (urlInput.text.isEmpty()) {
                                    Text("搜索或输入网址", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            navigate(urlInput.text)
                            focusRequester.freeFocus()
                            keyboardController?.hide()
                        }),
                    )
                    Spacer(Modifier.width(4.dp))
                    // 输入框内动态按钮：键盘展开显示清空，键盘收起显示刷新/停止
                    IconButton(
                        onClick = {
                            if (isKeyboardVisible) {
                                urlInput = TextFieldValue("")
                            } else if (isLoading) {
                                webViewState.value?.stopLoading()
                            } else {
                                webViewState.value?.reload()
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (isKeyboardVisible) Icons.Default.Close
                            else if (isLoading) Icons.Default.Close
                            else Icons.Default.Refresh,
                            contentDescription = if (isKeyboardVisible) "清空" else if (isLoading) "停止" else "刷新",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // 顶部功能收进一个图标，点击展开
                Box {
                    IconButton(onClick = { showTopMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多功能", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                    ) {
                        // 收藏开关：当前页已收藏则显示实心星 + 「取消收藏」，点击即移除收藏
                        val isBookmarked = bookmarkItems.any { it.url == currentUrl }

                        // ── 分组一：当前页面 ──
                        MenuSectionTitle("当前页面")
                        DropdownMenuItem(
                            text = { Text(if (desktopMode) "切换为移动版" else "切换为桌面版") },
                            leadingIcon = { Icon(if (desktopMode) Icons.Default.PhoneAndroid else Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { showTopMenu = false; toggleDesktopMode() },
                        )
                        DropdownMenuItem(
                            text = { Text("在浏览器打开") },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showTopMenu = false
                                openInCustomTabs(context, currentUrl)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isBookmarked) "取消收藏" else "加入收藏") },
                            leadingIcon = { Icon(if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showTopMenu = false
                                toggleBookmark()
                            },
                        )

                        MenuDivider()

                        // ── 分组二：浏览数据 ──
                        MenuSectionTitle("浏览数据")
                        DropdownMenuItem(
                            text = { Text("浏览记录") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { showTopMenu = false; showHistory = true },
                        )
                        DropdownMenuItem(
                            text = { Text("我的收藏") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { showTopMenu = false; showBookmarks = true },
                        )
                        DropdownMenuItem(
                            text = { Text("清空浏览数据") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { showTopMenu = false; showClearData = true },
                        )

                        MenuDivider()

                        // ── 分组三：设置与更多 ──
                        MenuSectionTitle("设置与更多")
                        DropdownMenuItem(
                            text = { Text("设为默认页") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showTopMenu = false
                                setDefaultHome()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { showTopMenu = false; showSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (debugMode) "关闭调试模式" else "调试模式") },
                            leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showTopMenu = false
                                debugMode = !debugMode
                                Toast.makeText(
                                    context,
                                    if (debugMode) "调试模式已开启：电脑端 Chrome 访问 chrome://inspect/#devices，找到本应用 WebView 点 inspect 即可使用完整 DevTools"
                                    else "调试模式已关闭",
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                        )
                    }
                }
            }

            // 进度条
            if (isLoading && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            }

            // 空白页兜底提示：检测到页面可能未正常加载
            if (showBlankHint) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "此页面在应用内可能未正常加载",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        showBlankHint = false
                        openInCustomTabs(context, currentUrl)
                    }) {
                        Text("在浏览器打开", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        showBlankHint = false
                        if (desktopMode) toggleDesktopMode() else webViewState.value?.reload()
                    }) {
                        Text("切移动版重试", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // 调试模式开启提示条
            if (debugMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "调试模式已开启 · 电脑端 Chrome 访问 chrome://inspect/#devices，找到本应用 WebView 点 inspect 即可使用完整 DevTools",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

    }

    // 历史记录弹窗
    if (showHistory) {
        BrowserHistoryDialog(
            items = historyItems.toList(),
            onDismiss = { showHistory = false },
            onLoad = { item ->
                navigate(item.url)
                showHistory = false
            },
            onDelete = { index ->
                if (index in historyItems.indices) historyItems.removeAt(index)
            },
            onClear = { historyItems.clear() },
        )
    }

    // 收藏夹弹窗
    if (showBookmarks) {
        BrowserBookmarksDialog(
            items = bookmarkItems.toList(),
            onDismiss = { showBookmarks = false },
            onLoad = { item ->
                navigate(item.url)
                showBookmarks = false
            },
            onDelete = { index ->
                if (index in bookmarkItems.indices) {
                    bookmarkItems.removeAt(index)
                    saveBookmarks(context, bookmarkItems.toList())
                }
            },
            onClear = {
                bookmarkItems.clear()
                saveBookmarks(context, bookmarkItems.toList())
            },
        )
    }

    // 清空浏览数据弹窗
    if (showClearData) {
        BrowserClearDataDialog(
            onDismiss = { showClearData = false },
            onConfirm = { opts ->
                clearBrowseData(opts)
                showClearData = false
            },
        )
    }

    // 设置弹窗
    if (showSettings) {
        BrowserSettingsDialog(
            currentDefault = defaultHome,
            onDismiss = { showSettings = false },
            onSave = { url ->
                val u = normalizeUrl(url)
                if (u.isNotBlank()) {
                    defaultHome = u
                    saveDefaultHome(context, u)
                }
                showSettings = false
            },
            onUseCurrent = {
                val u = currentUrl
                if (u.isNotBlank()) {
                    defaultHome = u
                    saveDefaultHome(context, u)
                }
                showSettings = false
            },
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserHistoryDialog(
    items: List<WebHistoryItem>,
    onDismiss: () -> Unit,
    onLoad: (WebHistoryItem) -> Unit,
    onDelete: (Int) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("浏览记录") },
        text = {
            if (items.isEmpty()) {
                Text(
                    "暂无浏览记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(items.size, key = { idx -> items[idx].url + items[idx].time }) { index ->
                        val item = items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(item) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (item.title.isNotBlank()) item.title else item.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    text = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(item.time)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { onClear(); onDismiss() }) {
                        Text("清空全部", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserBookmarksDialog(
    items: List<WebBookmarkItem>,
    onDismiss: () -> Unit,
    onLoad: (WebBookmarkItem) -> Unit,
    onDelete: (Int) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的收藏") },
        text = {
            if (items.isEmpty()) {
                Text(
                    "暂无收藏",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(items.size, key = { idx -> items[idx].url + items[idx].time }) { index ->
                        val item = items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(item) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (item.title.isNotBlank()) item.title else item.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { onClear(); onDismiss() }) {
                        Text("清空全部", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

private data class ClearDataOptions(
    val history: Boolean,
    val formData: Boolean,
    val cookies: Boolean,
    val cache: Boolean,
    val siteData: Boolean,
    val backForward: Boolean,
    val bookmarks: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserClearDataDialog(
    onDismiss: () -> Unit,
    onConfirm: (ClearDataOptions) -> Unit,
) {
    var history by remember { mutableStateOf(true) }
    var formData by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    var siteData by remember { mutableStateOf(true) }
    var backForward by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空浏览数据") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "勾选要清除的数据项：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                ClearDataCheckbox("浏览记录", history) { history = it }
                ClearDataCheckbox("输入历史与表单数据", formData) { formData = it }
                ClearDataCheckbox("Cookie 与登录状态", cookies) { cookies = it }
                ClearDataCheckbox("缓存文件", cache) { cache = it }
                ClearDataCheckbox("站点数据（LocalStorage 等）", siteData) { siteData = it }
                ClearDataCheckbox("前进 / 后退记录", backForward) { backForward = it }
                ClearDataCheckbox("我的收藏", bookmarks) { bookmarks = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    ClearDataOptions(history, formData, cookies, cache, siteData, backForward, bookmarks),
                )
            }) {
                Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 下拉菜单分组标题（不可点击，仅作分类标识） */
@Composable
private fun MenuSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** 下拉菜单分组分隔线 */
@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun ClearDataCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserSettingsDialog(
    currentDefault: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onUseCurrent: () -> Unit,
) {
    var input by remember { mutableStateOf(TextFieldValue(currentDefault)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("浏览器设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "默认打开页面",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (input.text.isEmpty()) {
                                    Text("输入默认页网址", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
                                }
                                inner()
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { input = TextFieldValue(currentDefault) }) {
                    Text("恢复已保存", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onUseCurrent) {
                    Text("使用当前页")
                }
                TextButton(onClick = { onSave(input.text) }) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
