package com.ht.apigent.curl

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray

class CurlViewModel(application: Application) : AndroidViewModel(application) {

    var command by mutableStateOf("") // 用户输入的 curl 命令

    var parsed by mutableStateOf<CurlRequest?>(null)
        private set

    var parseError by mutableStateOf<String?>(null)
        private set

    var response by mutableStateOf<CurlResponse?>(null)
        private set

    var executing by mutableStateOf(false)
        private set

    private val prefs = application.getSharedPreferences("curl_prefs", Context.MODE_PRIVATE)

    /** 历史记录（倒序，最近使用的在最前）。 */
    var history by mutableStateOf<List<String>>(loadHistory())
        private set

    fun parse() {
        parseError = null
        parsed = null
        response = null
        try {
            parsed = CurlParser.parse(command)
        } catch (e: Exception) {
            parseError = e.message ?: "解析失败"
        }
    }

    fun execute() {
        val req = parsed ?: return
        addHistory(command)
        executing = true
        response = null
        viewModelScope.launch {
            response = CurlExecutor.execute(req)
            executing = false
        }
    }

    fun fillExample() {
        command = """curl -X POST "https://api.example.com/v1/echo" \
  -H "Authorization: Bearer sk-123456" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'"""
    }

    // ---- 历史记录 ----

    fun addHistory(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty()) return
        val list = (listOf(c) + history.filter { it != c }).take(30)
        history = list
        saveHistory(list)
    }

    fun removeHistory(cmd: String) {
        history = history.filter { it != cmd }
        saveHistory(history)
    }

    fun clearHistory() {
        history = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString(KEY_HISTORY, JSONArray(list).toString()).apply()
    }

    private fun loadHistory(): List<String> {
        val s = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_HISTORY = "history"
    }
}
