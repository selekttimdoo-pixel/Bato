package com.vipla.bato.data

import java.util.UUID

class StenoRepository(private val dao: StenoDao) {
    data class RetrievalBundle(
        val steno: List<StenoEvent>,
        val riznica: List<KnowledgeObject>,
        val fastGraph: List<KnowledgeObject>
    )

    val events = dao.observeAll()
    val cockpitLog = dao.observeLog()
    val controlStates = dao.observeStates()
    val runtimeValues = dao.observeRuntime()
    val knowledge = dao.observeKnowledge()

    suspend fun append(role: String, text: String, providerState: String = "LOCAL"): StenoEvent {
        val now = System.currentTimeMillis()
        val previous = dao.recent(1).firstOrNull()
        val gap = previous?.let { now - it.endTs } ?: Long.MAX_VALUE
        val event = StenoEvent(
            role = role, rawText = text, startTs = now, endTs = now,
            sessionId = if (previous == null || gap > 600_000L) UUID.randomUUID().toString() else previous.sessionId,
            segmentId = if (previous == null || gap > 120_000L) UUID.randomUUID().toString() else previous.segmentId,
            providerState = providerState
        )
        dao.insert(event)
        return event
    }

    suspend fun context(limit: Int = 12): List<StenoEvent> = dao.recent(limit).reversed()
    suspend fun retrieve(query: String, excludeEventId: Long, limit: Int = 6): RetrievalBundle {
        val terms = terms(query)
        val steno = dao.retrievalWindow(250)
            .asSequence()
            .filter { it.id != excludeEventId && it.role in setOf("USER", "ASSISTANT") }
            .map { it to score(it.rawText, terms) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<StenoEvent, Int>> { it.second }.thenByDescending { it.first.startTs })
            .take(limit).map { it.first }.toList()
        val allKnowledge = dao.allKnowledge()
        val fastGraph = allKnowledge.filter { it.layer.contains("FAST GRAPH", true) || it.key.contains("fast-graph", true) }
            .take(4)
        val riznica = allKnowledge.asSequence()
            .filterNot { it in fastGraph }
            .map { it to score("${it.key} ${it.layer} ${it.content}", terms) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<KnowledgeObject, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(limit).map { it.first }.toList()
        return RetrievalBundle(steno, riznica, fastGraph)
    }

    private fun terms(text: String): Set<String> = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}-]+"), " ").split(' ')
        .filter { it.length >= 3 && it !in STOP_WORDS }.toSet()

    private fun score(text: String, queryTerms: Set<String>): Int {
        if (queryTerms.isEmpty()) return 0
        val candidate = terms(text)
        return queryTerms.fold(0) { total, term ->
            total + when {
                term in candidate -> if (term.any(Char::isDigit) || term.contains('-')) 6 else 2
                candidate.any { it.contains(term) || term.contains(it) } -> 1
                else -> 0
            }
        }
    }

    companion object {
        private val STOP_WORDS = setOf("koji", "koje", "koja", "kako", "šta", "sta", "moje", "moja", "moj", "test", "ime", "danas", "dan", "ovaj", "ono", "sam", "smo", "ste", "biti", "ima", "the", "and", "what", "which")
    }
    fun search(query: String) = dao.search(query)
    suspend fun log(code: String?, type: String, result: String, evidence: String) =
        dao.log(CockpitEvent(timestamp = System.currentTimeMillis(), controlCode = code, eventType = type, result = result, evidence = evidence))
    suspend fun saveState(state: ControlState) = dao.saveState(state)
    suspend fun runtimeSnapshot(): Map<String, String> = dao.runtimeSnapshot().associate { it.key to it.value }
    suspend fun putRuntime(key: String, value: String) = dao.putRuntime(RuntimeValue(key, value, System.currentTimeMillis()))
    suspend fun putKnowledge(key: String, layer: String, content: String) = dao.putKnowledge(KnowledgeObject(key, layer, content, System.currentTimeMillis()))
    suspend fun searchKnowledge(query: String) = dao.searchKnowledge(query)
    suspend fun stenoCount() = dao.stenoCount()
}
