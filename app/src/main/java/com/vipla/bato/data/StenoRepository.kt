package com.vipla.bato.data

import java.util.UUID

class StenoRepository(private val dao: StenoDao) {
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
