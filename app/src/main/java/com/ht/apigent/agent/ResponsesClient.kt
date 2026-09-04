package com.ht.apigent.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * OpenAI Responses API 客户端。
 * 支持流式(SSE)与非流式调用，兼容任意 OpenAI 兼容网关（自定义 Base URL）。
 */
class ResponsesClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    data class Config(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String = "默认配置",
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val systemPrompt: String = "你是一个乐于助人的 AI 助手。",
    ) {
        /** 仅用于编辑时生成新 ID 的便捷方法 */
        fun withNewId(): Config = copy(id = java.util.UUID.randomUUID().toString())
    }

    /** 流式对话，每收到一段增量文本回调一次 [onDelta]，结束时回调 [onDone]。 */
    suspend fun chatStream(
        config: Config,
        history: List<Pair<String, String>>, // (role, content)，role: user/assistant
        userMessage: String,
        onDelta: (String) -> Unit,
        onDone: (String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val body = buildBody(config, history, userMessage, stream = true)
            val request = buildRequest(config, body)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCancelled) cont.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            val err = "HTTP ${response.code}: ${response.body?.string()?.take(500)}"
                            if (!cont.isCancelled) cont.resume(Result.failure(IOException(err)))
                            return
                        }
                        val source = response.body?.source()
                        var full = StringBuilder()
                        var error: Exception? = null
                        while (!cont.isCancelled) {
                            val line = source?.readUtf8Line() ?: break
                            if (line.isEmpty() || line.startsWith(":")) continue
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") break
                            try {
                                val evt = JSONObject(payload)
                                when (evt.optString("type")) {
                                    // OpenAI Responses API 流式增量：delta 为直接字符串
                                    "response.output_text.delta" -> {
                                        val delta = evt.optString("delta")
                                        full.append(delta)
                                        onDelta(delta)
                                    }
                                    // 兼容 Chat Completions 流式：choices[0].delta.content
                                    "chat.completion.chunk" -> {
                                        val delta = evt.optJSONArray("choices")
                                            ?.optJSONObject(0)
                                            ?.optJSONObject("delta")
                                            ?.optString("content") ?: ""
                                        full.append(delta)
                                        onDelta(delta)
                                    }
                                    "error" -> {
                                        val msg = evt.optJSONObject("error")?.optString("message")
                                            ?: evt.optString("message", "未知错误")
                                        error = IOException(msg)
                                    }
                                }
                            } catch (_: Exception) { /* 忽略非 JSON 行 */ }
                        }
                        if (error != null) {
                            if (!cont.isCancelled) cont.resume(Result.failure(error))
                        } else {
                            onDone(full.toString())
                            if (!cont.isCancelled) cont.resume(Result.success(Unit))
                        }
                    } catch (e: Exception) {
                        if (!cont.isCancelled) cont.resume(Result.failure(e))
                    } finally {
                        response.close()
                    }
                }
            })
            cont.invokeOnCancellation { }
        }
    }

    /** 非流式对话，一次返回完整文本。 */
    suspend fun chatOnce(
        config: Config,
        history: List<Pair<String, String>>,
        userMessage: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildBody(config, history, userMessage, stream = false)
            val request = buildRequest(config, body)
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${raw.take(500)}")
                }
                extractText(JSONObject(raw))
            }
        }
    }

    /** 拉取模型列表（GET 兼容端点 /v1/models），返回模型 id 列表。 */
    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = resolveModelsEndpoint(baseUrl)
            val builder = Request.Builder().url(endpoint).get()
            if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${raw.take(500)}")
                parseModels(raw)
            }
        }
    }

    private fun parseModels(raw: String): List<String> {
        val json = JSONObject(raw)
        val list = mutableListOf<String>()
        val data = json.optJSONArray("data")
        if (data != null) {
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id")
                if (!id.isNullOrBlank()) list.add(id)
            }
        }
        if (list.isEmpty()) {
            for (key in listOf("models", "model_list", "models_list")) {
                val arr = json.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    val id = item?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: arr.optString(i).takeIf { it.isNotBlank() }
                    if (id != null) list.add(id)
                }
                if (list.isNotEmpty()) break
            }
        }
        return list.distinct()
    }

    private fun buildBody(
        config: Config,
        history: List<Pair<String, String>>,
        userMessage: String,
        stream: Boolean,
    ): String {
        val input = JSONArray()
        history.forEach { (role, content) ->
            if (content.isNotBlank()) {
                input.put(JSONObject().put("role", role).put("content", content))
            }
        }
        input.put(JSONObject().put("role", "user").put("content", userMessage))

        return JSONObject()
            .put("model", config.model)
            .put("instructions", config.systemPrompt)
            .put("input", input)
            .put("stream", stream)
            .toString()
    }

    private fun buildRequest(config: Config, body: String): Request {
        val endpoint = resolveEndpoint(config.baseUrl)
        val builder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
        if (config.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        return builder.build()
    }

    /** 从响应 JSON 中提取最终文本（兼容 Responses 与 Chat Completions 两种格式）。 */
    private fun extractText(json: JSONObject): String {
        val sb = StringBuilder()
        val output = json.optJSONArray("output")
        if (output != null) {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val c = content.optJSONObject(j) ?: continue
                    when (c.optString("type")) {
                        "output_text" -> sb.append(c.optString("text"))
                        "text" -> sb.append(c.optString("text"))
                    }
                }
            }
        }
        if (sb.isEmpty()) {
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.optJSONObject(0)?.optJSONObject("message")
                sb.append(msg?.optString("content"))
            }
        }
        return sb.toString().ifBlank {
            json.optString("output_text").ifBlank { "（空响应）" }
        }
    }

    companion object {
        /** 兼容 Base URL 三种写法：https://x/v1、https://x、https://x/v1/responses */
        fun resolveEndpoint(baseUrl: String): String {
            val base = baseUrl.trim().trimEnd('/')
            return when {
                base.endsWith("/responses") -> base
                base.endsWith("/v1") -> "$base/responses"
                else -> "$base/v1/responses"
            }
        }

        /** 模型列表端点：兼容 /responses 与 /v1 写法，默认 https://x/v1/models */
        fun resolveModelsEndpoint(baseUrl: String): String {
            var base = baseUrl.trim().trimEnd('/')
            if (base.endsWith("/responses")) base = base.removeSuffix("/responses")
            return if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
        }
    }
}
