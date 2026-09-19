package com.vipla.bato.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steno_events")
data class StenoEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val startTs: Long,
    val endTs: Long,
    val sessionId: String,
    val segmentId: String,
    val speaker: String = "USER"
)
