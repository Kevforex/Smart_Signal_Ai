package com.example

import com.example.domain.analysis.BacktestEngine
import com.example.domain.analysis.RiskCalculator
import com.example.domain.analysis.SignalEngine
import com.example.domain.analysis.TechnicalIndicators
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.BacktestConfig
import com.example.domain.model.BacktestTrade
import com.example.domain.model.Candle
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.H1TrendState
import com.example.domain.model.SignalDirection
import com.example.domain.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestAuditTest {

    private fun makeTrade(
        grossPnL: Double,
        cost: Double = 0.0,
        direction: SignalDirection = SignalDirection.BUY
    ): BacktestTrade {
        val netPnL = grossPnL - cost
        return BacktestTrade(
            entryTimeIso = "2026-11-20T10:00:00Z",
            exitTimeIso = "2026-11-20T11:00:00Z",
            direction = direction,
            score = 85,
            entryPrice = 1.09000,
            stopLoss = 1.08850,
            targetPrice = 1.09250,
            exitPrice = if (grossPnL >= 0) 1.09250 else 1.08850,
            grossPips = grossPnL,
            costPips = cost,
            netPips = netPnL,
            netPnLCurrency = netPnL,
            equityAfter = 1000.0,
            isWin = netPnL > 0.0,
            exitReason = if (grossPnL >= 0) "TP1" else "Stop Loss",
            grossPnLCurrency = grossPnL,
            costCurrency = cost
        )
    }

    @Test
    fun `mandatory case 1 win +4_07 and 1 loss -10_00 without costs and with costs`() {
        val config = BacktestConfig(initialCapital = 1000.0)

        // 1) Sans coûts : +4.07 et -10.00 -> avant coûts = -5.93, PF = 0.407
        val tradesNoCost = listOf(
            makeTrade(grossPnL = 4.07, cost = 0.0),
            makeTrade(grossPnL = -10.00, cost = 0.0)
        )
        val resNoCost = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = tradesNoCost
        )

        assertEquals(2, resNoCost.totalTrades)
        assertEquals(1, resNoCost.winningTrades)
        assertEquals(1, resNoCost.losingTrades)
        assertEquals(50.0, resNoCost.winRatePercent, 1e-6)
        assertEquals(4.07, resNoCost.grossProfitCurrency, 1e-6)
        assertEquals(10.00, resNoCost.grossLossCurrency, 1e-6)
        assertEquals(0.407, resNoCost.profitFactor, 1e-6)
        assertEquals(-5.93, resNoCost.grossProfitCurrency - resNoCost.grossLossCurrency, 1e-6)
        assertEquals(0.0, resNoCost.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(-5.93, resNoCost.netProfitCurrency, 1e-6)
        assertEquals(994.07, resNoCost.finalCapital, 1e-6)
        assertEquals(listOf(1000.0, 1004.07, 994.07), resNoCost.equityCurve)
        assertEquals(resNoCost.finalCapital, resNoCost.equityCurve.last(), 1e-6)
        assertEquals(10.00, resNoCost.maxDrawdownCurrency, 1e-6)

        // 2) Avec coûts (0.55 par transaction = 1.10 total) :
        //    Avant coûts = -5.93, Coûts = 1.10, Résultat net = -7.03, PF brut = 0.407
        val tradesWithCost = listOf(
            makeTrade(grossPnL = 4.07, cost = 0.55),
            makeTrade(grossPnL = -10.00, cost = 0.55)
        )
        val resWithCost = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = tradesWithCost
        )

        assertEquals(2, resWithCost.totalTrades)
        assertEquals(1, resWithCost.winningTrades)
        assertEquals(1, resWithCost.losingTrades)
        assertEquals(50.0, resWithCost.winRatePercent, 1e-6)
        assertEquals(0.407, resWithCost.profitFactor, 1e-6)
        assertEquals(4.07, resWithCost.grossProfitCurrency, 1e-6)
        assertEquals(10.00, resWithCost.grossLossCurrency, 1e-6)
        assertEquals(1.10, resWithCost.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(-7.03, resWithCost.netProfitCurrency, 1e-6)
        assertEquals(992.97, resWithCost.finalCapital, 1e-6)
        assertEquals(resWithCost.finalCapital, resWithCost.equityCurve.last(), 1e-6)
        assertEquals(
            resWithCost.grossProfitCurrency - resWithCost.grossLossCurrency - resWithCost.totalEstimatedCostsCurrency,
            resWithCost.netProfitCurrency,
            1e-6
        )
    }

    @Test
    fun `zero trades yields zero metrics and single-point equity curve`() {
        val config = BacktestConfig(initialCapital = 1500.0)
        val res = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = emptyList()
        )

        assertEquals(0, res.totalTrades)
        assertEquals(0, res.winningTrades)
        assertEquals(0, res.losingTrades)
        assertEquals(0.0, res.winRatePercent, 1e-6)
        assertEquals(0.0, res.profitFactor, 1e-6)
        assertEquals(0.0, res.grossProfitCurrency, 1e-6)
        assertEquals(0.0, res.grossLossCurrency, 1e-6)
        assertEquals(0.0, res.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(0.0, res.netProfitCurrency, 1e-6)
        assertEquals(0.0, res.maxDrawdownCurrency, 1e-6)
        assertEquals(1500.0, res.finalCapital, 1e-6)
        assertEquals(listOf(1500.0), res.equityCurve)
    }

    @Test
    fun `all winning trades yield 100 percent win rate, infinite profit factor, and exact costs deducted`() {
        val config = BacktestConfig(initialCapital = 1000.0)
        val trades = listOf(
            makeTrade(grossPnL = 15.0, cost = 1.2),
            makeTrade(grossPnL = 22.5, cost = 1.3),
            makeTrade(grossPnL = 12.5, cost = 1.0)
        )
        val res = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = trades
        )

        assertEquals(3, res.totalTrades)
        assertEquals(3, res.winningTrades)
        assertEquals(0, res.losingTrades)
        assertEquals(100.0, res.winRatePercent, 1e-6)
        assertTrue(res.profitFactor.isInfinite())
        assertEquals(50.0, res.grossProfitCurrency, 1e-6)
        assertEquals(0.0, res.grossLossCurrency, 1e-6)
        assertEquals(3.5, res.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(46.5, res.netProfitCurrency, 1e-6)
        assertEquals(1046.5, res.finalCapital, 1e-6)
        assertEquals(0.0, res.maxDrawdownCurrency, 1e-6)
        assertEquals(res.finalCapital, res.equityCurve.last(), 1e-6)
    }

    @Test
    fun `all losing trades yield 0 percent win rate, 0 profit factor, and accurate drawdown`() {
        val config = BacktestConfig(initialCapital = 1000.0)
        val trades = listOf(
            makeTrade(grossPnL = -10.0, cost = 1.0),
            makeTrade(grossPnL = -15.0, cost = 1.5)
        )
        val res = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = trades
        )

        assertEquals(2, res.totalTrades)
        assertEquals(0, res.winningTrades)
        assertEquals(2, res.losingTrades)
        assertEquals(0.0, res.winRatePercent, 1e-6)
        assertEquals(0.0, res.profitFactor, 1e-6)
        assertEquals(0.0, res.grossProfitCurrency, 1e-6)
        assertEquals(25.0, res.grossLossCurrency, 1e-6)
        assertEquals(2.5, res.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(-27.5, res.netProfitCurrency, 1e-6)
        assertEquals(972.5, res.finalCapital, 1e-6)
        assertEquals(27.5, res.maxDrawdownCurrency, 1e-6)
        assertEquals(2.75, res.maxDrawdownPercent, 1e-6)
        assertEquals(res.finalCapital, res.equityCurve.last(), 1e-6)
    }

    @Test
    fun `mixed trades with positive net result reconcile gross, costs, net, drawdown, and equity curve`() {
        val config = BacktestConfig(initialCapital = 2000.0)
        val trades = listOf(
            makeTrade(grossPnL = 40.0, cost = 2.0),   // eq = 2038, peak = 2038
            makeTrade(grossPnL = -15.0, cost = 2.0),  // eq = 2021, dd = 17
            makeTrade(grossPnL = -10.0, cost = 2.0),  // eq = 2009, dd = 29
            makeTrade(grossPnL = 35.0, cost = 2.0)    // eq = 2042, peak = 2042
        )
        val res = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 220,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = trades
        )

        assertEquals(4, res.totalTrades)
        assertEquals(2, res.winningTrades)
        assertEquals(2, res.losingTrades)
        assertEquals(50.0, res.winRatePercent, 1e-6)
        assertEquals(75.0, res.grossProfitCurrency, 1e-6)
        assertEquals(25.0, res.grossLossCurrency, 1e-6)
        assertEquals(3.0, res.profitFactor, 1e-6) // 75 / 25
        assertEquals(8.0, res.totalEstimatedCostsCurrency, 1e-6)
        assertEquals(42.0, res.netProfitCurrency, 1e-6) // 75 - 25 - 8
        assertEquals(2042.0, res.finalCapital, 1e-6)
        assertEquals(2.1, res.returnPercent, 1e-6)
        assertEquals(29.0, res.maxDrawdownCurrency, 1e-6)
        assertEquals((29.0 / 2038.0) * 100.0, res.maxDrawdownPercent, 1e-6)
        assertEquals(listOf(2000.0, 2038.0, 2021.0, 2009.0, 2042.0), res.equityCurve)
        assertEquals(res.finalCapital, res.equityCurve.last(), 1e-6)
        // Trades list is returned newest-first, check equityAfter matches forward curve
        assertEquals(2042.0, res.trades.first().equityAfter, 1e-6)
        assertEquals(2038.0, res.trades.last().equityAfter, 1e-6)
    }

    @Test
    fun `contradictory H1 bullish and M15 bearish forces NO TRADE`() {
        val start = 1700000000000L
        val h1Bull = buildTrending(170, 1.0500, 0.00030, 60 * 60_000L, start)
        val m15Bear = buildTrending(200, 1.1200, -0.00018, 15 * 60_000L, start)

        val h1Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, h1Bull)
        val m15Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, m15Bear)

        assertEquals(H1TrendState.BULLISH, SignalEngine.inferH1Trend(h1Bull, h1Snap))

        val sig = SignalEngine.evaluate(
            symbol = "EURUSD",
            closedM15 = m15Bear,
            closedH1 = h1Bull,
            freshnessStatus = FreshnessStatus.FRESH,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap
        )

        assertEquals(SignalDirection.NO_TRADE, sig.direction)
        assertTrue(sig.blockingReasons.isNotEmpty())
    }

    @Test
    fun `flat RSI series yields neutral 50 instead of 100`() {
        val closes = List(30) { 1.09500 }
        val rsi = TechnicalIndicators.calculateRsiSeries(closes, 14)
        assertEquals(50.0, rsi.last()!!, 1e-6)
    }

    @Test
    fun `risk calculator validates USDJPY pip value and handles invalid SL safely`() {
        val jpyCalc = RiskCalculator.calculate(
            symbol = "USDJPY",
            accountCapital = 1000.0,
            riskPercent = 1.0,
            entryPrice = 150.000,
            stopLossPrice = 149.800, // 20 pips
            takeProfit1Price = 150.340, // 34 pips
            takeProfit2Price = 150.560, // 56 pips
            spreadPips = 1.5
        )
        // pip value = 1000 / 150 = 6.6667 USD/pip; risk = $10; 20 pips -> lots = 10 / (20 * 6.6667) = 0.075
        assertEquals(20.0, jpyCalc.stopLossPips, 1e-6)
        assertEquals(0.075, jpyCalc.positionLotsRaw, 1e-4)
        assertEquals((34.0 - 1.5) / (20.0 + 1.5), jpyCalc.effectiveRiskRewardAfterSpread, 1e-4)

        val zeroSlCalc = RiskCalculator.calculate(
            symbol = "EURUSD",
            accountCapital = 1000.0,
            riskPercent = 1.0,
            entryPrice = 1.09000,
            stopLossPrice = 1.09000,
            takeProfit1Price = 1.09200,
            takeProfit2Price = 1.09400,
            spreadPips = 1.0
        )
        assertEquals(0.0, zeroSlCalc.stopLossPips, 1e-6)
        assertEquals(0.0, zeroSlCalc.positionLotsRaw, 1e-6)
        assertEquals(0.0, zeroSlCalc.effectiveRiskRewardAfterSpread, 1e-6)
    }

    @Test
    fun `backtest run ignores out-of-order, open, and duplicate candles cleanly`() {
        val start = 1700000000000L
        val m15 = buildTrending(192, 1.0700, 0.00012, 15 * 60_000L, start)
        val h1 = buildTrending(168, 1.0500, 0.00025, 60 * 60_000L, start)
        // Reverse order + duplicate + open candle
        val noisyM15 = m15.reversed() + m15.first() + m15.last().copy(isOpen = true)
        val noisyH1 = h1.reversed() + h1.last().copy(isOpen = true)

        val cleanRes = BacktestEngine.run(BacktestConfig(minScore = 70), m15, h1)
        val noisyRes = BacktestEngine.run(BacktestConfig(minScore = 70), noisyM15, noisyH1)

        assertEquals(cleanRes.testedCandlesCount, noisyRes.testedCandlesCount)
        assertEquals(cleanRes.totalTrades, noisyRes.totalTrades)
        assertEquals(cleanRes.netProfitCurrency, noisyRes.netProfitCurrency, 1e-6)
        assertEquals(cleanRes.profitFactor, noisyRes.profitFactor, 1e-6)
        assertEquals(cleanRes.finalCapital, noisyRes.equityCurve.last(), 1e-6)
    }

    private fun buildTrending(
        count: Int,
        startPrice: Double,
        stepPerBar: Double,
        intervalMs: Long,
        startMs: Long
    ): List<Candle> {
        val list = mutableListOf<Candle>()
        var price = startPrice
        for (i in 0 until count) {
            val openMs = startMs + i * intervalMs
            val wave = if (i % 7 == 0) -stepPerBar * 0.55 else stepPerBar
            val open = price
            val close = price + wave
            val high = maxOf(open, close) + 0.00045
            val low = minOf(open, close) - 0.00035
            list.add(
                Candle(
                    openTimeIso = TimeUtils.formatUtcIso(openMs),
                    openTimeMs = openMs,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    tickVolume = 300L,
                    isOpen = false
                )
            )
            price = close
        }
        return list
    }
}
