package com.vipla.bato.ai


import com.vipla.bato.data.RetrievedItem
import com.vipla.bato.data.StenoEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection


sealed class ProviderResult {
    data class Response(val text:String,val httpStatus:Int,val model:String,val provenance:String,val retrievalUsed:Boolean,val retrievalSources:List<String>,val retrievedItemIds:List<String>,val graphResolution:String,val currentDatetimeUsed:Boolean,val contextBundle:String):ProviderResult()
    data class Blocked(val reason:String):ProviderResult()
}
data class ProviderContext(val recentConversation:List<StenoEvent>,val stenoRetrieval:List<RetrievedItem>,val riznicaRetrieval:List<RetrievedItem>,val fastGraphContext:List<RetrievedItem>,val lexicalGrammarContext:List<RetrievedItem>,val resolvedEntities:List<String>,val graphResolution:String,val ambiguityCandidates:List<String>,val retrievalRequired:Boolean=false)
interface ProviderBridge { suspend fun respond(message:String,context:ProviderContext):ProviderResult }


class HttpProviderBridge(private val endpoint:String):ProviderBridge {
    override suspend fun respond(message:String,context:ProviderContext):ProviderResult=withContext(Dispatchers.IO){
        if(endpoint.isBlank()) return@withContext ProviderResult.Blocked("AI provider endpoint is not configured; no response was fabricated.")
        if(!endpoint.startsWith("https://")) return@withContext ProviderResult.Blocked("Provider endpoint must use HTTPS.")
        runCatching {
            fun eventJson(e:StenoEvent)=JSONObject().put("role",e.role.lowercase()).put("content",e.rawText).put("timestamp_ms",e.startTs).put("provenance",e.providerState).put("source_id","STENO:${e.id}")
            fun itemJson(i:RetrievedItem)=JSONObject().put("source_type",i.sourceType).put("source_id",i.id).put("timestamp_ms",i.timestamp).put("canonical_text",i.canonicalText).put("confidence",i.confidence).put("provenance",i.provenance).put("entity_id",i.entityId).put("cluster_id",i.clusterId).put("domain",i.domain)
            fun <T> array(items:List<T>,convert:(T)->JSONObject)=JSONArray().also{a->items.forEach{a.put(convert(it))}}
            val body=JSONObject().put("message",message).put("current_user_message",message).put("timezone",TimeZone.getDefault().id)
                .put("recent_conversation",array(context.recentConversation,::eventJson)).put("resolved_entities",JSONArray(context.resolvedEntities))
                .put("fast_graph_context",array(context.fastGraphContext,::itemJson)).put("steno_retrieval",array(context.stenoRetrieval,::itemJson))
                .put("riznica_retrieval",array(context.riznicaRetrieval,::itemJson)).put("lexical_grammar_context",array(context.lexicalGrammarContext,::itemJson))
                .put("fast_graph_resolution",context.graphResolution).put("ambiguity_candidates",JSONArray(context.ambiguityCandidates)).put("retrieval_required",context.retrievalRequired)
            var code=0; var raw=""; var attempt=0
            do {
                attempt++
