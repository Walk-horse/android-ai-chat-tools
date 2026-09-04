package com.ht.apigent.ui

import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Base64 编码（UTF-8，不换行） */
private fun base64Encode(input: String): String =
    Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

/** Base64 解码；输入非法时返回 null */
private fun base64Decode(input: String): String? = try {
    String(Base64.decode(input.trim(), Base64.DEFAULT), Charsets.UTF_8)
} catch (_: Exception) {
    null
}

@Composable
fun Base64ToolContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun copyOutput() {
        if (output.isEmpty()) {
            Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setText(AnnotatedString(output))
        Toast.makeText(context, "已复制结果", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "输入文本或 Base64 串，点击编码 / 解码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入") },
            minLines = 4,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    output = base64Encode(input)
                    error = null
                },
                enabled = input.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("编码 →")
            }
            Button(
                onClick = {
                    val decoded = base64Decode(input)
                    if (decoded == null) {
                        error = "解码失败：不是合法的 Base64 字符串"
                    } else {
                        output = decoded
                        error = null
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("解码 →")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    input = output
                    output = ""
                    error = null
                },
                enabled = output.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("结果填入输入")
            }
            OutlinedButton(
                onClick = {
                    input = ""
                    output = ""
                    error = null
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清空")
            }
        }

        if (error != null) {
            Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }

        OutlinedTextField(
            value = output,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("结果") },
            minLines = 4,
            maxLines = 8,
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = {
                if (output.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { copyOutput() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "输入 ${input.length} 字符 · 结果 ${output.length} 字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
