package com.example.domain.analysis

import com.example.domain.model.RiskCalculation
import kotlin.math.abs

object RiskCalculator {

    /**
     * Calcule la taille de position brute sans imposer de lot minimum (ex: 0.004 lot possible).
     */
    fun calculate(
        symbol: String,
        accountCapital: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfit1Price: Double?,
        takeProfit2Price: Double?,
        spreadPips: Double
    ): RiskCalculation {
        val cleanCapital = if (accountCapital.isFinite()) accountCapital else 1000.0
        val cleanRisk = if (riskPercent.isFinite()) riskPercent else 1.0
        val cleanSpread = if (spreadPips.isFinite()) spreadPips.coerceAtLeast(0.0) else 0.0

        val safeCapital = cleanCapital.coerceAtLeast(1.0)
        val safeRiskPct = cleanRisk.coerceIn(0.05, 100.0)
        val riskAmount = safeCapital * (safeRiskPct / 100.0)

        val validEntry = entryPrice.isFinite() && entryPrice > 0.0
        val validSl = stopLossPrice.isFinite() && stopLossPrice > 0.0
        val pip = TimeUtils.pipSize(symbol)
        val slPips = if (validEntry && validSl) abs(entryPrice - stopLossPrice) / pip else 0.0
        val pipValuePerLot = if (validEntry) TimeUtils.pipValuePerStandardLotUsd(symbol, entryPrice) else 10.0

        val lotsRaw = if (slPips > 0.0 && pipValuePerLot > 0.0) {
            riskAmount / (slPips * pipValuePerLot)
        } else {
            0.0
        }
        val units = lotsRaw * 100_000.0

        val tp1Pips = takeProfit1Price
            ?.takeIf { it.isFinite() && it > 0.0 && validEntry }
            ?.let { abs(it - entryPrice) / pip } ?: 0.0
        val tp2Pips = takeProfit2Price
            ?.takeIf { it.isFinite() && it > 0.0 && validEntry }
            ?.let { abs(it - entryPrice) / pip } ?: 0.0

        val netTp1Pips = (tp1Pips - cleanSpread).coerceAtLeast(0.0)
        val effectiveSlPips = (slPips + cleanSpread).coerceAtLeast(0.1)
        val effectiveRr = if (slPips > 0.0) netTp1Pips / effectiveSlPips else 0.0

        val profitTp1 = netTp1Pips * pipValuePerLot * lotsRaw
        val profitTp2 = (tp2Pips - cleanSpread).coerceAtLeast(0.0) * pipValuePerLot * lotsRaw

        return RiskCalculation(
            accountCapital = safeCapital,
            riskPercent = safeRiskPct,
            riskAmountCurrency = riskAmount,
            stopLossPips = slPips,
            positionLotsRaw = lotsRaw,
            positionUnits = units,
            potentialProfitTp1 = profitTp1,
            potentialProfitTp2 = profitTp2,
            effectiveRiskRewardAfterSpread = effectiveRr
        )
    }
}
