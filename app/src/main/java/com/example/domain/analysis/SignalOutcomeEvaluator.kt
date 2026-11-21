package com.example.domain.analysis

import com.example.data.local.SignalHistoryEntity
import com.example.domain.model.Candle
import com.example.domain.model.SignalOutcome
import kotlin.math.abs

object SignalOutcomeEvaluator {

    data class OutcomeUpdate(
        val outcome: SignalOutcome,
        val pips: Double,
        val evaluatedAtIso: String
    )

    /**
     * Evalue le resultat reel d'un signal en cours sur les bougies M15 cloturees ulterieures,
     * sans jamais modifier les parametres initiaux du signal.
     */
    fun evaluateOutcome(
        signal: SignalHistoryEntity,
        closedM15Candles: List<Candle>
    ): OutcomeUpdate? {
        if (signal.outcome != SignalOutcome.EN_COURS.name) return null
        val signalOpenMs = TimeUtils.parseIsoUtcToMillis(signal.candleOpenTimeIso)
        if (signalOpenMs <= 0L) return null

        val subsequent = closedM15Candles.filter { it.openTimeMs > signalOpenMs }
        if (subsequent.isEmpty()) return null

        val pip = TimeUtils.pipSize(signal.symbol)
        val isBuy = signal.direction == "BUY"

        // Max 96 bougies M15 (24 heures) avant expiration
        val horizon = subsequent.take(96)
        for (bar in horizon) {
            if (isBuy) {
                // Regle conservatrice : si SL et TP touches dans la meme bougie, SL prime
                if (bar.low <= signal.stopLoss) {
                    val pips = (signal.stopLoss - signal.entryPrice) / pip
                    return OutcomeUpdate(SignalOutcome.SL_ATTEINT, pips, bar.openTimeIso)
                }
                if (bar.high >= signal.takeProfit2) {
                    val pips = (signal.takeProfit2 - signal.entryPrice) / pip
                    return OutcomeUpdate(SignalOutcome.TP2_ATTEINT, pips, bar.openTimeIso)
                }
                if (bar.high >= signal.takeProfit1) {
                    val pips = (signal.takeProfit1 - signal.entryPrice) / pip
                    return OutcomeUpdate(SignalOutcome.TP1_ATTEINT, pips, bar.openTimeIso)
                }
            } else {
                if (bar.high >= signal.stopLoss) {
                    val pips = (signal.entryPrice - signal.stopLoss) / pip
                    return OutcomeUpdate(SignalOutcome.SL_ATTEINT, pips, bar.openTimeIso)
                }
                if (bar.low <= signal.takeProfit2) {
                    val pips = (signal.entryPrice - signal.takeProfit2) / pip
                    return OutcomeUpdate(SignalOutcome.TP2_ATTEINT, pips, bar.openTimeIso)
                }
                if (bar.low <= signal.takeProfit1) {
                    val pips = (signal.entryPrice - signal.takeProfit1) / pip
                    return OutcomeUpdate(SignalOutcome.TP1_ATTEINT, pips, bar.openTimeIso)
                }
            }
        }

        if (subsequent.size >= 96) {
            val lastBar = horizon.last()
            val pips = if (isBuy) {
                (lastBar.close - signal.entryPrice) / pip
            } else {
                (signal.entryPrice - lastBar.close) / pip
            }
            return OutcomeUpdate(SignalOutcome.EXPIRE, pips, lastBar.openTimeIso)
        }

        return null
    }
}
