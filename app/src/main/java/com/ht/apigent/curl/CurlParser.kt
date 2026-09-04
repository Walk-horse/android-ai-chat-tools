package com.ht.apigent.curl

import java.net.URLEncoder
import java.util.Base64

/** 解析后的 curl 请求。 */
data class CurlRequest(
    val method: String,
    val url: String,
    val headers: MutableMap<String, String> = LinkedHashMap(),
    val body: String? = null,
    val isForm: Boolean = false,
    val formParts: List<FormPart> = emptyList(),
)

/** multipart 表单字段。 */
data class FormPart(
    val name: String,
    val value: String? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
)

/**
 * 轻量 curl 命令行解析器（增强版）。
 *
 * 相比旧版主要改进：
 * - token 自动剥离一层引号（修复 header 名变成 "Authorization、URL/body 带引号等错误）
 * - 正确处理 shell 行续接（反斜杠 + 换行）
 * - 支持短选项粘连：-XPOST / -d'{}' / -uuser:pass / -A'UA'
 * - 支持 --flag=value 长选项语法
 * - 多个 -d 按 curl 语义拼接（非 -G 直接拼接；-G 作为 query 参数）
 * - -F 表单字段解析为 multipart（由 CurlExecutor 构建真正的 multipart body）
 * - --data-urlencode 正确编码
 */
object CurlParser {

    class ParseException(message: String) : Exception(message)

    // 不需要参数、直接忽略的长选项
    private val IGNORE_LONG_NO_VALUE = setOf(
        "--compressed", "--silent", "--show-error", "--insecure", "--verbose",
        "--location", "--location-trusted", "--include", "--head", "--fail",
        "--fail-early", "--globoff", "--no-progress-meter", "--progress-bar",
        "--disable-epsv", "--http1.1", "--http2", "--tlsv1.2", "--tlsv1.3",
        "--ipv4", "--ipv6", "--next",
    )

    // 需要忽略参数（消耗下一个 token）的长选项
    private val IGNORE_LONG_WITH_VALUE = setOf(
        "--max-time", "--connect-timeout", "--output", "--cookie-jar",
        "--write-out", "--proxy", "--limit-rate", "--retry", "--retry-delay",
        "--cert", "--key", "--cacert", "--capath", "--interface", "--resolve",
        "--digest", "--negotiate", "--ntlm", "--abstract-unix-socket",
    )

    fun parse(raw: String): CurlRequest {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) throw ParseException("命令为空")

        var method: String? = null
        var url: String? = null
        val headers = LinkedHashMap<String, String>()
        val dataParts = ArrayList<String>()
        val formParts = ArrayList<FormPart>()
        var isForm = false
        var isGet = false
        var isHead = false

        var i = 0
        while (i < tokens.size) {
            val original = tokens[i]

            // 剥离可能的 --flag=value 语法（仅长选项）
            var t = original
            var inlineValue: String? = null
            if (t.startsWith("--") && t.contains("=")) {
                val eq = t.indexOf('=')
                inlineValue = t.substring(eq + 1)
                t = t.substring(0, eq)
            }

            when {
                // 命令名本身
                t == "curl" || t.equals("curl.exe", true) -> { /* 忽略 */ }

                // 长选项 / 独立短选项（取值优先用 inlineValue）
                t == "-X" || t == "--request" -> method = inlineValue ?: need(tokens, ++i, t)
                t == "-H" || t == "--header" -> addHeader(headers, inlineValue ?: need(tokens, ++i, t))
                t == "-d" || t == "--data" || t == "--data-raw" ||
                t == "--data-ascii" || t == "--data-binary" ->
                    dataParts.add(inlineValue ?: need(tokens, ++i, t))
                t == "--data-urlencode" ->
                    dataParts.add(encodeUrlComponent(inlineValue ?: need(tokens, ++i, t)))
                t == "-F" || t == "--form" -> {
                    isForm = true
                    formParts.add(parseFormField(inlineValue ?: need(tokens, ++i, t)))
                }
                t == "-u" || t == "--user" -> addBasicAuth(headers, inlineValue ?: need(tokens, ++i, t))
                t == "-A" || t == "--user-agent" -> headers["User-Agent"] = inlineValue ?: need(tokens, ++i, t)
                t == "-b" || t == "--cookie" -> headers["Cookie"] = inlineValue ?: need(tokens, ++i, t)
                t == "-e" || t == "--referer" -> headers["Referer"] = inlineValue ?: need(tokens, ++i, t)
                t == "-K" || t == "--config" -> { if (inlineValue == null) i++ } // 忽略配置文件
                t == "-G" || t == "--get" -> isGet = true
                t == "-I" || t == "--head" -> isHead = true
                t == "--url" -> url = inlineValue ?: need(tokens, ++i, t)
                t == "--" -> { /* 选项结束标记，后续为位置参数 */ }

                t in IGNORE_LONG_NO_VALUE -> { /* 忽略 */ }
                t in IGNORE_LONG_WITH_VALUE -> { if (inlineValue == null) i++ }

                // 组合短选项，如 -skvL / -XPOST / -d'{}' / -uuser:pass
                t.startsWith("-") && !t.startsWith("--") && t.length > 1 && t != "-" -> {
                    parseCombinedShortFlags(t, tokens, i, headers,
                        onMethod = { method = it },
                        onData = { dataParts.add(it) },
                        onForm = { isForm = true; formParts.add(it) },
                        onAdvance = { i = it },
                        onHead = { isHead = true },
                    )
                }

                t.startsWith("--") -> throw ParseException("不支持的选项: $t")

                else -> {
                    if (url == null) url = t else throw ParseException("多余的参数: $t")
                }
            }
            i++
        }

        if (url == null) throw ParseException("未找到 URL")

        val rawBody = dataParts.joinToString("")
        val finalMethod = method ?: when {
            isHead -> "HEAD"
            rawBody.isNotEmpty() || isForm -> "POST"
            else -> "GET"
        }

        // -G 模式：把 data 数据拼到 query 上（curl 以 & 连接多个字段）
        var finalUrl = url
        if (isGet && dataParts.isNotEmpty()) {
            val query = dataParts.joinToString("&")
            val sep = if (url.contains("?")) "&" else "?"
            finalUrl = "$url$sep$query"
        }

        // 默认 Content-Type（仅当用户未显式指定时）
        if (rawBody.isNotEmpty() && !headers.keys.any { it.equals("Content-Type", true) }) {
            val ct = when {
                rawBody.trimStart().startsWith("{") || rawBody.trimStart().startsWith("[") -> "application/json"
                rawBody.contains("=") -> "application/x-www-form-urlencoded"
                else -> "text/plain"
            }
            headers["Content-Type"] = ct
        }
        // 表单：不设置 Content-Type，交给 CurlExecutor 用 OkHttp multipart（自带 boundary）

        return CurlRequest(
            method = finalMethod,
            url = finalUrl,
            headers = headers,
            body = if (isGet || isForm) null else rawBody.ifEmpty { null },
            isForm = isForm,
            formParts = if (isForm) formParts else emptyList(),
        )
    }

    /**
     * 解析组合短选项（已剥离 --flag=value 的长选项不进这里）。
     * 通过回调把解析到的值回传给主流程，并通过 onAdvance 推进 i（消耗取值用的下一个 token）。
     */
    private fun parseCombinedShortFlags(
        t: String,
        tokens: List<String>,
        startIndex: Int,
        headers: MutableMap<String, String>,
        onMethod: (String) -> Unit,
        onData: (String) -> Unit,
        onForm: (FormPart) -> Unit,
        onAdvance: (Int) -> Unit,
        onHead: () -> Unit,
    ) {
        val flags = t.substring(1)
        var p = 0
        while (p < flags.length) {
            val c = flags[p]
            val rest = flags.substring(p + 1)
            when {
                // 纯忽略标志
                c in "skSvLfiIgqQ" -> p++
                // HEAD 方法
                c == 'I' -> { onHead(); p++ }
                // 取值标志（语义相关）
                c == 'X' -> {
                    if (rest.isNotEmpty()) onMethod(unquote(rest)) else onMethod(unquote(need(tokens, startIndex + 1, t)))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'd' -> {
                    if (rest.isNotEmpty()) onData(unquote(rest)) else onData(unquote(need(tokens, startIndex + 1, t)))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'F' -> {
                    if (rest.isNotEmpty()) onForm(parseFormField(unquote(rest)))
                    else onForm(parseFormField(unquote(need(tokens, startIndex + 1, t))))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'u' -> {
                    if (rest.isNotEmpty()) addBasicAuth(headers, unquote(rest))
                    else addBasicAuth(headers, unquote(need(tokens, startIndex + 1, t)))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'A' -> {
                    if (rest.isNotEmpty()) headers["User-Agent"] = unquote(rest)
                    else headers["User-Agent"] = unquote(need(tokens, startIndex + 1, t))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'b' -> {
                    if (rest.isNotEmpty()) headers["Cookie"] = unquote(rest)
                    else headers["Cookie"] = unquote(need(tokens, startIndex + 1, t))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'e' -> {
                    if (rest.isNotEmpty()) headers["Referer"] = unquote(rest)
                    else headers["Referer"] = unquote(need(tokens, startIndex + 1, t))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                c == 'H' -> {
                    if (rest.isNotEmpty()) addHeader(headers, unquote(rest))
                    else addHeader(headers, unquote(need(tokens, startIndex + 1, t)))
                    if (rest.isEmpty()) onAdvance(startIndex + 1)
                    p = flags.length
                }
                // 需要消耗参数的忽略标志（值忽略）：c/o/O/w/x/m/K/E/T/P/y/Y/z/Z
                c in "coOwxmKETPyYzZ" -> {
                    if (rest.isNotEmpty()) { /* 粘连值，忽略 */ } else onAdvance(startIndex + 1)
                    p = flags.length
                }
                else -> throw ParseException("不支持的选项: -$c")
            }
        }
    }

    /** 解析 -F 表单字段：支持 name=value、name=@/path、name=@/path;type=image/png;filename=a.png */
    private fun parseFormField(raw: String): FormPart {
        val segments = raw.split(";")
        val kv = segments[0]
        val eq = kv.indexOf('=')
        if (eq <= 0) throw ParseException("无效的表单字段: $raw")
        val name = kv.substring(0, eq).trim()
        val spec = kv.substring(eq + 1)
        if (spec.startsWith("@")) {
            val path = spec.substring(1).trim()
            var fileName = path.substringAfterLast('/').substringAfterLast('\\')
            var contentType: String? = null
            for (extra in segments.drop(1)) {
                val e = extra.split("=", limit = 2)
                if (e.size == 2) {
                    when (e[0].trim()) {
                        "type" -> contentType = e[1].trim()
                        "filename" -> fileName = e[1].trim()
                    }
                }
            }
            return FormPart(name = name, filePath = path, fileName = fileName, contentType = contentType)
        }
        return FormPart(name = name, value = spec)
    }

    private fun addHeader(headers: MutableMap<String, String>, v: String) {
        val idx = v.indexOf(':')
        if (idx <= 0) throw ParseException("无效的 header: $v")
        val name = v.substring(0, idx).trim()
        val value = v.substring(idx + 1).trim()
        // 跳过响应行，如 "HTTP/1.1 200 OK" 或被误粘进来的内容
        if (name.startsWith("HTTP/") || name.equals("HTTP", true)) return
        headers[name] = value
    }

    private fun addBasicAuth(headers: MutableMap<String, String>, userpass: String) {
        val token = "Basic " + Base64.getEncoder().encodeToString(userpass.toByteArray(Charsets.UTF_8))
        headers["Authorization"] = token
    }

    /** 剥离一层首尾引号（" 或 '）。 */
    private fun unquote(s: String): String {
        if (s.length >= 2) {
            val first = s[0]
            val last = s[s.length - 1]
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length - 1)
            }
        }
        return s
    }

    /** 按 shell 规则切分：支持单引号、双引号、反斜杠转义、行续接（反斜杠+换行）。 */
    fun tokenize(raw: String): List<String> {
        val input = raw.trim()
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\'' && !inDouble -> { inSingle = !inSingle }
                c == '"' && !inSingle -> { inDouble = !inDouble }
                c == '\\' && !inSingle -> {
                    if (i + 1 < input.length) {
                        val next = input[i + 1]
                        if (next == '\n' || next == '\r') {
                            // 行续接：丢弃反斜杠与换行
                            i++ // 跳过换行
                            if (i + 1 < input.length && input[i + 1] == '\n') i++ // 处理 \r\n
                        } else {
                            if (inDouble && next !in "\"\\$`") sb.append('\\')
                            sb.append(next)
                            i++
                        }
                    } else {
                        sb.append(c)
                    }
                }
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> {
                    if (!inSingle && !inDouble) {
                        if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
                    } else sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        if (inSingle || inDouble) throw ParseException("引号未闭合")
        return tokens
    }

    private fun need(tokens: List<String>, index: Int, flag: String): String {
        if (index >= tokens.size) throw ParseException("选项 $flag 缺少参数")
        return tokens[index]
    }

    private fun encodeUrlComponent(v: String): String {
        // --data-urlencode 支持 "name=value" 与 "=value" 形式
        val idx = v.indexOf('=')
        return if (idx < 0) {
            URLEncoder.encode(v, "UTF-8")
        } else {
            val name = v.substring(0, idx)
            val value = v.substring(idx + 1)
            if (name.isEmpty()) URLEncoder.encode(value, "UTF-8")
            else "$name=${URLEncoder.encode(value, "UTF-8")}"
        }
    }
}
