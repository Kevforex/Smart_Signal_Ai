package com.example

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.BacktestEngine
import com.example.domain.analysis.SignalEngine
import com.example.domain.analysis.TechnicalIndicators
import com.example.domain.model.BacktestConfig
import com.example.domain.model.BacktestTrade
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.SignalDirection
import com.example.domain.model.Timeframe
import com.example.ui.screens.BacktestScreen
import com.example.ui.theme.SmartSignalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BacktestScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `backtest screen displays 10 trades and 80 percent win rate for 8 wins and 2 losses`() {
        val config = BacktestConfig(initialCapital = 1000.0)
        val sampleTrades = (1..10).map { idx ->
            val isWin = idx <= 8
            BacktestTrade(
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
                netPnLCurrency = if (isWin) 20.0 else -10.0,
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

        val emptyM15 = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, emptyList())
        val emptyH1 = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, emptyList())
        val bundle = MarketAnalysisBundle(
            symbol = "EURUSD",
            quote = null,
            freshnessStatus = FreshnessStatus.FRESH,
            m15AllCandles = emptyList(),
            m15ClosedCandles = emptyList(),
            h1AllCandles = emptyList(),
            h1ClosedCandles = emptyList(),
            m15Snapshot = emptyM15,
            h1Snapshot = emptyH1,
            activeSignal = SignalEngine.evaluate(
                symbol = "EURUSD",
                closedM15 = emptyList(),
                closedH1 = emptyList(),
                freshnessStatus = FreshnessStatus.INSUFFICIENT_DATA,
                m15Snapshot = emptyM15,
                h1Snapshot = emptyH1
            ),
            fetchedAtMs = 1700000000000L
        )

        composeRule.setContent {
            SmartSignalTheme(darkTheme = true) {
                BacktestScreen(
                    bundle = bundle,
                    config = config,
                    result = result,
                    isRunning = false,
                    onUpdateConfig = { _, _, _, _, _, _ -> },
                    onRunBacktest = {}
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag("backtest_profit_factor"))

        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_total_trades")) and hasText("10"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_win_rate")) and hasText("80,0 %"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_wins_count")) and hasText("8 gagnante(s)"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_losses_count")) and hasText("2 perdante(s)"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_profit_factor")) and hasText("8,00"))
            .assertExists()
    }

    @Test
    fun `backtest screen displays 2 trades, 50 percent win rate, and 0,41 profit factor for +4_07 and -10_00`() {
        val config = BacktestConfig(initialCapital = 1000.0)
        val trades = listOf(
            BacktestTrade(
                entryTimeIso = "2026-11-20T10:00:00Z",
                exitTimeIso = "2026-11-20T11:00:00Z",
                direction = SignalDirection.BUY,
                score = 85,
                entryPrice = 1.09000,
                stopLoss = 1.08850,
                targetPrice = 1.09250,
                exitPrice = 1.09250,
                grossPips = 4.07,
                costPips = 0.0,
                netPips = 4.07,
                netPnLCurrency = 4.07,
                equityAfter = 1004.07,
                isWin = true,
                exitReason = "TP1",
                grossPnLCurrency = 4.07,
                costCurrency = 0.0
            ),
            BacktestTrade(
                entryTimeIso = "2026-11-20T12:00:00Z",
                exitTimeIso = "2026-11-20T13:00:00Z",
                direction = SignalDirection.BUY,
                score = 82,
                entryPrice = 1.09100,
                stopLoss = 1.08950,
                targetPrice = 1.09350,
                exitPrice = 1.08950,
                grossPips = -10.00,
                costPips = 0.0,
                netPips = -10.00,
                netPnLCurrency = -10.00,
                equityAfter = 994.07,
                isWin = false,
                exitReason = "Stop Loss",
                grossPnLCurrency = -10.00,
                costCurrency = 0.0
            )
        )
        val result = BacktestEngine.buildResultFromTrades(
            config = config,
            testedCandlesCount = 192,
            startDateIso = "2026-11-18T00:00:00Z",
            endDateIso = "2026-11-20T16:00:00Z",
            tradesAscending = trades
        )

        val emptyM15 = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, emptyList())
        val emptyH1 = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, emptyList())
        val bundle = MarketAnalysisBundle(
            symbol = "EURUSD",
            quote = null,
            freshnessStatus = FreshnessStatus.FRESH,
            m15AllCandles = emptyList(),
            m15ClosedCandles = emptyList(),
            h1AllCandles = emptyList(),
            h1ClosedCandles = emptyList(),
            m15Snapshot = emptyM15,
            h1Snapshot = emptyH1,
            activeSignal = SignalEngine.evaluate(
                symbol = "EURUSD",
                closedM15 = emptyList(),
                closedH1 = emptyList(),
                freshnessStatus = FreshnessStatus.INSUFFICIENT_DATA,
                m15Snapshot = emptyM15,
                h1Snapshot = emptyH1
            ),
            fetchedAtMs = 1700000000000L
        )

        composeRule.setContent {
            SmartSignalTheme(darkTheme = true) {
                BacktestScreen(
                    bundle = bundle,
                    config = config,
                    result = result,
                    isRunning = false,
                    onUpdateConfig = { _, _, _, _, _, _ -> },
                    onRunBacktest = {}
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag("backtest_profit_factor"))

        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_total_trades")) and hasText("2"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_win_rate")) and hasText("50,0 %"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_wins_count")) and hasText("1 gagnante(s)"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_losses_count")) and hasText("1 perdante(s)"))
            .assertExists()
        composeRule.onNode(hasAnyAncestor(hasTestTag("backtest_profit_factor")) and hasText("0,41"))
            .assertExists()
    }
}
