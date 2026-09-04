package com.ht.apigent.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val role: String, // user / assistant / error
    val content: String,
    val streaming: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("agent_config", Application.MODE_PRIVATE)
    private val client = ResponsesClient()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _configs = MutableStateFlow<List<ResponsesClient.Config>>(loadConfigs())
    val configs: StateFlow<List<ResponsesClient.Config>> = _configs

    private val _activeId = MutableStateFlow(prefs.getString("active_id", "") ?: "")
    val activeId: StateFlow<String> = _activeId

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** 当前启用的配置；找不到时回退到列表第一项 */
    val activeConfig: ResponsesClient.Config
        get() = _configs.value.firstOrNull { it.id == _activeId.value }
            ?: _configs.value.firstOrNull()
            ?: ResponsesClient.Config(baseUrl = "https://api.openai.com/v1", apiKey = "", model = "gpt-4o-mini")

    private fun loadConfigs(): List<ResponsesClient.Config> {
        val raw = prefs.getString("configs", null)
        if (raw != null) {
            try {
                return parseConfigs(JSONArray(raw))
            } catch (_: Exception) { /* 损坏则回退默认 */ }
        }
        // 兼容旧版单配置存储
        val oldBase = prefs.getString("base_url", null)
        return if (oldBase != null) {
            listOf(
                ResponsesClient.Config(
                    name = "默认配置",
                    baseUrl = oldBase,
                    apiKey = prefs.getString("api_key", "") ?: "",
                    model = prefs.getString("model", "gpt-4o-mini") ?: "",
                    systemPrompt = prefs.getString("system_prompt", "你是一个乐于助人的 AI 助手。") ?: "",
                ),
            )
        } else {
            listOf(
                ResponsesClient.Config(
                    name = "默认配置",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = "",
                    model = "gpt-4o-mini",
                ),
            )
        }
    }

    private fun parseConfigs(arr: JSONArray): List<ResponsesClient.Config> {
        val list = mutableListOf<ResponsesClient.Config>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                ResponsesClient.Config(
                    id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    name = o.optString("name").ifBlank { "配置 $i" },
                    baseUrl = o.optString("baseUrl"),
                    apiKey = o.optString("apiKey"),
                    model = o.optString("model").ifBlank { "gpt-4o-mini" },
                    systemPrompt = o.optString("systemPrompt").ifBlank { "你是一个乐于助人的 AI 助手。" },
                ),
            )
        }
        return list
    }

    private fun persist() {
        val arr = JSONArray()
        _configs.value.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("baseUrl", c.baseUrl)
                    put("apiKey", c.apiKey)
                    put("model", c.model)
                    put("systemPrompt", c.systemPrompt)
                },
            )
        }
        prefs.edit()
            .putString("configs", arr.toString())
            .putString("active_id", _activeId.value)
            .apply()
    }

    /** 新增或更新配置；newConfig 带已有 id 视为更新，否则新增。setActive=true 同时设为启用。 */
    fun saveConfig(cfg: ResponsesClient.Config, setActive: Boolean = false) {
        val exists = _configs.value.any { it.id == cfg.id }
        _configs.value = if (exists) {
            _configs.value.map { if (it.id == cfg.id) cfg else it }
        } else {
            _configs.value + cfg
        }
        if (setActive || _activeId.value.isBlank() || _configs.value.none { it.id == _activeId.value }) {
            _activeId.value = cfg.id
        }
        persist()
    }

    fun deleteConfig(id: String) {
        val remaining = _configs.value.filter { it.id != id }
        _configs.value = remaining
        if (_activeId.value == id) {
            _activeId.value = remaining.firstOrNull()?.id ?: ""
        }
        persist()
    }

    fun setActive(id: String) {
        if (_configs.value.any { it.id == id }) {
            _activeId.value = id
            persist()
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _busy.value) return

        val cfg = activeConfig
        if (cfg.apiKey.isBlank()) {
            _messages.value += ChatMessage("error", "请先在右上角配置中填写 API Key（${cfg.name}）")
            return
        }

        val history = _messages.value
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }

        _messages.value += ChatMessage("user", content)
        _messages.value += ChatMessage("assistant", "", streaming = true)
        _busy.value = true

        viewModelScope.launch {
            val sb = StringBuilder()
            val result = client.chatStream(
                config = cfg,
                history = history,
                userMessage = content,
                onDelta = { delta ->
                    sb.append(delta)
                    updateLastAssistant(sb.toString(), streaming = true)
                },
                onDone = { full ->
                    updateLastAssistant(full, streaming = false)
                },
            )
            result.onFailure { e ->
                val msg = e.message ?: "网络请求失败"
                _messages.value = _messages.value.mapIndexed { idx, m ->
                    if (idx == _messages.value.lastIndex && m.role == "assistant") {
                        m.copy(content = m.content.ifEmpty { "（无回复）" } + "\n\n⚠️ $msg", streaming = false)
                    } else m
                }
            }
            _busy.value = false
        }
    }

    private fun updateLastAssistant(content: String, streaming: Boolean) {
        val list = _messages.value.toMutableList()
        if (list.isEmpty()) return
        val last = list.last()
        if (last.role != "assistant") return
        list[list.lastIndex] = last.copy(content = content, streaming = streaming)
        _messages.value = list
    }
}
