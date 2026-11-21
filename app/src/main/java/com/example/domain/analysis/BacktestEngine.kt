package com.example.domain.analysis

import com.example.domain.model.BacktestConfig
import com.example.domain.model.BacktestResult
import com.example.domain.model.BacktestTrade
import com.example.domain.model.Candle
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.SignalDirection
import com.example.domain.model.Timeframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object BacktestEngine {

    /**
     * Simule la stratégie pas à pas sur les bougies historiques clôturées disponibles (M15 + H1).
     * N'invente jamais de données : retourne une erreur explicite si l'historique est insuffisant.
     */
    fun run(
        config: BacktestConfig,
        closedM15: List<Candle>,
        closedH1: List<Candle>
    ): BacktestResult {
        val cleanM15 = sanitizeClosedCandles(closedM15)
        val cleanH1 = sanitizeClosedCandles(closedH1)

        val minRequired = SignalEngine.MIN_CLOSED_CANDLES + 20
        if (cleanM15.size < minRequired || cleanH1.size < SignalEngine.MIN_CLOSED_CANDLES) {
            return BacktestResult(
                config = config,
                testedCandlesCount = cleanM15.size,
                startDateIso = cleanM15.firstOrNull()?.openTimeIso ?: "",
                endDateIso = cleanM15.lastOrNull()?.openTimeIso ?: "",
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0,
                profitFactor = 0.0,
                maxDrawdownPercent = 0.0,
                maxDrawdownCurrency = 0.0,
                grossProfitCurrency = 0.0,
                grossLossCurrency = 0.0,
                totalEstimatedCostsCurrency = 0.0,
                netProfitCurrency = 0.0,
                finalCapital = config.initialCapital,
                returnPercent = 0.0,
                equityCurve = listOf(config.initialCapital),
                trades = emptyList(),
                errorMessage = "Données historiques insuffisantes pour le backtest (M15: ${cleanM15.size}, H1: ${cleanH1.size})."
            )
        }

        // Precompute full series once for O(N) walk-forward indicator lookup
        val m15Closes = cleanM15.map { it.close }
        val m15Ema50 = TechnicalIndicators.calculateEmaSeries(m15Closes, 50)
        val m15Ema200 = TechnicalIndicators.calculateEmaSeries(m15Closes, 200)
        val m15Rsi = TechnicalIndicators.calculateRsiSeries(m15Closes, 14)
        val m15Atr = TechnicalIndicators.calculateAtrSeries(cleanM15, 14)

        val h1Closes = cleanH1.map { it.close }
        val h1Ema50 = TechnicalIndicators.calculateEmaSeries(h1Closes, 50)
        val h1Ema200 = TechnicalIndicators.calculateEmaSeries(h1Closes, 200)
        val h1Rsi = TechnicalIndicators.calculateRsiSeries(h1Closes, 14)
        val h1Atr = TechnicalIndicators.calculateAtrSeries(cleanH1, 14)

        val pip = TimeUtils.pipSize(config.symbol)
        val spreadPips = config.spreadPips.coerceAtLeast(0.0)
        val commissionPips = config.commissionPips.coerceAtLeast(0.0)
        val totalCostPips = spreadPips + commissionPips

        var equity = config.initialCapital.coerceAtLeast(1.0)
        val trades = mutableListOf<BacktestTrade>()

        var i = SignalEngine.MIN_CLOSED_CANDLES
        var h1Pointer = 0

        while (i < cleanM15.size - 2) {
            val currentM15 = cleanM15[i]
            val m15CloseTimeMs = currentM15.openTimeMs + Timeframe.M15.durationMs

            // Advance H1 pointer strictly to H1 candles already closed at or before m15CloseTimeMs
            while (h1Pointer + 1 < cleanH1.size &&
                (cleanH1[h1Pointer + 1].openTimeMs + Timeframe.H1.durationMs) <= m15CloseTimeMs
            ) {
                h1Pointer++
            }

            if (h1Pointer < SignalEngine.MIN_CLOSED_CANDLES) {
                i++
                continue
            }

            val m15SliceStart = max(0, i - 160)
            val m15Window = cleanM15.subList(m15SliceStart, i + 1)
            val h1SliceStart = max(0, h1Pointer - 120)
            val h1Window = cleanH1.subList(h1SliceStart, h1Pointer + 1)

            val m15Snap = TechnicalIndicators.buildSnapshotFromPrecomputed(
                symbol = config.symbol,
                timeframe = Timeframe.M15,
                closedWindow = m15Window,
                ema50Series = m15Ema50.subList(m15SliceStart, i + 1),
                ema200Series = m15Ema200.subList(m15SliceStart, i + 1),
                rsiSeries = m15Rsi.subList(m15SliceStart, i + 1),
                atrSeries = m15Atr.subList(m15SliceStart, i + 1),
                totalCount = i + 1
            )

            val h1Snap = TechnicalIndicators.buildSnapshotFromPrecomputed(
                symbol = config.symbol,
                timeframe = Timeframe.H1,
                closedWindow = h1Window,
                ema50Series = h1Ema50.subList(h1SliceStart, h1Pointer + 1),
                ema200Series = h1Ema200.subList(h1SliceStart, h1Pointer + 1),
                rsiSeries = h1Rsi.subList(h1SliceStart, h1Pointer + 1),
                atrSeries = h1Atr.subList(h1SliceStart, h1Pointer + 1),
                totalCount = h1Pointer + 1
            )

            val signal = SignalEngine.evaluate(
                symbol = config.symbol,
                closedM15 = m15Window,
                closedH1 = h1Window,
                freshnessStatus = FreshnessStatus.FRESH,
                m15Snapshot = m15Snap,
                h1Snapshot = h1Snap
            )

            val entryPrice = signal.entryPrice
            val stopLoss = signal.stopLoss
            val tp1 = signal.takeProfit1
            val tp2 = signal.takeProfit2

            if (signal.direction != SignalDirection.NO_TRADE &&
                signal.technicalScore >= config.minScore &&
                entryPrice != null &&
                stopLoss != null &&
                tp1 != null &&
                tp2 != null
            ) {
                val targetPrice = if (config.targetTp == BacktestConfig.BacktestTarget.TP1) tp1 else tp2
                val isBuy = signal.direction == SignalDirection.BUY
                val slPips = abs(entryPrice - stopLoss) / pip

                if (slPips > 0.5 && equity > 1.0) {
                    val riskAmount = equity * (config.riskPercent.coerceIn(0.05, 100.0) / 100.0)
                    val pipValuePerLot = TimeUtils.pipValuePerStandardLotUsd(config.symbol, entryPrice)
                    val lotsRaw = riskAmount / (slPips * pipValuePerLot)

                    var exitBarIndex = -1
                    var exitPrice: Double = entryPrice
                    var exitReason = "Fin historique"

                    val maxHorizon = min(cleanM15.size - 1, i + 96)
                    for (j in (i + 1)..maxHorizon) {
                        val bar = cleanM15[j]
                        if (isBuy) {
                            // Règle conservatrice : si SL et TP touchés sur la même bougie, SL prime
                            if (bar.low <= stopLoss) {
                                exitBarIndex = j
                                exitPrice = stopLoss
                                exitReason = "Stop Loss"
                                break
                            }
                            if (bar.high >= targetPrice) {
                                exitBarIndex = j
                                exitPrice = targetPrice
                                exitReason = config.targetTp.name
                                break
                            }
                        } else {
                            if (bar.high >= stopLoss) {
                                exitBarIndex = j
                                exitPrice = stopLoss
                                exitReason = "Stop Loss"
                                break
                            }
                            if (bar.low <= targetPrice) {
                                exitBarIndex = j
                                exitPrice = targetPrice
                                exitReason = config.targetTp.name
                                break
                            }
                        }
                    }

                    if (exitBarIndex == -1) {
                        exitBarIndex = maxHorizon
                        exitPrice = cleanM15[exitBarIndex].close
                        exitReason = "Expiration 24h"
                    }

                    val grossPips = if (isBuy) {
                        (exitPrice - entryPrice) / pip
                    } else {
                        (entryPrice - exitPrice) / pip
                    }
                    val netPips = grossPips - totalCostPips
                    val grossCurrency = grossPips * pipValuePerLot * lotsRaw
                    val costCurrency = totalCostPips * pipValuePerLot * lotsRaw
                    val netCurrency = grossCurrency - costCurrency

                    equity += netCurrency
                    trades.add(
                        BacktestTrade(
                            entryTimeIso = currentM15.openTimeIso,
                            exitTimeIso = cleanM15[exitBarIndex].openTimeIso,
                            direction = signal.direction,
                            score = signal.technicalScore,
                            entryPrice = entryPrice,
                            stopLoss = stopLoss,
                            targetPrice = targetPrice,
                            exitPrice = exitPrice,
                            grossPips = grossPips,
                            costPips = totalCostPips,
                            netPips = netPips,
                            netPnLCurrency = netCurrency,
                            equityAfter = equity,
                            isWin = netCurrency > 0.0,
                            exitReason = exitReason,
                            grossPnLCurrency = grossCurrency,
                            costCurrency = costCurrency
                        )
                    )

                    // Advance index past closed trade (single position at a time)
                    i = exitBarIndex + 1
                    continue
                }
            }
            i++
        }

        return buildResultFromTrades(
            config = config,
            testedCandlesCount = cleanM15.size,
            startDateIso = cleanM15[SignalEngine.MIN_CLOSED_CANDLES].openTimeIso,
            endDateIso = cleanM15.last().openTimeIso,
            tradesAscending = trades
        )
    }

    /**
     * Agrège toutes les métriques du backtest à partir d'une source unique de transactions :
     * - Gains bruts = somme des PnL bruts positifs (> 0)
     * - Pertes brutes = valeur absolue de la somme des PnL bruts négatifs (< 0)
     * - Profit Factor = gains bruts / |pertes brutes| (∞ si gains > 0 et 0 perte ; 0.0 si aucun gain)
     * - Coûts estimés = somme des coûts (spread, commissions) sur chaque transaction
     * - Résultat net = gains bruts - pertes brutes - coûts estimés
     * - Taux de réussite = (nombre de gains nets / nombre total de transactions) * 100
     * - Courbe d'équité & drawdown dérivés des mêmes transactions sans dérive
     */
    fun buildResultFromTrades(
        config: BacktestConfig,
        testedCandlesCount: Int,
        startDateIso: String,
        endDateIso: String,
        tradesAscending: List<BacktestTrade>
    ): BacktestResult {
        var equity = config.initialCapital
        var peakEquity = equity
        var maxDrawdownCurrency = 0.0
        var maxDrawdownPercent = 0.0
        val equityCurve = mutableListOf(equity)

        val normalizedTrades = tradesAscending.map { trade ->
            val safeCost = trade.costCurrency.coerceAtLeast(0.0)
            val safeGross = trade.grossPnLCurrency
            val reconciledNet = safeGross - safeCost
            val isWinTrade = reconciledNet > 0.0

            equity += reconciledNet
            equityCurve.add(equity)
            if (equity > peakEquity) peakEquity = equity
            val ddCurrency = peakEquity - equity
            val ddPct = if (peakEquity > 0.0) (ddCurrency / peakEquity) * 100.0 else 0.0
            if (ddCurrency > maxDrawdownCurrency) maxDrawdownCurrency = ddCurrency
            if (ddPct > maxDrawdownPercent) maxDrawdownPercent = ddPct

            trade.copy(
                grossPnLCurrency = safeGross,
                costCurrency = safeCost,
                netPnLCurrency = reconciledNet,
                equityAfter = equity,
                isWin = isWinTrade
            )
        }

        val wins = normalizedTrades.count { it.isWin }
        val losses = normalizedTrades.count { !it.isWin }
        val totalTrades = normalizedTrades.size
        val winRate = if (totalTrades > 0) {
            (wins.toDouble() / totalTrades.toDouble()) * 100.0
        } else {
            0.0
        }

        val grossProfit = normalizedTrades
            .filter { it.grossPnLCurrency > 0.0 }
            .sumOf { it.grossPnLCurrency }
        val grossLoss = normalizedTrades
            .filter { it.grossPnLCurrency < 0.0 }
            .sumOf { abs(it.grossPnLCurrency) }
        val totalCosts = normalizedTrades.sumOf { it.costCurrency }

        val profitFactor = when {
            grossLoss > 0.0 -> grossProfit / grossLoss
            grossProfit > 0.0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }

        val netProfit = grossProfit - grossLoss - totalCosts
        val finalCapital = config.initialCapital + netProfit
        val returnPct = if (config.initialCapital > 0.0) {
            (netProfit / config.initialCapital) * 100.0
        } else {
            0.0
        }

        return BacktestResult(
            config = config,
            testedCandlesCount = testedCandlesCount,
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            totalTrades = totalTrades,
            winningTrades = wins,
            losingTrades = losses,
            winRatePercent = winRate,
            profitFactor = profitFactor,
            maxDrawdownPercent = maxDrawdownPercent,
            maxDrawdownCurrency = maxDrawdownCurrency,
            grossProfitCurrency = grossProfit,
            grossLossCurrency = grossLoss,
            totalEstimatedCostsCurrency = totalCosts,
            netProfitCurrency = netProfit,
            finalCapital = finalCapital,
            returnPercent = returnPct,
            equityCurve = equityCurve,
            trades = normalizedTrades.reversed()
        )
    }

    private fun sanitizeClosedCandles(candles: List<Candle>): List<Candle> {
        return candles
            .filter { c ->
                !c.isOpen &&
                    c.openTimeMs > 0L &&
                    c.open.isFinite() && c.high.isFinite() && c.low.isFinite() && c.close.isFinite() &&
                    c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 &&
                    c.high >= c.low &&
                    c.high >= max(c.open, c.close) &&
                    c.low <= min(c.open, c.close)
            }
            .distinctBy { it.openTimeMs }
            .sortedBy { it.openTimeMs }
    }
}
