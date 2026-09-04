package com.ht.apigent.ui

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

@Suppress("DEPRECATION")
private fun getAppInfo(context: Context): AppInfo {
    val pm = context.packageManager
    val pi = pm.getPackageInfo(context.packageName, 0)
    val appName = pm.getApplicationLabel(pm.getApplicationInfo(context.packageName, 0)).toString()
    return AppInfo(
        appName = appName,
        packageName = context.packageName,
        versionName = pi.versionName ?: "unknown",
        versionCode = pi.versionCode.toLong(),
    )
}

/** 清理 WebView 缓存、Cookie 与应用临时文件 */
private fun clearAllCache(context: Context) {
    try {
        WebView(context).apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            destroy()
        }
    } catch (_: Exception) {
    }
    try {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    } catch (_: Exception) {
    }
    try {
        context.cacheDir?.deleteRecursively()
    } catch (_: Exception) {
    }
}

@Composable
fun MineContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val appInfo = remember { getAppInfo(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        MineListItem(
            icon = Icons.Default.DeleteSweep,
            title = "清理缓存",
            subtitle = "清除浏览器缓存、Cookie 与应用临时文件",
            onClick = {
                clearAllCache(context)
                Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
            },
        )
        HorizontalDivider()
        MineListItem(
            icon = Icons.Default.Info,
            title = "关于",
            subtitle = "${appInfo.appName} v${appInfo.versionName}",
            onClick = { showAbout = true },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Column {
                    Text(appInfo.appName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("版本：v${appInfo.versionName} (${appInfo.versionCode})")
                    Text("包名：${appInfo.packageName}")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${appInfo.appName} 是一款集 AI 对话、内置浏览器与实用工具于一体的工具箱。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun MineListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
