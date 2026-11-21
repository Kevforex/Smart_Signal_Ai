package com.example.data.repository

import com.example.data.local.SignalHistoryDao
import com.example.data.local.SignalHistoryEntity
import com.example.data.remote.BiquoteApiException
import com.example.data.remote.BiquoteApiService
import com.example.data.remote.BiquoteJsonParser
import com.example.domain.analysis.SignalEngine
import com.example.domain.analysis.SignalOutcomeEvaluator
import com.example.domain.analysis.TechnicalIndicators
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.Candle
import com.example.domain.model.FreshnessStatus
import com.example.domain.model.IndicatorSnapshot
import com.example.domain.model.QuoteSnapshot
import com.example.domain.model.SignalDirection
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class MarketAnalysisBundle(
    val symbol: String,
    val quote: QuoteSnapshot?,
    val freshnessStatus: FreshnessStatus,
    val m15AllCandles: List<Candle>,
    val m15ClosedCandles: List<Candle>,
    val h1AllCandles: List<Candle>,
    val h1ClosedCandles: List<Candle>,
    val m15Snapshot: IndicatorSnapshot,
    val h1Snapshot: IndicatorSnapshot,
    val activeSignal: TradingSignal,
    val fetchedAtMs: Long,
    val errorMessage: String? = null
)

class MarketRepository(
    private val apiService: BiquoteApiService,
    private val signalHistoryDao: SignalHistoryDao
) {
    companion object {
        const val M15_LIMIT = 192
        const val H1_LIMIT = 168
        private const val TARGET_EMA200_CANDLES = 205
    }

    val signalHistoryFlow: Flow<List<SignalHistoryEntity>> = signalHistoryDao.observeAllSignals()

    suspend fun fetchAndAnalyze(symbol: String): MarketAnalysisBundle = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        try {
            val (quoteResp, m15Resp, h1Resp) = coroutineScope {
                val qDeferred = async { apiService.getLatestQuotes(listOf(symbol)) }
                val m15Deferred = async {
                    apiService.getOhlc(
                        symbol = symbol,
                        interval = Timeframe.M15.apiValue,
                        limit = M15_LIMIT
                    )
                }
                val h1Deferred = async {
                    apiService.getOhlc(
                        symbol = symbol,
                        interval = Timeframe.H1.apiValue,
                        limit = H1_LIMIT
                    )
                }
                Triple(qDeferred.await(), m15Deferred.await(), h1Deferred.await())
            }

            val quoteBody = readSuccessBody(quoteResp, "cotation $symbol")
            val m15Body = readSuccessBody(m15Resp, "OHLC M15 $symbol")
            val h1Body = readSuccessBody(h1Resp, "OHLC H1 $symbol")

            val quote = BiquoteJsonParser.parseLatestQuote(quoteBody, symbol)
            val m15Initial = BiquoteJsonParser.parseOhlcResponse(m15Body, symbol, allowEmpty = false)
            val h1Initial = BiquoteJsonParser.parseOhlcResponse(h1Body, symbol, allowEmpty = false)

            // Pagination complementaire via from/to si < 205 bougies cloturees pour EMA 200
            val (m15All, h1All) = coroutineScope {
                val m15Paged = async {
                    fetchEarlierPageIfNeeded(
                        symbol = symbol,
                        timeframe = Timeframe.M15,
                        limit = M15_LIMIT,
                        initial = m15Initial
                    )
                }
                val h1Paged = async {
                    fetchEarlierPageIfNeeded(
                        symbol = symbol,
                        timeframe = Timeframe.H1,
                        limit = H1_LIMIT,
                        initial = h1Initial
                    )
                }
                m15Paged.await() to h1Paged.await()
            }

            // Regle stricte : ecarter toutes les bougies dont isOpen est true pour l'analyse et garantir l'ordre chronologique
            val m15Closed = m15All
                .filter { !it.isOpen }
                .distinctBy { it.openTimeMs }
                .sortedBy { it.openTimeMs }
            val h1Closed = h1All
                .filter { !it.isOpen }
                .distinctBy { it.openTimeMs }
                .sortedBy { it.openTimeMs }

            val freshness = evaluateFreshness(quote, m15Closed, h1Closed, nowMs)

            val m15Snap = TechnicalIndicators.buildSnapshot(symbol, Timeframe.M15, m15Closed)
            val h1Snap = TechnicalIndicators.buildSnapshot(symbol, Timeframe.H1, h1Closed)

            val signal = SignalEngine.evaluate(
                symbol = symbol,
                closedM15 = m15Closed,
                closedH1 = h1Closed,
                freshnessStatus = freshness,
                m15Snapshot = m15Snap,
                h1Snapshot = h1Snap
            )

            // Enregistrement immuable des signaux confirmes / en attente
            if (signal.direction != SignalDirection.NO_TRADE &&
                signal.entryPrice != null &&
                signal.stopLoss != null &&
                signal.takeProfit1 != null &&
                signal.takeProfit2 != null &&
                signal.candleOpenTimeIso.isNotBlank()
            ) {
                val entity = SignalHistoryEntity(
                    symbol = signal.symbol,
                    timeframe = Timeframe.M15.label,
                    candleOpenTimeIso = signal.candleOpenTimeIso,
                    direction = signal.direction.name,
                    status = signal.status.name,
                    entryPrice = signal.entryPrice,
                    stopLoss = signal.stopLoss,
                    takeProfit1 = signal.takeProfit1,
                    takeProfit2 = signal.takeProfit2,
                    riskRewardTp1 = signal.riskRewardTp1 ?: 0.0,
                    riskRewardTp2 = signal.riskRewardTp2 ?: 0.0,
                    technicalScore = signal.technicalScore,
                    scoreLabel = signal.scoreLabel,
                    reasonsJoined = signal.reasons.joinToString("\n"),
                    h1TrendState = signal.h1TrendState.labelFr,
                    signalTimestampIso = signal.signalTimestampIso,
                    signalTimestampMs = signal.signalTimestampMs
                )
                signalHistoryDao.insertIfAbsent(entity)
            }

            val openSignals = signalHistoryDao.getOpenOutcomeSignals(symbol)
            for (openEntity in openSignals) {
                val update = SignalOutcomeEvaluator.evaluateOutcome(openEntity, m15Closed)
                if (update != null) {
                    signalHistoryDao.resolveOutcomeIfOpen(
                        id = openEntity.id,
                        outcome = update.outcome.name,
                        pips = update.pips,
                        evaluatedAtIso = update.evaluatedAtIso
                    )
                }
            }

            MarketAnalysisBundle(
                symbol = symbol,
                quote = quote,
                freshnessStatus = freshness,
                m15AllCandles = m15All,
                m15ClosedCandles = m15Closed,
                h1AllCandles = h1All,
                h1ClosedCandles = h1Closed,
                m15Snapshot = m15Snap,
                h1Snapshot = h1Snap,
                activeSignal = signal,
                fetchedAtMs = nowMs,
                errorMessage = null
            )
        } catch (e: Exception) {
            val friendlyError = mapNetworkError(e)
            val emptyM15 = TechnicalIndicators.buildSnapshot(symbol, Timeframe.M15, emptyList())
            val emptyH1 = TechnicalIndicators.buildSnapshot(symbol, Timeframe.H1, emptyList())
            val errorSignal = SignalEngine.evaluate(
                symbol = symbol,
                closedM15 = emptyList(),
                closedH1 = emptyList(),
                freshnessStatus = FreshnessStatus.ERROR,
                m15Snapshot = emptyM15,
                h1Snapshot = emptyH1
            )
            MarketAnalysisBundle(
                symbol = symbol,
                quote = null,
                freshnessStatus = FreshnessStatus.ERROR,
                m15AllCandles = emptyList(),
                m15ClosedCandles = emptyList(),
                h1AllCandles = emptyList(),
                h1ClosedCandles = emptyH1Candles(),
                m15Snapshot = emptyM15,
                h1Snapshot = emptyH1,
                activeSignal = errorSignal,
                fetchedAtMs = nowMs,
                errorMessage = friendlyError
            )
        }
    }

    private fun emptyH1Candles(): List<Candle> = emptyList()

    private suspend fun fetchEarlierPageIfNeeded(
        symbol: String,
        timeframe: Timeframe,
        limit: Int,
        initial: List<Candle>
    ): List<Candle> {
        var accumulated = initial
            .distinctBy { it.openTimeMs }
            .sortedBy { it.openTimeMs }

        var attempts = 0
        while (attempts < 3 && accumulated.isNotEmpty() && accumulated.count { !it.isOpen } < TARGET_EMA200_CANDLES) {
            attempts++
            val oldestMs = accumulated.first().openTimeMs
            val toMs = oldestMs - timeframe.durationMs
            val fromMs = toMs - (limit.toLong() * timeframe.durationMs)
            val olderBars = try {
                val resp = apiService.getOhlc(
                    symbol = symbol,
                    interval = timeframe.apiValue,
                    limit = limit,
                    from = TimeUtils.formatUtcIso(fromMs),
                    to = TimeUtils.formatUtcIso(toMs)
                )
                if (!resp.isSuccessful) break
                val body = resp.body()?.string() ?: break
                BiquoteJsonParser.parseOhlcResponse(body, symbol, allowEmpty = true)
            } catch (_: Exception) {
                break
            }

            if (olderBars.isEmpty()) break
            val merged = (olderBars + accumulated)
                .distinctBy { it.openTimeMs }
                .sortedBy { it.openTimeMs }
            // Stop if API did not return any older candle (prevents redundant loops)
            if (merged.size <= accumulated.size || merged.first().openTimeMs >= oldestMs) break
            accumulated = merged
        }
        return accumulated
    }

    private fun readSuccessBody(response: Response<ResponseBody>, contextLabel: String): String {
        if (!response.isSuccessful) {
            val errRaw = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsedMsg = BiquoteJsonParser.extractErrorMessageIfAny(errRaw)
            val detail = when (response.code()) {
                429 -> "Limite de requêtes Biquote atteinte (HTTP 429). Patientez quelques secondes."
                404 -> "Symbole ou données introuvables sur Biquote (HTTP 404) pour $contextLabel."
                else -> "Erreur serveur Biquote HTTP ${response.code()} ($contextLabel)${if (parsedMsg != null) " : $parsedMsg" else "."}"
            }
            throw BiquoteApiException(detail)
        }

        val body = response.body()?.string()
        if (body.isNullOrBlank()) {
            throw BiquoteApiException("Réponse HTTP vide reçue de Biquote ($contextLabel).")
        }
        return body
    }

    private fun evaluateFreshness(
        quote: QuoteSnapshot?,
        m15Closed: List<Candle>,
        h1Closed: List<Candle>,
        nowMs: Long
    ): FreshnessStatus {
        if (quote == null) return FreshnessStatus.STALE
        if (quote.marketState.equals("closed", ignoreCase = true)) {
            return FreshnessStatus.MARKET_CLOSED
        }
        if (quote.stale || quote.quoteAgeSeconds > 300 ||
            (quote.timestampMs > 0L && (nowMs - quote.timestampMs) > 600_000L)
        ) {
            return FreshnessStatus.STALE
        }
        if (m15Closed.size < SignalEngine.MIN_CLOSED_CANDLES ||
            h1Closed.size < SignalEngine.MIN_CLOSED_CANDLES
        ) {
            return FreshnessStatus.INSUFFICIENT_DATA
        }
        val lastClosedCloseTime = m15Closed.last().openTimeMs + Timeframe.M15.durationMs
        val ageSinceLastClosedMin = (nowMs - lastClosedCloseTime) / 60_000L
        if (ageSinceLastClosedMin > 45L) {
            return FreshnessStatus.STALE
        }
        return FreshnessStatus.FRESH
    }

    private fun mapNetworkError(e: Exception): String {
        return when (e) {
            is BiquoteApiException -> e.message ?: "Erreur lors de la lecture des données Biquote."
            is UnknownHostException -> "API Biquote inaccessible : impossible de joindre biquote.io. Vérifiez votre connexion Internet."
            is SocketTimeoutException -> "Délai de réponse dépassé lors de la connexion à Biquote."
            is HttpException -> when (e.code()) {
                429 -> "Limite de requêtes Biquote atteinte (HTTP 429). Patientez quelques secondes."
                404 -> "Symbole introuvable ou aucune donnée récente disponible sur Biquote (HTTP 404)."
                else -> "Erreur serveur Biquote (HTTP ${e.code()})."
            }
            is IOException -> "Erreur réseau Biquote : ${e.localizedMessage ?: "connexion interrompue"}."
            else -> "Erreur inattendue : ${e.localizedMessage ?: "erreur interne"}."
        }
    }
}
