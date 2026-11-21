package com.example

import com.example.domain.analysis.BacktestEngine
import com.example.domain.analysis.RiskCalculator
import com.example.domain.analysis.SignalEngine
import com.example.domain.analysis.TechnicalIndicators
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.BacktestConfig
import com.example.domain.model.Candle
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.SignalDirection
import com.example.domain.model.SignalStatus
import com.example.domain.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyEngineTest {

    private fun generateTrendingCandles(
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
            val wave = if (i % 7 == 0) -stepPerBar * 0.6 else stepPerBar
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
                    tickVolume = 250L,
                    isOpen = false
                )
            )
            price = close
        }
        return list
    }

    @Test
    fun `stale data forces NO TRADE even in strong trend`() {
        val start = 1700000000000L
        val m15 = generateTrendingCandles(260, 1.0700, 0.00012, 15 * 60_000L, start)
        val h1 = generateTrendingCandles(260, 1.0500, 0.00025, 60 * 60_000L, start)

        val m15Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, m15)
        val h1Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, h1)

        val signal = SignalEngine.evaluate(
            symbol = "EURUSD",
            closedM15 = m15,
            closedH1 = h1,
            freshnessStatus = FreshnessStatus.STALE,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap
        )

        assertEquals(SignalDirection.NO_TRADE, signal.direction)
        assertEquals(SignalStatus.NO_TRADE, signal.status)
    }

    @Test
    fun `insufficient candles forces NO TRADE`() {
        val start = 1700000000000L
        val m15 = generateTrendingCandles(80, 1.0700, 0.00012, 15 * 60_000L, start)
        val h1 = generateTrendingCandles(80, 1.0500, 0.00025, 60 * 60_000L, start)

        val m15Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, m15)
        val h1Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, h1)

        val signal = SignalEngine.evaluate(
            symbol = "EURUSD",
            closedM15 = m15,
            closedH1 = h1,
            freshnessStatus = FreshnessStatus.FRESH,
            m15Snapshot = m15Snap,
            h1Snapshot = h1Snap
        )

        assertEquals(SignalDirection.NO_TRADE, signal.direction)
    }

    @Test
    fun `indicators EMA50 EMA200 RSI14 ATR14 compute cleanly on 192 M15 and 168 H1 candles`() {
        val start = 1700000000000L
        val m15 = generateTrendingCandles(192, 1.0700, 0.00012, 15 * 60_000L, start)
        val h1 = generateTrendingCandles(168, 1.0500, 0.00025, 60 * 60_000L, start)

        val m15Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, m15)
        val h1Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, h1)

        assertTrue(m15Snap.ema50 != null && m15Snap.ema50 > 1.0)
        assertTrue(m15Snap.ema200 != null && m15Snap.ema200 > 1.0)
        assertTrue(m15Snap.rsi14 != null && m15Snap.rsi14 in 0.0..100.0)
        assertTrue(m15Snap.atr14 != null && m15Snap.atr14 > 0.0)
        assertTrue(h1Snap.ema50 != null && h1Snap.ema200 != null)
        assertTrue(h1Snap.rsi14 != null && h1Snap.atr14 != null)
    }

    @Test
    fun `risk calculator imposes no minimum lot`() {
        val calc = RiskCalculator.calculate(
            symbol = "EURUSD",
            accountCapital = 100.0,
            riskPercent = 0.5,
            entryPrice = 1.08500,
            stopLossPrice = 1.08250,
            takeProfit1Price = 1.08925,
            takeProfit2Price = 1.09200,
            spreadPips = 1.0
        )
        // 0.5% of 100 = 0.50; SL = 25 pips; 0.50 / (25 * 10) = 0.002 lots (< 0.01 standard minimum)
        assertEquals(0.002, calc.positionLotsRaw, 0.0001)
        assertEquals(200.0, calc.positionUnits, 1.0)
    }

    @Test
    fun `backtest executes without inventing trades when insufficient`() {
        val start = 1700000000000L
        val m15 = generateTrendingCandles(100, 1.0700, 0.0001, 15 * 60_000L, start)
        val h1 = generateTrendingCandles(100, 1.0500, 0.0002, 60 * 60_000L, start)
        val res = BacktestEngine.run(BacktestConfig(), m15, h1)
        assertTrue(res.errorMessage != null)
        assertEquals(0, res.totalTrades)
    }

    @Test
    fun `backtest with 8 wins and 2 losses yields 10 total trades and 80 percent win rate`() {
        val config = BacktestConfig(initialCapital = 1000.0)
        val sampleTrades = (1..10).map { idx ->
            val isWin = idx <= 8
            val netPnL = if (isWin) 20.0 else -10.0
            com.example.domain.model.BacktestTrade(
                entryTimeIso = "2026-11-20T10:00:00Z",
                exitTimeIso = "2026-11-20T11:00:00Z",
                direction = SignalDirection.BUY,
                score = 85,
                entryPrice = 1.09000,
                stopLoss = 1.08850,
                targetPrice = 1.09250,
                exitPrice = if (isWin) 1.09250 else 1.08850,
                grossPips = if (isWin) 25.0 else -15.0,
                costPips = 1.6,
                netPips = if (isWin) 23.4 else -16.6,
                netPnLCurrency = netPnL,
                equityAfter = 1000.0,
                isWin = isWin,
                exitReason = if (isWin) "TP1" else "Stop Loss"
            )
        }

        val result = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = sampleTrades
        )

        assertEquals(10, result.totalTrades)
        assertEquals(8, result.winningTrades)
        assertEquals(2, result.losingTrades)
        assertEquals(80.0, result.winRatePercent, 0.0001)
        // 8 * 20 = 160 gains nets, 2 * 10 = 20 pertes nettes -> PF = 160 / 20 = 8.0
        assertEquals(8.0, result.profitFactor, 0.0001)
        assertEquals(1140.0, result.finalCapital, 0.0001)
    }

    @Test
    fun `backtest run maintains totalTrades equal to winning plus losing trades`() {
        val start = 1700000000000L
        val m15 = generateTrendingCandles(192, 1.0700, 0.00012, 15 * 60_000L, start)
        val h1 = generateTrendingCandles(168, 1.0500, 0.00025, 60 * 60_000L, start)
        val res = BacktestEngine.run(BacktestConfig(minScore = 70), m15, h1)
        assertEquals(null, res.errorMessage)
        assertEquals(res.winningTrades + res.losingTrades, res.totalTrades)
        val expectedWinRate = if (res.totalTrades > 0) {
            (res.winningTrades.toDouble() / res.totalTrades.toDouble()) * 100.0
        } else {
            0.0
        }
        assertEquals(expectedWinRate, res.winRatePercent, 0.0001)
    }
}
