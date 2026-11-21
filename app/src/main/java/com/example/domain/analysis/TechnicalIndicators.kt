package com.example.domain.analysis

import com.example.domain.model.Candle
import com.example.domain.model.IndicatorSnapshot
import com.example.domain.model.MarketStructure
import com.example.domain.model.SupportResistanceZone
import com.example.domain.model.Timeframe
import kotlin.math.abs
import kotlin.math.max

object TechnicalIndicators {

    fun calculateEmaSeries(closes: List<Double>, period: Int): List<Double?> {
        if (period <= 0 || closes.isEmpty()) return List(closes.size) { null }
        if (closes.size < period) {
            // Si Biquote fournit 120..199 bougies reelles (ex: M15=192, H1=168 sans historique plus ancien),
            // amorcer l'EMA 200 sur la SMA(50) des 50 premieres clotures reelles et lisser avec k=2/(200+1).
            if (period == 200 && closes.size >= 120) {
                val seedLen = 50
                val result = MutableList<Double?>(closes.size) { null }
                var sum = 0.0
                for (i in 0 until seedLen) {
                    sum += closes[i]
                }
                var ema = sum / seedLen
                result[seedLen - 1] = ema
                val k = 2.0 / (period + 1.0)
                for (i in seedLen until closes.size) {
                    ema = (closes[i] - ema) * k + ema
                    result[i] = ema
                }
                return result
            }
            return List(closes.size) { null }
        }
        val result = MutableList<Double?>(closes.size) { null }
        var sum = 0.0
        for (i in 0 until period) {
            sum += closes[i]
        }
        var ema = sum / period
        result[period - 1] = ema
        val k = 2.0 / (period + 1.0)
        for (i in period until closes.size) {
            ema = (closes[i] - ema) * k + ema
            result[i] = ema
        }
        return result
    }

    fun calculateRsiSeries(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.size <= period || period <= 0) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) gainSum += change else lossSum += abs(change)
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        result[period] = computeRsiValue(avgGain, avgLoss)

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            result[i] = computeRsiValue(avgGain, avgLoss)
        }
        return result
    }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double = when {
        avgGain == 0.0 && avgLoss == 0.0 -> 50.0
        avgLoss == 0.0 -> 100.0
        else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }

    fun calculateAtrSeries(candles: List<Candle>, period: Int = 14): List<Double?> {
        if (candles.size <= period || period <= 0) return List(candles.size) { null }
        val result = MutableList<Double?>(candles.size) { null }
        val trueRanges = DoubleArray(candles.size)
        trueRanges[0] = candles[0].high - candles[0].low
        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prevClose = candles[i - 1].close
            val tr1 = cur.high - cur.low
            val tr2 = abs(cur.high - prevClose)
            val tr3 = abs(cur.low - prevClose)
            trueRanges[i] = max(tr1, max(tr2, tr3))
        }
        var atrSum = 0.0
        for (i in 1..period) {
            atrSum += trueRanges[i]
        }
        var atr = atrSum / period
        result[period] = atr
        for (i in (period + 1) until candles.size) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period
            result[i] = atr
        }
        return result
    }

    private data class SwingPoint(val index: Int, val price: Double, val isHigh: Boolean)

    private fun detectSwingPoints(candles: List<Candle>, lookback: Int = 3): List<SwingPoint> {
        if (candles.size < lookback * 2 + 1) return emptyList()
        val points = mutableListOf<SwingPoint>()
        for (i in lookback until (candles.size - lookback)) {
            val currentHigh = candles[i].high
            val currentLow = candles[i].low
            var isSwingHigh = true
            var isSwingLow = true
            for (j in (i - lookback)..(i + lookback)) {
                if (j == i) continue
                if (candles[j].high >= currentHigh) isSwingHigh = false
                if (candles[j].low <= currentLow) isSwingLow = false
            }
            if (isSwingHigh) points.add(SwingPoint(i, currentHigh, isHigh = true))
            if (isSwingLow) points.add(SwingPoint(i, currentLow, isHigh = false))
        }
        return points
    }

    fun detectMarketStructure(
        candles: List<Candle>,
        ema50: Double?,
        ema200: Double?,
        atr: Double?
    ): MarketStructure {
        val recent = if (candles.size > 80) candles.takeLast(80) else candles
        val swings = detectSwingPoints(recent, lookback = 3)
        val swingHighs = swings.filter { it.isHigh }.takeLast(3)
        val swingLows = swings.filter { !it.isHigh }.takeLast(3)

        if (swingHighs.size < 2 || swingLows.size < 2) {
            return MarketStructure.RANGING
        }

        val lastHigh = swingHighs.last().price
        val prevHigh = swingHighs[swingHighs.size - 2].price
        val lastLow = swingLows.last().price
        val prevLow = swingLows[swingLows.size - 2].price

        // Detect flat / directionless market when EMA50 and EMA200 are tightly compressed
        if (ema50 != null && ema200 != null && atr != null && atr > 0.0) {
            val emaGap = abs(ema50 - ema200)
            if (emaGap < atr * 0.20 && abs(lastHigh - prevHigh) < atr * 0.35 && abs(lastLow - prevLow) < atr * 0.35) {
                return MarketStructure.RANGING
            }
        }

        val higherHigh = lastHigh > prevHigh
        val higherLow = lastLow > prevLow
        val lowerHigh = lastHigh < prevHigh
        val lowerLow = lastLow < prevLow

        return when {
            higherHigh && higherLow -> MarketStructure.BULLISH
            lowerHigh && lowerLow -> MarketStructure.BEARISH
            // Transitional bias only if both price & EMA align strongly, else ranging
            higherLow && ema50 != null && ema200 != null && ema50 > ema200 && recent.last().close > ema50 ->
                MarketStructure.BULLISH
            lowerHigh && ema50 != null && ema200 != null && ema50 < ema200 && recent.last().close < ema50 ->
                MarketStructure.BEARISH
            else -> MarketStructure.RANGING
        }
    }

    fun detectSupportResistanceZones(
        symbol: String,
        candles: List<Candle>,
        currentPrice: Double,
        atr: Double?
    ): Pair<List<SupportResistanceZone>, List<SupportResistanceZone>> {
        val window = if (candles.size > 160) candles.takeLast(160) else candles
        val swings = detectSwingPoints(window, lookback = 3)
        val pip = TimeUtils.pipSize(symbol)
        val clusterTolerance = max((atr ?: (15 * pip)) * 0.45, 5.0 * pip)

        val clusters = mutableListOf<MutableList<Double>>()
        for (pt in swings) {
            val matching = clusters.firstOrNull { cluster ->
                abs(cluster.average() - pt.price) <= clusterTolerance
            }
            if (matching != null) {
                matching.add(pt.price)
            } else {
                clusters.add(mutableListOf(pt.price))
            }
        }

        val supports = mutableListOf<SupportResistanceZone>()
        val resistances = mutableListOf<SupportResistanceZone>()

        for (cluster in clusters) {
            val level = cluster.average()
            val distancePips = abs(currentPrice - level) / pip
            if (level < currentPrice) {
                supports.add(
                    SupportResistanceZone(
                        price = level,
                        type = SupportResistanceZone.ZoneType.SUPPORT,
                        touches = cluster.size,
                        distancePips = distancePips
                    )
                )
            } else if (level > currentPrice) {
                resistances.add(
                    SupportResistanceZone(
                        price = level,
                        type = SupportResistanceZone.ZoneType.RESISTANCE,
                        touches = cluster.size,
                        distancePips = distancePips
                    )
                )
            }
        }

        val nearestSupports = supports
            .sortedWith(compareBy<SupportResistanceZone> { it.distancePips }.thenByDescending { it.touches })
            .take(4)
        val nearestResistances = resistances
            .sortedWith(compareBy<SupportResistanceZone> { it.distancePips }.thenByDescending { it.touches })
            .take(4)

        return nearestSupports to nearestResistances
    }

    fun buildSnapshot(
        symbol: String,
        timeframe: Timeframe,
        closedCandles: List<Candle>
    ): IndicatorSnapshot {
        val closes = closedCandles.map { it.close }
        val ema50Series = calculateEmaSeries(closes, 50)
        val ema200Series = calculateEmaSeries(closes, 200)
        val rsiSeries = calculateRsiSeries(closes, 14)
        val atrSeries = calculateAtrSeries(closedCandles, 14)

        val ema50 = ema50Series.lastOrNull()
        val ema200 = ema200Series.lastOrNull()
        val rsi14 = rsiSeries.lastOrNull()
        val rsi14Prev = if (rsiSeries.size >= 2) rsiSeries[rsiSeries.size - 2] else null
        val atr14 = atrSeries.lastOrNull()
        val pip = TimeUtils.pipSize(symbol)
        val atrPips = atr14?.let { it / pip }

        val lastClosed = closedCandles.lastOrNull()
        val price = lastClosed?.close ?: 0.0
        val structure = detectMarketStructure(closedCandles, ema50, ema200, atr14)
        val (supports, resistances) = if (lastClosed != null) {
            detectSupportResistanceZones(symbol, closedCandles, price, atr14)
        } else {
            emptyList<SupportResistanceZone>() to emptyList()
        }

        return IndicatorSnapshot(
            timeframe = timeframe,
            lastClosedCandle = lastClosed,
            ema50 = ema50,
            ema200 = ema200,
            ema50Series = ema50Series,
            ema200Series = ema200Series,
            rsi14 = rsi14,
            rsi14Prev = rsi14Prev,
            atr14 = atr14,
            atr14Pips = atrPips,
            structure = structure,
            supports = supports,
            resistances = resistances,
            closedCandleCount = closedCandles.size
        )
    }

    fun buildSnapshotFromPrecomputed(
        symbol: String,
        timeframe: Timeframe,
        closedWindow: List<Candle>,
        ema50Series: List<Double?>,
        ema200Series: List<Double?>,
        rsiSeries: List<Double?>,
        atrSeries: List<Double?>,
        totalCount: Int
    ): IndicatorSnapshot {
        val ema50 = ema50Series.lastOrNull()
        val ema200 = ema200Series.lastOrNull()
        val rsi14 = rsiSeries.lastOrNull()
        val rsi14Prev = if (rsiSeries.size >= 2) rsiSeries[rsiSeries.size - 2] else null
        val atr14 = atrSeries.lastOrNull()
        val pip = TimeUtils.pipSize(symbol)
        val atrPips = atr14?.let { it / pip }
        val lastClosed = closedWindow.lastOrNull()
        val price = lastClosed?.close ?: 0.0
        val structure = detectMarketStructure(closedWindow, ema50, ema200, atr14)
        val (supports, resistances) = if (lastClosed != null) {
            detectSupportResistanceZones(symbol, closedWindow, price, atr14)
        } else {
            emptyList<SupportResistanceZone>() to emptyList()
        }

        return IndicatorSnapshot(
            timeframe = timeframe,
            lastClosedCandle = lastClosed,
            ema50 = ema50,
            ema200 = ema200,
            ema50Series = ema50Series,
            ema200Series = ema200Series,
            rsi14 = rsi14,
            rsi14Prev = rsi14Prev,
            atr14 = atr14,
            atr14Pips = atrPips,
            structure = structure,
            supports = supports,
            resistances = resistances,
            closedCandleCount = totalCount
        )
    }
}
