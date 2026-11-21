package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalHistoryDao {
    @Query("SELECT * FROM signal_history ORDER BY signalTimestampMs DESC")
    fun observeAllSignals(): Flow<List<SignalHistoryEntity>>

    @Query("SELECT * FROM signal_history WHERE symbol = :symbol AND outcome = 'EN_COURS'")
    suspend fun getOpenOutcomeSignals(symbol: String): List<SignalHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: SignalHistoryEntity): Long

    @Query(
        "UPDATE signal_history SET outcome = :outcome, outcomePips = :pips, outcomeEvaluatedAtIso = :evaluatedAtIso " +
            "WHERE id = :id AND outcome = 'EN_COURS'"
    )
    suspend fun resolveOutcomeIfOpen(
        id: Long,
        outcome: String,
        pips: Double,
        evaluatedAtIso: String
    )
}
