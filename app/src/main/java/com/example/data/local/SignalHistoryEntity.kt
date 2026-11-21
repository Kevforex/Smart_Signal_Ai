package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "signal_history",
    indices = [
        Index(value = ["symbol", "timeframe", "candleOpenTimeIso"], unique = true)
    ]
)
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val symbol: String,
    val timeframe: String,
    val candleOpenTimeIso: String,
    val direction: String,
    val status: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskRewardTp1: Double,
    val riskRewardTp2: Double,
    val technicalScore: Int,
    val scoreLabel: String,
    val reasonsJoined: String,
    val h1TrendState: String,
    val signalTimestampIso: String,
    val signalTimestampMs: Long,
    val outcome: String = "EN_COURS",
    val outcomePips: Double = 0.0,
    val outcomeEvaluatedAtIso: String? = null
)
