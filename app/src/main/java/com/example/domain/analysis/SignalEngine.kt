package com.example.domain.analysis

import com.example.domain.model.Candle
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.H1TrendState
import com.example.domain.model.IndicatorSnapshot
import com.example.domain.model.MarketStructure
import com.example.domain.model.ScoreCriterion
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.TradingSignal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SignalEngine {

    const val MIN_CLOSED_CANDLES = 120

    fun evaluate(
        symbol: String,
        closedM15: List<Candle>,
        closedH1: List<Candle>,
        freshnessStatus: FreshnessStatus,
        m15Snapshot: IndicatorSnapshot,
        h1Snapshot: IndicatorSnapshot
    ): TradingSignal {
        val lastM15 = closedM15.lastOrNull()
        val lastH1 = closedH1.lastOrNull()
        val signalTimeMs = lastM15?.openTimeMs?.plus(15 * 60_000L) ?: System.currentTimeMillis()
        val signalTimeIso = TimeUtils.formatUtcIso(signalTimeMs)
        val candleOpenIso = lastM15?.openTimeIso ?: ""

        // 1. Freshness & market open check
        if (!freshnessStatus.isTradable) {
            return noTrade(
                symbol = symbol,
                h1TrendState = inferH1Trend(closedH1, h1Snapshot),
                blockingReasons = listOf(freshnessStatus.labelFr),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        // 2. Sufficient data check
        if (closedM15.size < MIN_CLOSED_CANDLES || closedH1.size < MIN_CLOSED_CANDLES ||
            lastM15 == null || lastH1 == null
        ) {
            return noTrade(
                symbol = symbol,
                h1TrendState = H1TrendState.NEUTRAL,
                blockingReasons = listOf(
                    "Données historiques insuffisantes (M15: ${closedM15.size}/$MIN_CLOSED_CANDLES, H1: ${closedH1.size}/$MIN_CLOSED_CANDLES bougies clôturées)."
                ),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        val h1Trend = inferH1Trend(closedH1, h1Snapshot)
        val m15Ema50 = m15Snapshot.ema50
        val m15Ema200 = m15Snapshot.ema200
        val m15Rsi = m15Snapshot.rsi14
        val m15RsiPrev = m15Snapshot.rsi14Prev
        val m15Atr = m15Snapshot.atr14

        if (m15Ema50 == null || m15Ema200 == null || m15Rsi == null || m15RsiPrev == null ||
            m15Atr == null || m15Atr <= 0.0 ||
            !m15Ema50.isFinite() || !m15Ema200.isFinite() || !m15Rsi.isFinite() || !m15Atr.isFinite()
        ) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf("Indicateurs techniques incomplets sur les bougies clôturées."),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        // 3. Reject directionless H1 or ranging M15
        if (h1Trend == H1TrendState.NEUTRAL) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf("Tendance H1 neutre ou indécise : aucune confirmation multi-timeframe."),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        if (m15Snapshot.structure == MarketStructure.RANGING) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf("Structure M15 sans direction claire (range / compression EMA)."),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        // 4. Reject contradictory signals between H1 and M15
        val candidateDirection = when (h1Trend) {
            H1TrendState.BULLISH -> SignalDirection.BUY
            H1TrendState.BEARISH -> SignalDirection.SELL
            H1TrendState.NEUTRAL -> SignalDirection.NO_TRADE
        }

        val m15BullishBias = lastM15.close > m15Ema200 && m15Ema50 > m15Ema200
        val m15BearishBias = lastM15.close < m15Ema200 && m15Ema50 < m15Ema200

        if (candidateDirection == SignalDirection.BUY &&
            (m15Snapshot.structure == MarketStructure.BEARISH || !m15BullishBias)
        ) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf("Contradiction M15/H1 : H1 haussier mais M15 non aligné au-dessus d'EMA 50/200."),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        if (candidateDirection == SignalDirection.SELL &&
            (m15Snapshot.structure == MarketStructure.BULLISH || !m15BearishBias)
        ) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf("Contradiction M15/H1 : H1 baissier mais M15 non aligné sous EMA 50/200."),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        // 5. Volatility sanity check via ATR(14)
        val pip = TimeUtils.pipSize(symbol)
        val atrPips = m15Atr / pip
        if (atrPips < 3.0) {
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = listOf(
                    "Volatilité ATR(14) trop faible (${String.format(Locale.US, "%.1f", atrPips)} pips) : marché inactif."
                ),
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        // 6. Score Technical Confluence (0 - 100) across 5 weighted criteria
        val criteria = mutableListOf<ScoreCriterion>()
        val reasons = mutableListOf<String>()
        val prevM15 = closedM15[closedM15.size - 2]
        val m15Ema50Prev = m15Snapshot.ema50Series.getOrNull(m15Snapshot.ema50Series.size - 4) ?: m15Ema50

        // Criterion A: H1 Trend & Structure (25 pts)
        val h1Score = evaluateH1Score(h1Trend, h1Snapshot)
        criteria.add(
            ScoreCriterion(
                title = "Confirmation tendance H1",
                earnedPoints = h1Score,
                maxPoints = 25,
                detail = if (h1Trend == H1TrendState.BULLISH) {
                    "Prix H1 > EMA50 > EMA200 (${h1Snapshot.structure.labelFr})"
                } else {
                    "Prix H1 < EMA50 < EMA200 (${h1Snapshot.structure.labelFr})"
                },
                passed = h1Score >= 20
            )
        )
        if (h1Score >= 20) {
            reasons.add("Tendance H1 ${h1Trend.labelFr.lowercase()} confirmée par EMA 50/200 et structure.")
        }

        // Criterion B: M15 EMA50 / EMA200 Alignment & Slope (20 pts)
        val emaScore = evaluateM15EmaScore(candidateDirection, lastM15, m15Ema50, m15Ema50Prev, m15Ema200, m15Atr)
        criteria.add(
            ScoreCriterion(
                title = "Alignement EMA 50 / 200 (M15)",
                earnedPoints = emaScore,
                maxPoints = 20,
                detail = if (candidateDirection == SignalDirection.BUY) {
                    "EMA50 (${TimeUtils.formatPrice(symbol, m15Ema50)}) > EMA200, pente positive"
                } else {
                    "EMA50 (${TimeUtils.formatPrice(symbol, m15Ema50)}) < EMA200, pente négative"
                },
                passed = emaScore >= 14
            )
        )
        if (emaScore >= 14) {
            reasons.add("Moyennes mobiles M15 alignées (EMA 50 vs EMA 200) avec écart sain.")
        }

        // Criterion C: RSI(14) Momentum (15 pts)
        val rsiScore = evaluateRsiScore(candidateDirection, m15Rsi, m15RsiPrev)
        criteria.add(
            ScoreCriterion(
                title = "Momentum RSI 14 (M15)",
                earnedPoints = rsiScore,
                maxPoints = 15,
                detail = "RSI(14) clôturé à ${String.format(Locale.US, "%.1f", m15Rsi)} (préc. ${String.format(Locale.US, "%.1f", m15RsiPrev)})",
                passed = rsiScore >= 10
            )
        )
        if (rsiScore >= 10) {
            reasons.add("RSI(14) à ${String.format(Locale.US, "%.1f", m15Rsi)} en zone de momentum favorable sans essoufflement.")
        }

        // Criterion D: Market Structure & S/R Proximity / Pullback Trigger (20 pts)
        val structureSrScore = evaluateStructureAndTriggerScore(
            direction = candidateDirection,
            lastM15 = lastM15,
            prevM15 = prevM15,
            m15Ema50 = m15Ema50,
            m15Atr = m15Atr,
            m15Snapshot = m15Snapshot
        )
        criteria.add(
            ScoreCriterion(
                title = "Structure & Support/Résistance",
                earnedPoints = structureSrScore,
                maxPoints = 20,
                detail = "${m15Snapshot.structure.labelFr}, bougie M15 clôturée en impulsion",
                passed = structureSrScore >= 14
            )
        )
        if (structureSrScore >= 14) {
            reasons.add("Rejet/impulsion sur zone clé (${m15Snapshot.structure.labelFr}) confirmée à la clôture M15.")
        }

        // Calculate SL, TP1, TP2 based on ATR(14) & recent swing structure
        val entryPrice = lastM15.close
        val recentSlice = closedM15.takeLast(14)
        val (stopLoss, tp1, tp2) = computeRiskLevels(
            direction = candidateDirection,
            entry = entryPrice,
            atr = m15Atr,
            recentCandles = recentSlice,
            m15Snapshot = m15Snapshot
        )

        val slDist = abs(entryPrice - stopLoss)
        val tp1Dist = abs(tp1 - entryPrice)
        val tp2Dist = abs(tp2 - entryPrice)
        val rr1 = if (slDist > 0.0) tp1Dist / slDist else 0.0
        val rr2 = if (slDist > 0.0) tp2Dist / slDist else 0.0

        // Criterion E: Volatility ATR & R:R Clearance (20 pts)
        val riskRewardScore = evaluateVolatilityAndRrScore(
            direction = candidateDirection,
            entryPrice = entryPrice,
            tp1 = tp1,
            atrPips = atrPips,
            rr1 = rr1,
            m15Snapshot = m15Snapshot,
            pip = pip
        )
        criteria.add(
            ScoreCriterion(
                title = "Volatilité ATR & Ratio R:R",
                earnedPoints = riskRewardScore,
                maxPoints = 20,
                detail = "ATR ${String.format(Locale.US, "%.1f", atrPips)} pips • R:R TP1 = 1:${String.format(Locale.US, "%.2f", rr1)}",
                passed = riskRewardScore >= 14
            )
        )
        if (riskRewardScore >= 14) {
            reasons.add("Ratio Risque/Rendement favorable (1:${String.format(Locale.US, "%.2f", rr1)} sur TP1) avec marge S/R.")
        }

        val totalScore = criteria.sumOf { it.earnedPoints }.coerceIn(0, 100)

        // Enforce strict score rules:
        // 90-100: Excellente configuration (CONFIRMED)
        // 80-89: Bonne configuration (CONFIRMED)
        // 70-79: Configuration moyenne (PENDING - wait for stronger confluence)
        // < 70: Aucun signal (NO TRADE)
        if (totalScore < 70 || rr1 < 1.3) {
            val blockList = mutableListOf<String>()
            if (totalScore < 70) {
                blockList.add("Score technique insuffisant ($totalScore/100 < 70) : aucun signal émis.")
            }
            if (rr1 < 1.3) {
                blockList.add("Ratio Risque/Rendement TP1 insuffisant (1:${String.format(Locale.US, "%.2f", rr1)} < 1:1.30).")
            }
            return noTrade(
                symbol = symbol,
                h1TrendState = h1Trend,
                blockingReasons = blockList,
                criteria = criteria,
                score = totalScore,
                signalTimeIso = signalTimeIso,
                signalTimeMs = signalTimeMs,
                candleOpenIso = candleOpenIso
            )
        }

        val (status, scoreLabel) = when {
            totalScore >= 90 -> SignalStatus.CONFIRMED to "Excellente configuration"
            totalScore >= 80 -> SignalStatus.CONFIRMED to "Bonne configuration"
            else -> SignalStatus.PENDING to "Configuration moyenne — En attente de confirmation"
        }

        return TradingSignal(
            symbol = symbol,
            direction = candidateDirection,
            status = status,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            riskRewardTp1 = rr1,
            riskRewardTp2 = rr2,
            technicalScore = totalScore,
            scoreLabel = scoreLabel,
            criteriaBreakdown = criteria,
            reasons = reasons,
            blockingReasons = emptyList(),
            h1TrendState = h1Trend,
            signalTimestampIso = signalTimeIso,
            signalTimestampMs = signalTimeMs,
            candleOpenTimeIso = candleOpenIso
        )
    }

    fun inferH1Trend(closedH1: List<Candle>, h1Snapshot: IndicatorSnapshot): H1TrendState {
        val last = closedH1.lastOrNull() ?: return H1TrendState.NEUTRAL
        val ema50 = h1Snapshot.ema50 ?: return H1TrendState.NEUTRAL
        val ema200 = h1Snapshot.ema200 ?: return H1TrendState.NEUTRAL
        val ema50Prev = h1Snapshot.ema50Series.getOrNull(h1Snapshot.ema50Series.size - 4) ?: ema50

        return when {
            last.close > ema50 && ema50 > ema200 && ema50 >= ema50Prev &&
                h1Snapshot.structure != MarketStructure.BEARISH -> H1TrendState.BULLISH
            last.close < ema50 && ema50 < ema200 && ema50 <= ema50Prev &&
                h1Snapshot.structure != MarketStructure.BULLISH -> H1TrendState.BEARISH
            else -> H1TrendState.NEUTRAL
        }
    }

    private fun evaluateH1Score(h1Trend: H1TrendState, h1Snapshot: IndicatorSnapshot): Int {
        var pts = 15
        if (h1Trend == H1TrendState.BULLISH && h1Snapshot.structure == MarketStructure.BULLISH) pts += 6
        if (h1Trend == H1TrendState.BEARISH && h1Snapshot.structure == MarketStructure.BEARISH) pts += 6
        val rsi = h1Snapshot.rsi14 ?: 50.0
        if ((h1Trend == H1TrendState.BULLISH && rsi in 50.0..70.0) ||
            (h1Trend == H1TrendState.BEARISH && rsi in 30.0..50.0)
        ) {
            pts += 4
        }
        return pts.coerceAtMost(25)
    }

    private fun evaluateM15EmaScore(
        direction: SignalDirection,
        lastM15: Candle,
        ema50: Double,
        ema50Prev: Double,
        ema200: Double,
        atr: Double
    ): Int {
        var pts = 0
        val separation = abs(ema50 - ema200)
        if (direction == SignalDirection.BUY) {
            if (ema50 > ema200) pts += 8
            if (lastM15.close > ema50) pts += 5
            if (ema50 > ema50Prev) pts += 4
            if (separation >= atr * 0.4) pts += 3
        } else {
            if (ema50 < ema200) pts += 8
            if (lastM15.close < ema50) pts += 5
            if (ema50 < ema50Prev) pts += 4
            if (separation >= atr * 0.4) pts += 3
        }
        return pts.coerceAtMost(20)
    }

    private fun evaluateRsiScore(direction: SignalDirection, rsi: Double, rsiPrev: Double): Int {
        return if (direction == SignalDirection.BUY) {
            when {
                rsi in 52.0..66.0 && rsi > rsiPrev -> 15
                rsi in 48.0..69.0 -> 11
                rsi in 44.0..72.0 -> 7
                else -> 2
            }
        } else {
            when {
                rsi in 34.0..48.0 && rsi < rsiPrev -> 15
                rsi in 31.0..52.0 -> 11
                rsi in 28.0..56.0 -> 7
                else -> 2
            }
        }
    }

    private fun evaluateStructureAndTriggerScore(
        direction: SignalDirection,
        lastM15: Candle,
        prevM15: Candle,
        m15Ema50: Double,
        m15Atr: Double,
        m15Snapshot: IndicatorSnapshot
    ): Int {
        var pts = 0
        if (direction == SignalDirection.BUY && m15Snapshot.structure == MarketStructure.BULLISH) pts += 8
        if (direction == SignalDirection.SELL && m15Snapshot.structure == MarketStructure.BEARISH) pts += 8

        // Closed candle confirms bullish/bearish momentum close
        val body = abs(lastM15.close - lastM15.open)
        if (direction == SignalDirection.BUY && lastM15.close > lastM15.open && lastM15.close > prevM15.high) {
            pts += 6
        } else if (direction == SignalDirection.BUY && lastM15.close > lastM15.open && body >= m15Atr * 0.25) {
            pts += 4
        } else if (direction == SignalDirection.SELL && lastM15.close < lastM15.open && lastM15.close < prevM15.low) {
            pts += 6
        } else if (direction == SignalDirection.SELL && lastM15.close < lastM15.open && body >= m15Atr * 0.25) {
            pts += 4
        }

        // Healthy pullback near EMA50 or Support/Resistance zone (not overextended > 2.2 ATR)
        val distFromEma50 = abs(lastM15.close - m15Ema50)
        if (distFromEma50 <= m15Atr * 1.4) {
            pts += 6
        } else if (distFromEma50 <= m15Atr * 2.2) {
            pts += 3
        }

        return pts.coerceAtMost(20)
    }

    private fun evaluateVolatilityAndRrScore(
        direction: SignalDirection,
        entryPrice: Double,
        tp1: Double,
        atrPips: Double,
        rr1: Double,
        m15Snapshot: IndicatorSnapshot,
        pip: Double
    ): Int {
        var pts = 0
        // Optimal Forex M15 ATR window
        pts += when {
            atrPips in 5.0..28.0 -> 8
            atrPips in 3.5..38.0 -> 5
            else -> 2
        }
        // R:R quality
        pts += when {
            rr1 >= 1.8 -> 7
            rr1 >= 1.5 -> 5
            else -> 2
        }
        // Check no immediate S/R obstacle blocking TP1 before 60% of path
        val blockingZone = if (direction == SignalDirection.BUY) {
            m15Snapshot.resistances.firstOrNull()?.let { res ->
                res.price > entryPrice && (res.price - entryPrice) < (tp1 - entryPrice) * 0.65 && res.distancePips > 3.0
            } ?: false
        } else {
            m15Snapshot.supports.firstOrNull()?.let { sup ->
                sup.price < entryPrice && (entryPrice - sup.price) < (entryPrice - tp1) * 0.65 && sup.distancePips > 3.0
            } ?: false
        }
        if (!blockingZone) pts += 5 else pts += 1

        return pts.coerceAtMost(20)
    }

    private fun computeRiskLevels(
        direction: SignalDirection,
        entry: Double,
        atr: Double,
        recentCandles: List<Candle>,
        m15Snapshot: IndicatorSnapshot
    ): Triple<Double, Double, Double> {
        return if (direction == SignalDirection.BUY) {
            val swingLow = recentCandles.minOf { it.low }
            val atrSl = entry - (1.6 * atr)
            val structuralSl = swingLow - (0.3 * atr)
            // Keep SL tight enough yet structurally protected (bounded 1.1*ATR..2.4*ATR)
            val sl = max(min(atrSl, structuralSl), entry - (2.4 * atr))
                .coerceAtMost(entry - (1.1 * atr))
            val risk = entry - sl
            val tp1 = entry + (risk * 1.7)
            val tp2 = entry + (risk * 2.8)
            Triple(sl, tp1, tp2)
        } else {
            val swingHigh = recentCandles.maxOf { it.high }
            val atrSl = entry + (1.6 * atr)
            val structuralSl = swingHigh + (0.3 * atr)
            val sl = min(max(atrSl, structuralSl), entry + (2.4 * atr))
                .coerceAtLeast(entry + (1.1 * atr))
            val risk = sl - entry
            val tp1 = entry - (risk * 1.7)
            val tp2 = entry - (risk * 2.8)
            Triple(sl, tp1, tp2)
        }
    }

    private fun noTrade(
        symbol: String,
        h1TrendState: H1TrendState,
        blockingReasons: List<String>,
        criteria: List<ScoreCriterion> = emptyList(),
        score: Int = 0,
        signalTimeIso: String,
        signalTimeMs: Long,
        candleOpenIso: String
    ): TradingSignal {
        return TradingSignal(
            symbol = symbol,
            direction = SignalDirection.NO_TRADE,
            status = SignalStatus.NO_TRADE,
            entryPrice = null,
            stopLoss = null,
            takeProfit1 = null,
            takeProfit2 = null,
            riskRewardTp1 = null,
            riskRewardTp2 = null,
            technicalScore = score,
            scoreLabel = "Aucun signal (NO TRADE)",
            criteriaBreakdown = criteria,
            reasons = emptyList(),
            blockingReasons = blockingReasons,
            h1TrendState = h1TrendState,
            signalTimestampIso = signalTimeIso,
            signalTimestampMs = signalTimeMs,
            candleOpenTimeIso = candleOpenIso
        )
    }
}
