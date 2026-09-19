package com.vipla.bato.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steno_events")
data class StenoEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val rawText: String,
    val startTs: Long,
    val endTs: Long,
    val sessionId: String,
    val segmentId: String,
    val providerState: String = "LOCAL"
)

@Entity(tableName = "cockpit_events")
data class CockpitEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val controlCode: String?,
    val eventType: String,
    val result: String,
    val evidence: String
)

@Entity(tableName = "control_state")
data class ControlState(
    @PrimaryKey val code: String,
    val localPass: Boolean,
    val forcedBlockPass: Boolean,
    val effectProven: Boolean,
    val light: String,
    val updatedAt: Long,
    val handler: String = "",
    val observableResult: String = "",
    val evidence: String = ""
)

@Entity(tableName = "runtime_values")
data class RuntimeValue(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long
)

@Entity(tableName = "knowledge_objects")
data class KnowledgeObject(
    @PrimaryKey val key: String,
    val layer: String,
    val content: String,
    val updatedAt: Long
)
