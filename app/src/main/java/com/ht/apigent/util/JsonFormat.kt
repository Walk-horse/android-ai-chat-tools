package com.ht.apigent.util

import org.json.JSONArray
import org.json.JSONObject

/** JSON 美化，失败时返回原文。 */
object JsonFormat {
    fun pretty(raw: String, indent: Int = 2): String {
        if (raw.isBlank()) return raw
        return try {
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(indent)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(indent)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }
}
