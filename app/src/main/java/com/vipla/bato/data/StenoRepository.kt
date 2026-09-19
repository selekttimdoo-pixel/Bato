package com.vipla.bato.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

class StenoRepository(private val dao: StenoDao) {
    private val segmentGapMs = 120_000L
    private val sessionGapMs = 600_000L

    val events: Flow<List<StenoEvent>> = dao.observeAll()

    suspend fun append(text: String, now: Long = System.currentTimeMillis()) {
        val previous = dao.latest()
        val gap = previous?.let { now - it.endTs } ?: Long.MAX_VALUE

        val sessionId = when {
            previous == null || gap > sessionGapMs -> UUID.randomUUID().toString()
            else -> previous.sessionId
        }

        val segmentId = when {
            previous == null || gap > segmentGapMs -> UUID.randomUUID().toString()
            else -> previous.segmentId
        }

        dao.insert(
            StenoEvent(
                rawText = text,
                startTs = now,
                endTs = now,
                sessionId = sessionId,
                segmentId = segmentId
            )
        )
    }

    suspend fun search(query: String): List<StenoEvent> = dao.search(query)
}
