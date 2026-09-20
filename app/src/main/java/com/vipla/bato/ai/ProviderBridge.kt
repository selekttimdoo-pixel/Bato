package com.vipla.bato.ai

import com.vipla.bato.data.StenoEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection

sealed class ProviderResult {
    data class Response(
        val text: String,
        val httpStatus: Int,
        val model: String,
        val provenance: String,
        val retrievalUsed: Boolean,
        val retrievalSources: List<String>
    ) : ProviderResult()
    data class Blocked(val reason: String) : ProviderResult()
}

data class ProviderContext(
    val recentConversation: List<StenoEvent>,
    val stenoRetrieval: List<StenoEvent>,
    val riznicaRetrieval: List<com.vipla.bato.data.KnowledgeObject>,
    val fastGraphContext: List<com.vipla.bato.data.KnowledgeObject>
)

interface ProviderBridge {
    suspend fun respond(message: String, context: ProviderContext): ProviderResult
}

class HttpProviderBridge(private val endpoint: String) : ProviderBridge {
    override suspend fun respond(message: String, context: ProviderContext): ProviderResult = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext ProviderResult.Blocked("AI provider endpoint is not configured; no response was fabricated.")
        if (!endpoint.startsWith("https://")) return@withContext ProviderResult.Blocked("Provider endpoint must use HTTPS.")
        runCatching {
            val connection = URL(endpoint).openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            fun eventJson(it: StenoEvent) = JSONObject()
                .put("role", it.role.lowercase()).put("content", it.rawText)
                .put("timestamp_ms", it.startTs).put("provenance", it.providerState)
                .put("source", "STENO:${it.id}")
            val recent = JSONArray().also { array -> context.recentConversation.forEach { array.put(eventJson(it)) } }
            val steno = JSONArray().also { array -> context.stenoRetrieval.forEach { array.put(eventJson(it)) } }
            fun knowledgeJson(it: com.vipla.bato.data.KnowledgeObject) = JSONObject()
                .put("key", it.key).put("layer", it.layer).put("content", it.content)
                .put("timestamp_ms", it.updatedAt).put("provenance", "LOCAL_RIZNICA:${it.key}")
            val riznica = JSONArray().also { array -> context.riznicaRetrieval.forEach { array.put(knowledgeJson(it)) } }
            val graph = JSONArray().also { array -> context.fastGraphContext.forEach { array.put(knowledgeJson(it)) } }
            val hooks = JSONObject()
                .put("steno_wal", true).put("fast_graph", true).put("riznica", true)
                .put("active_serbian_lexicon", true).put("serbian_grammar_graph", true)
            val body = JSONObject().put("message", message)
                .put("timezone", TimeZone.getDefault().id)
                .put("recent_conversation", recent)
                .put("steno_retrieval", steno)
                .put("riznica_retrieval", riznica)
                .put("fast_graph_context", graph)
                .put("knowledge_hooks", hooks)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (code !in 200..299) ProviderResult.Blocked("Provider HTTP $code: ${raw.take(200)}")
            else {
                val json = JSONObject(raw)
                val text = json.optString("response").ifBlank { json.optString("text") }
                if (text.isBlank()) ProviderResult.Blocked("Provider returned no response field.") else {
                    val sources = json.optJSONArray("retrieval_sources")
                    ProviderResult.Response(
                        text = text, httpStatus = code,
                        model = json.optString("provider_model", json.optString("model", "unreported")),
                        provenance = json.optString("provenance", "PROVIDER_REAL"),
                        retrievalUsed = json.optBoolean("retrieval_used", false),
                        retrievalSources = (0 until (sources?.length() ?: 0)).map { sources!!.optString(it) }
                    )
                }
            }
        }.getOrElse { ProviderResult.Blocked("Provider bridge failed [${it::class.java.simpleName}]: ${it.message ?: "no detail"}") }
    }
}
