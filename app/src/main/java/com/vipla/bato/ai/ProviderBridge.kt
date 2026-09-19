package com.vipla.bato.ai

import com.vipla.bato.data.StenoEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class ProviderResult {
    data class Response(val text: String) : ProviderResult()
    data class Blocked(val reason: String) : ProviderResult()
}

interface ProviderBridge {
    suspend fun respond(message: String, context: List<StenoEvent>): ProviderResult
}

class HttpProviderBridge(private val endpoint: String) : ProviderBridge {
    override suspend fun respond(message: String, context: List<StenoEvent>): ProviderResult = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext ProviderResult.Blocked("AI provider endpoint is not configured; no response was fabricated.")
        runCatching {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val history = JSONArray()
            context.forEach { history.put(JSONObject().put("role", it.role.lowercase()).put("content", it.rawText)) }
            val hooks = JSONObject()
                .put("steno_wal", true).put("fast_graph", true).put("riznica", true)
                .put("active_serbian_lexicon", true).put("serbian_grammar_graph", true)
            val body = JSONObject().put("message", message).put("history", history).put("knowledge_hooks", hooks)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) ProviderResult.Blocked("Provider HTTP $code: ${raw.take(200)}")
            else {
                val text = JSONObject(raw).optString("response").ifBlank { JSONObject(raw).optString("text") }
                if (text.isBlank()) ProviderResult.Blocked("Provider returned no response field.") else ProviderResult.Response(text)
            }
        }.getOrElse { ProviderResult.Blocked("Provider bridge failed: ${it.message ?: it::class.java.simpleName}") }
    }
}
