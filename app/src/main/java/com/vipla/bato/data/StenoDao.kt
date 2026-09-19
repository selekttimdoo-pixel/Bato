package com.vipla.bato.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StenoDao {
    @Insert
    suspend fun insert(event: StenoEvent): Long

    @Query("SELECT * FROM steno_events ORDER BY startTs DESC")
    fun observeAll(): Flow<List<StenoEvent>>

    @Query("SELECT * FROM steno_events ORDER BY startTs DESC LIMIT 1")
    suspend fun latest(): StenoEvent?

    @Query("SELECT * FROM steno_events WHERE rawText LIKE '%' || :query || '%' ORDER BY startTs DESC")
    suspend fun search(query: String): List<StenoEvent>
}
