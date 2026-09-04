package com.ht.apigent.curl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一次 curl 请求的执行结果。 */
data class CurlResponse(
    val success: Boolean,
    val statusCode: Int?,
    val headers: Map<String, String>,
    val body: String,
    val durationMs: Long,
    val error: String? = null,
)

/** 发送解析后的 curl 请求。 */
object CurlExecutor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun execute(req: CurlRequest): CurlResponse = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val builder = Request.Builder().url(req.url)
            req.headers.forEach { (k, v) -> builder.header(k, v) }

            when {
                req.isForm && req.formParts.isNotEmpty() -> {
                    val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                    for (part in req.formParts) {
                        if (part.filePath != null) {
                            val file = File(part.filePath)
                            if (!file.isFile) throw IOException("无法读取文件: ${part.filePath}")
                            val mt = (part.contentType ?: guessContentType(part.fileName ?: file.name))
                                .toMediaTypeOrNull()
                            mb.addFormDataPart(
                                part.name,
                                part.fileName ?: file.name,
                                file.readBytes().toRequestBody(mt),
                            )
                        } else {
                            val mt = (part.contentType ?: "text/plain").toMediaTypeOrNull()
                            mb.addFormDataPart(part.name, null, (part.value ?: "").toRequestBody(mt))
                        }
                    }
                    builder.method(req.method, mb.build())
                }
                req.body != null -> {
                    var raw = req.body
                    // 支持 -d @file / --data=@file 读取本地文件内容
                    if (raw.startsWith("@")) {
                        val file = File(raw.substring(1))
                        if (!file.isFile) throw IOException("无法读取文件: ${raw.substring(1)}")
                        raw = file.readText(Charsets.UTF_8)
                    }
                    val mediaType = req.headers["Content-Type"] ?: "application/x-www-form-urlencoded"
                    builder.method(req.method, raw.toRequestBody(mediaType.toMediaTypeOrNull()))
                }
                else -> builder.method(req.method, null)
            }

            client.newCall(builder.build()).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                val headers = LinkedHashMap<String, String>()
                response.headers.forEach { (k, v) -> headers.putIfAbsent(k, v) }
                CurlResponse(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    headers = headers,
                    body = respBody,
                    durationMs = System.currentTimeMillis() - start,
                )
            }
        } catch (e: Exception) {
            CurlResponse(
                success = false,
                statusCode = null,
                headers = emptyMap(),
                body = "",
                durationMs = System.currentTimeMillis() - start,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private fun guessContentType(name: String): String {
        return when (name.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "mp4" -> "video/mp4"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
