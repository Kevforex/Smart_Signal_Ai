package com.example.domain.model

enum class Timeframe(val apiValue: String, val label: String, val durationMs: Long) {
    M15("15m", "M15", 15 * 60_000L),
    H1("1h", "H1", 60 * 60_000L)
}

enum class SignalDirection(val labelFr: String) {
    BUY("BUY"),
    SELL("SELL"),
    NO_TRADE("NO TRADE")
}

enum class SignalStatus(val labelFr: String) {
    CONFIRMED("SIGNAL CONFIRMÉ"),
    PENDING("SIGNAL EN ATTENTE"),
    NO_TRADE("NO TRADE")
}

enum class MarketStructure(val labelFr: String) {
    BULLISH("Structure haussière (HH / HL)"),
    BEARISH("Structure baissière (LH / LL)"),
    RANGING("Marché sans direction / Range")
}

enum class H1TrendState(val labelFr: String) {
    BULLISH("Haussière confirmée"),
    BEARISH("Baissière confirmée"),
    NEUTRAL("Neutre / Sans direction")
}

enum class FreshnessStatus(val labelFr: String, val isTradable: Boolean) {
    FRESH("Données temps réel valides", true),
    STALE("Données périmées — Signal bloqué", false),
    MARKET_CLOSED("Marché Forex fermé — Analyse sur dernière clôture", false),
    INSUFFICIENT_DATA("Données insuffisantes (< 120 bougies)", false),
    ERROR("Erreur de connexion Biquote", false)
}

enum class SignalOutcome(val labelFr: String) {
    EN_COURS("En cours"),
    TP1_ATTEINT("TP1 atteint"),
    TP2_ATTEINT("TP2 atteint"),
    SL_ATTEINT("Stop Loss atteint"),
    EXPIRE("Expiré (24h)")
}

data class Candle(
    val openTimeIso: String,
    val openTimeMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val tickVolume: Long,
    val isOpen: Boolean
)

data class QuoteSnapshot(
    val symbol: String,
    val description: String,
    val bid: Double,
    val ask: Double,
    val mid: Double,
    val spread: Double,
    val high: Double,
    val low: Double,
    val direction: String,
    val dayDiffPercent: Double,
    val timestampIso: String,
    val timestampMs: Long,
    val source: String,
    val marketState: String,
    val stale: Boolean,
    val quoteAgeSeconds: Long
)

data class SupportResistanceZone(
    val price: Double,
    val type: ZoneType,
    val touches: Int,
    val distancePips: Double
) {
    enum class ZoneType(val labelFr: String) {
        SUPPORT("Support"),
        RESISTANCE("Résistance")
    }
}

data class IndicatorSnapshot(
    val timeframe: Timeframe,
    val lastClosedCandle: Candle?,
    val ema50: Double?,
    val ema200: Double?,
    val ema50Series: List<Double?>,
    val ema200Series: List<Double?>,
    val rsi14: Double?,
    val rsi14Prev: Double?,
    val atr14: Double?,
    val atr14Pips: Double?,
    val structure: MarketStructure,
    val supports: List<SupportResistanceZone>,
    val resistances: List<SupportResistanceZone>,
    val closedCandleCount: Int
)

data class ScoreCriterion(
    val title: String,
    val earnedPoints: Int,
    val maxPoints: Int,
    val detail: String,
    val passed: Boolean
)

data class TradingSignal(
    val symbol: String,
    val direction: SignalDirection,
    val status: SignalStatus,
    val entryPrice: Double?,
    val stopLoss: Double?,
    val takeProfit1: Double?,
    val takeProfit2: Double?,
    val riskRewardTp1: Double?,
    val riskRewardTp2: Double?,
    val technicalScore: Int,
    val scoreLabel: String,
    val criteriaBreakdown: List<ScoreCriterion>,
    val reasons: List<String>,
    val blockingReasons: List<String>,
    val h1TrendState: H1TrendState,
    val signalTimestampIso: String,
    val signalTimestampMs: Long,
    val candleOpenTimeIso: String
)

data class RiskCalculation(
    val accountCapital: Double,
    val riskPercent: Double,
    val riskAmountCurrency: Double,
    val stopLossPips: Double,
    val positionLotsRaw: Double,
    val positionUnits: Double,
    val potentialProfitTp1: Double,
    val potentialProfitTp2: Double,
    val effectiveRiskRewardAfterSpread: Double
)

data class BacktestConfig(
    val symbol: String = "EURUSD",
    val initialCapital: Double = 1000.0,
    val riskPercent: Double = 1.0,
    val minScore: Int = 80,
    val spreadPips: Double = 1.2,
    val commissionPips: Double = 0.4,
    val targetTp: BacktestTarget = BacktestTarget.TP1
) {
    enum class BacktestTarget(val labelFr: String) {
        TP1("Sortie complète sur TP1"),
        TP2("Sortie complète sur TP2")
    }
}

data class BacktestTrade(
    val entryTimeIso: String,
    val exitTimeIso: String,
    val direction: SignalDirection,
    val score: Int,
    val entryPrice: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val exitPrice: Double,
    val grossPips: Double,
    val costPips: Double,
    val netPips: Double,
    val netPnLCurrency: Double,
    val equityAfter: Double,
    val isWin: Boolean,
    val exitReason: String,
    val grossPnLCurrency: Double = netPnLCurrency,
    val costCurrency: Double = 0.0
)

data class BacktestResult(
    val config: BacktestConfig,
    val testedCandlesCount: Int,
    val startDateIso: String,
    val endDateIso: String,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double,
    val profitFactor: Double,
    val maxDrawdownPercent: Double,
    val maxDrawdownCurrency: Double,
    val grossProfitCurrency: Double,
    val grossLossCurrency: Double,
    val totalEstimatedCostsCurrency: Double,
    val netProfitCurrency: Double,
    val finalCapital: Double,
    val returnPercent: Double,
    val equityCurve: List<Double>,
    val trades: List<BacktestTrade>,
    val errorMessage: String? = null
)

data class UserSettings(
    val selectedSymbol: String = "EURUSD",
    val accountCapital: Double = 1000.0,
    val riskPercent: Double = 1.0,
    val spreadPips: Double = 1.2,
    val commissionPips: Double = 0.4,
    val autoRefreshSeconds: Int = 30,
    val darkMode: Boolean = true
)
