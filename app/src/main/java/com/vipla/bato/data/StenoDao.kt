package com.vipla.bato.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StenoDao {
    @Insert suspend fun insert(event: StenoEvent): Long
    @Query("SELECT * FROM steno_events ORDER BY startTs DESC") fun observeAll(): Flow<List<StenoEvent>>
    @Query("SELECT * FROM steno_events ORDER BY startTs DESC LIMIT :limit") suspend fun recent(limit: Int): List<StenoEvent>
    @Query("SELECT * FROM steno_events WHERE rawText LIKE '%' || :query || '%' ORDER BY startTs DESC") fun search(query: String): Flow<List<StenoEvent>>
    @Insert suspend fun log(event: CockpitEvent): Long
    @Query("SELECT * FROM cockpit_events ORDER BY timestamp DESC") fun observeLog(): Flow<List<CockpitEvent>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveState(state: ControlState)
    @Query("SELECT * FROM control_state ORDER BY code") fun observeStates(): Flow<List<ControlState>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRuntime(value: RuntimeValue)
    @Query("SELECT * FROM runtime_values") suspend fun runtimeSnapshot(): List<RuntimeValue>
    @Query("SELECT * FROM runtime_values ORDER BY updatedAt DESC") fun observeRuntime(): Flow<List<RuntimeValue>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putKnowledge(value: KnowledgeObject)
    @Query("SELECT * FROM knowledge_objects ORDER BY updatedAt DESC") fun observeKnowledge(): Flow<List<KnowledgeObject>>
    @Query("SELECT * FROM knowledge_objects WHERE key LIKE '%' || :query || '%' OR layer LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC") suspend fun searchKnowledge(query: String): List<KnowledgeObject>
    @Query("SELECT COUNT(*) FROM steno_events") suspend fun stenoCount(): Int
}
