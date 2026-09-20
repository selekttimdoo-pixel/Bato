package com.vipla.bato.data

import java.text.Normalizer
import java.util.UUID

data class RetrievedItem(val id: String, val sourceType: String, val timestamp: Long?, val canonicalText: String, val confidence: Double, val provenance: String, val entityId: String? = null, val clusterId: String? = null, val domain: String? = null)
data class RetrievalBundle(val steno: List<RetrievedItem>, val riznica: List<RetrievedItem>, val fastGraph: List<RetrievedItem>, val lexicalGrammar: List<RetrievedItem>, val resolvedEntities: List<String>, val graphResolution: String, val ambiguityCandidates: List<String>, val retrievalRequired: Boolean)

class StenoRepository(private val dao: StenoDao) {
    val events = dao.observeAll(); val cockpitLog = dao.observeLog(); val controlStates = dao.observeStates()
    val runtimeValues = dao.observeRuntime(); val knowledge = dao.observeKnowledge()

    suspend fun append(role: String, text: String, providerState: String = "LOCAL"): StenoEvent {
        val now = System.currentTimeMillis(); val previous = dao.recent(1).firstOrNull(); val gap = previous?.let { now - it.endTs } ?: Long.MAX_VALUE
        val event = StenoEvent(role = role, rawText = text, startTs = now, endTs = now,
            sessionId = if (previous == null || gap > 600_000L) UUID.randomUUID().toString() else previous.sessionId,
            segmentId = if (previous == null || gap > 120_000L) UUID.randomUUID().toString() else previous.segmentId, providerState = providerState)
        val saved = event.copy(id = dao.insert(event)); if (role == "USER") indexEntities(saved); return saved
    }

    suspend fun context(limit: Int = 12): List<StenoEvent> = dao.recent(limit).reversed()
    suspend fun retrieve(query: String, excludeEventId: Long, recent: List<StenoEvent> = emptyList(), limit: Int = 8): RetrievalBundle {
        val q = features(query); val referring = INDIRECT.containsMatchIn(query); val direct = aliasEntities(query)
        val anchors = if (referring) recent.takeLast(8) else recent.takeLast(2)
        val anchorFeatures = features(anchors.joinToString(" ") { it.rawText })
        val contextualAliases = if (referring) aliasEntities(anchors.joinToString(" ") { it.rawText }) else emptySet()
        val resolvedAliases = if (direct.isNotEmpty()) direct else contextualAliases
        val all = dao.allKnowledge(); val graph = all.filter { it.layer == "FAST GRAPH" }.map { card ->
            val cf = features("${card.key} ${card.content}")
            val directBoost = if (resolvedAliases.any { card.content.contains(it, true) || card.key.contains(it.lowercase(), true) }) .75 else 0.0
            val score = semanticScore(q, cf) + (if (referring) semanticScore(anchorFeatures, cf) * .65 else 0.0) + directBoost
            card to score.coerceAtMost(1.0)
        }.filter { it.second >= .18 }.sortedByDescending { it.second }.take(4)
        val contextualWinner = graph.firstOrNull()?.takeIf { resolvedAliases.isNotEmpty() || referring && it.second >= .28 }
        val entities = when {
            direct.isNotEmpty() -> direct.toList()
            contextualAliases.isNotEmpty() -> contextualAliases.toList()
            contextualWinner != null -> listOf(contextualWinner.first.key.removePrefix("entity:"))
            else -> graph.map { it.first.key.removePrefix("entity:") }
        }
        val expanded = q.copy(tokens = q.tokens + anchorFeatures.tokens + graph.flatMap { features(it.first.content).tokens })
        val steno = dao.retrievalWindow(1000).asSequence().filter { it.id != excludeEventId && it.role in setOf("USER", "ASSISTANT") }
            .map { event -> event to (semanticScore(expanded, features(event.rawText)) + if (referring && event in anchors) .45 else 0.0).coerceAtMost(1.0) }
            .filter { it.second >= .16 }.sortedWith(compareByDescending<Pair<StenoEvent, Double>> { it.second }.thenByDescending { it.first.startTs })
            .distinctBy { normalize(it.first.rawText) }.take(limit).map { (e, score) -> RetrievedItem("STENO:${e.id}", if (e.providerState.contains("IMPORTED")) "IMPORTED_STENO" else "LOCAL_STENO", e.startTs, e.rawText, score.coerceAtMost(1.0), e.providerState, entities.firstOrNull(), graph.firstOrNull()?.first?.content?.field("cluster"), graph.firstOrNull()?.first?.content?.field("domain")) }.toList()
        val graphItems = graph.map { (k, score) -> RetrievedItem("GRAPH:${k.key}", "FAST_GRAPH", k.updatedAt, k.content, score.coerceAtMost(1.0), "LOCAL_RIZNICA:${k.key}", k.key.removePrefix("entity:"), k.content.field("cluster"), k.content.field("domain")) }
        val lexical = all.filter { it.layer in setOf("Active Serbian Lexicon", "Serbian Grammar Graph") }.map { it to semanticScore(q, features(it.content)) }.filter { it.second >= .22 }.sortedByDescending { it.second }.take(3).map { it.first.asItem("LEXICAL_GRAMMAR", it.second) }
        val riznica = all.asSequence().filter { it.layer !in setOf("FAST GRAPH", "Active Serbian Lexicon", "Serbian Grammar Graph") }.map { it to semanticScore(expanded, features("${it.key} ${it.content}")) }.filter { it.second >= .2 }.sortedByDescending { it.second }.take(limit).map { it.first.asItem("RIZNICA", it.second) }.toList()
        val contextualEvidence = direct.isNotEmpty() || referring && (steno.isNotEmpty() || contextualWinner != null)
        val ambiguous = if (!contextualEvidence && graph.size >= 2 && kotlin.math.abs(graph[0].second - graph[1].second) < .05) graph.take(2).map { it.first.key.removePrefix("entity:") } else emptyList()
        val resolution = if (ambiguous.isNotEmpty()) "AMBIGUOUS:${ambiguous.joinToString("|")}" else if (entities.isNotEmpty()) "RESOLVED:${entities.joinToString()}" else "UNRESOLVED"
        val retrievalRequired = referring && (steno.isNotEmpty() || graphItems.isNotEmpty() || riznica.isNotEmpty()) || direct.isNotEmpty() && (graphItems.isNotEmpty() || riznica.isNotEmpty())
        return RetrievalBundle(steno, riznica, graphItems, lexical, entities, resolution, ambiguous, retrievalRequired)
    }

    private suspend fun indexEntities(event: StenoEvent) = extractEntities(event.rawText).take(6).forEach { entity ->
        val slug = normalize(entity).replace(' ', '-'); dao.putKnowledge(KnowledgeObject("entity:$slug", "FAST GRAPH", "entity=$entity; aliases=$entity; cluster=conversation:$slug; domain=conversation; evidence=STENO:${event.id}; relation=MENTIONED_IN", event.startTs))
    }
    private data class Features(val tokens: Set<String>, val phrases: Set<String>, val entities: Set<String>)
    private fun features(text: String): Features { val tokens = normalize(text).split(' ').filter { it.length >= 3 && it !in STOP }.toSet(); val list=tokens.toList(); return Features(tokens, (list.windowed(2)+list.windowed(3)).map { it.joinToString(" ") }.toSet(), extractEntities(text).map(::normalize).toSet()) }
    private fun semanticScore(q: Features, c: Features): Double { if (q.tokens.isEmpty() && q.entities.isEmpty()) return 0.0; val code=q.tokens.any { it.any(Char::isDigit)&&it in c.tokens }; return (overlap(q.entities,c.entities) * 0.5 + overlap(q.phrases,c.phrases) * 0.3 + overlap(q.tokens,c.tokens) * 0.2 + if (code) 0.5 else 0.0).coerceAtMost(1.0) }
    private fun overlap(a:Set<String>,b:Set<String>)=if(a.isEmpty()||b.isEmpty())0.0 else a.intersect(b).size.toDouble()/a.size
    private fun extractEntities(text:String):Set<String> = Regex("\\b[\\p{L}]{2,}-?\\d{2,}\\b").findAll(text).map{it.value}.toSet() + Regex("(?<![.!?]\\s)\\b[\\p{Lu}][\\p{L}]{2,}(?:\\s+[\\p{Lu}][\\p{L}]{2,}){0,3}").findAll(text).map{it.value}.filter { normalize(it) !in NON_ENTITIES }.toSet() + ALIASES.filterKeys { normalize(text).contains(it) }.values.flatten()
    private fun normalize(text:String)=Normalizer.normalize(text.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace(Regex("[^\\p{L}\\p{N}-]+")," ").trim()
    private fun String.field(name:String)=substringAfter("$name=","").substringBefore(';').ifBlank{null}
    private fun KnowledgeObject.asItem(type:String,score:Double)=RetrievedItem("RIZNICA:$key",type,updatedAt,content,score.coerceAtMost(1.0),"LOCAL_RIZNICA:$key")
    private fun aliasEntities(text:String):Set<String> {
        val found = ALIASES.filterKeys { normalize(text).contains(it) }.values.flatten().toSet()
        return if (found.size > 1 && "VIPLA_BATO" in found) found - "VIPLA_BATO" else found
    }
    companion object { private val INDIRECT=Regex("(?iu)(?:\\bto\\b|ono|tome|toga|onda|očigledno|ocigledno|pitao|pitala|pitanje|ranije|prethod|nastavi|vrati|prvi|isto|ako je nastavilo)"); private val NON_ENTITIES=setOf("onda","ono","to","tome","toga","ocigledno","pitao","pitala","pitanje","kako","sta","sto","zasto"); private val STOP=setOf("koji","koje","koja","kako","šta","sta","moje","moja","moj","test","ime","danas","dan","ovaj","ono","sam","smo","ste","biti","ima","the","and","what","which","nastavi","priču","pricu"); private val ALIASES=mapOf("rimsko carstvo" to setOf("ROMAN_EMPIRE"),"roman empire" to setOf("ROMAN_EMPIRE"),"rim" to setOf("ROMAN_EMPIRE"),"zapadno rimsko" to setOf("WESTERN_ROMAN_EMPIRE"),"istocno rimsko" to setOf("EASTERN_ROMAN_EMPIRE"),"istočno rimsko" to setOf("EASTERN_ROMAN_EMPIRE"),"vizantij" to setOf("BYZANTINE_EMPIRE"),"vipla" to setOf("VIPLA_BATO"),"bato" to setOf("VIPLA_BATO")) }

    fun search(query:String)=dao.search(query); suspend fun log(code:String?,type:String,result:String,evidence:String)=dao.log(CockpitEvent(timestamp=System.currentTimeMillis(),controlCode=code,eventType=type,result=result,evidence=evidence)); suspend fun saveState(state:ControlState)=dao.saveState(state); suspend fun runtimeSnapshot():Map<String,String> = dao.runtimeSnapshot().associate{it.key to it.value}; suspend fun putRuntime(key:String,value:String)=dao.putRuntime(RuntimeValue(key,value,System.currentTimeMillis())); suspend fun putKnowledge(key:String,layer:String,content:String)=dao.putKnowledge(KnowledgeObject(key,layer,content,System.currentTimeMillis())); suspend fun searchKnowledge(query:String)=dao.searchKnowledge(query); suspend fun stenoCount()=dao.stenoCount()
}
