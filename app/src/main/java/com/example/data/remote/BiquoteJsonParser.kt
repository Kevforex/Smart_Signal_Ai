package com.example.data.remote

import com.example.domain.analysis.TimeUtils
import com.example.domain.model.Candle
import com.example.domain.model.QuoteSnapshot
import com.squareup.moshi.Moshi
import java.io.IOException

class BiquoteApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

object BiquoteJsonParser {
    private val moshi: Moshi = Moshi.Builder().build()
    private val anyAdapter = moshi.adapter<Any>(Any::class.java)

    /**
     * Parse une reponse OHLC Biquote :
     * - Lit la reponse comme un objet JSON (ex: { "bars": [...] } ou { "data": { "bars": [...] } })
     * - Verifie que bars existe et contient des donnees (sauf si allowEmpty = true pour la pagination)
     * - Lit openTime, open, high, low, close, tickVolume et isOpen
     */
    fun parseOhlcResponse(
        rawJson: String?,
        symbol: String,
        allowEmpty: Boolean = false
    ): List<Candle> {
        if (rawJson.isNullOrBlank()) {
            throw BiquoteApiException("Réponse OHLC vide reçue de l'API Biquote pour $symbol.")
        }

        val root = try {
            anyAdapter.fromJson(rawJson)
        } catch (e: Exception) {
            throw BiquoteApiException("Format JSON OHLC Biquote invalide pour $symbol.", e)
        } ?: throw BiquoteApiException("Réponse JSON OHLC nulle pour $symbol.")

        if (root !is Map<*, *>) {
            throw BiquoteApiException(
                "Format JSON OHLC inattendu pour $symbol : un objet JSON contenant 'bars' était attendu."
            )
        }

        extractApiErrorMessage(root)?.let { errMsg ->
            throw BiquoteApiException("Erreur API Biquote ($symbol) : $errMsg")
        }

        val dataObj = root["data"] as? Map<*, *>
        val barsRaw = root["bars"] ?: dataObj?.get("bars")
        if (barsRaw == null) {
            throw BiquoteApiException("Propriété 'bars' absente dans la réponse OHLC Biquote pour $symbol.")
        }
        if (barsRaw !is List<*>) {
            throw BiquoteApiException("Propriété 'bars' invalide (tableau attendu) dans la réponse OHLC pour $symbol.")
        }
        if (barsRaw.isEmpty()) {
            if (allowEmpty) return emptyList()
            throw BiquoteApiException("Le tableau 'bars' retourné par Biquote est vide pour $symbol.")
        }

        val candles = barsRaw.mapNotNull { item ->
            (item as? Map<*, *>)?.let { mapToCandle(it) }
        }
            .distinctBy { it.openTimeMs }
            .sortedBy { it.openTimeMs }

        if (candles.isEmpty()) {
            throw BiquoteApiException("Aucune bougie OHLC valide exploitable dans 'bars' pour $symbol.")
        }

        return candles
    }

    /**
     * Parse /api/latest?symbols=... de maniere resiliente :
     * - Objet indexe par symbole : { "EURUSD": { "bid": ..., "ask": ... } } (format live Biquote)
     * - Objet enveloppe : { "quotes": [...] } ou { "data": [...] }
     * - Objet unique : { "symbol": "EURUSD", "bid": ... }
     * - Tableau JSON : [ { "symbol": "EURUSD", "bid": ... } ]
     */
    fun parseLatestQuote(rawJson: String?, symbol: String): QuoteSnapshot {
        if (rawJson.isNullOrBlank()) {
            throw BiquoteApiException("Réponse de cotation vide reçue de l'API Biquote.")
        }

        val root = try {
            anyAdapter.fromJson(rawJson)
        } catch (e: Exception) {
            throw BiquoteApiException("Format JSON de cotation Biquote invalide.", e)
        } ?: throw BiquoteApiException("Réponse JSON de cotation nulle.")

        val quoteMap: Map<*, *>? = when (root) {
            is List<*> -> {
                root.filterIsInstance<Map<*, *>>().firstOrNull { map ->
                    (map["symbol"] as? String)?.equals(symbol, ignoreCase = true) == true
                } ?: root.filterIsInstance<Map<*, *>>().firstOrNull()
            }

            is Map<*, *> -> {
                extractApiErrorMessage(root)?.let { errMsg ->
                    throw BiquoteApiException("Erreur API Biquote : $errMsg")
                }

                val directBySymbol = (root[symbol] as? Map<*, *>)
                    ?: (root[symbol.uppercase()] as? Map<*, *>)
                if (directBySymbol != null) {
                    directBySymbol
                } else if (root["bid"] != null && root["symbol"] != null) {
                    root
                } else {
                    val nestedList = (root["quotes"] as? List<*>) ?: (root["data"] as? List<*>)
                    val nestedMap = root["data"] as? Map<*, *>
                    when {
                        nestedList != null -> {
                            nestedList.filterIsInstance<Map<*, *>>().firstOrNull { map ->
                                (map["symbol"] as? String)?.equals(symbol, ignoreCase = true) == true
                            } ?: nestedList.filterIsInstance<Map<*, *>>().firstOrNull()
                        }
                        nestedMap != null -> {
                            (nestedMap[symbol] as? Map<*, *>)
                                ?: (nestedMap[symbol.uppercase()] as? Map<*, *>)
                                ?: nestedMap.takeIf { it["bid"] != null }
                        }
                        else -> null
                    }
                }
            }

            else -> throw BiquoteApiException("Format JSON racine de cotation non supporté.")
        }

        if (quoteMap == null) {
            throw BiquoteApiException("Aucune cotation trouvée pour $symbol dans la réponse Biquote.")
        }

        return mapToQuoteSnapshot(quoteMap, symbol)
            ?: throw BiquoteApiException("Champs bid/symbole manquants ou invalides pour $symbol.")
    }

    fun extractErrorMessageIfAny(rawJson: String?): String? {
        if (rawJson.isNullOrBlank()) return null
        val root = try {
            anyAdapter.fromJson(rawJson)
        } catch (_: Exception) {
            return null
        }
        return (root as? Map<*, *>)?.let { extractApiErrorMessage(it) }
    }

    private fun extractApiErrorMessage(map: Map<*, *>): String? {
        val errorVal = map["error"] as? String
        val messageVal = map["message"] as? String
        return when {
            !errorVal.isNullOrBlank() && !messageVal.isNullOrBlank() -> "$errorVal — $messageVal"
            !errorVal.isNullOrBlank() -> errorVal
            !messageVal.isNullOrBlank() && map["bars"] == null && map["bid"] == null -> messageVal
            else -> null
        }
    }

    private fun mapToCandle(map: Map<*, *>): Candle? {
        val openTime = map["openTime"] as? String ?: return null
        val open = asDouble(map["open"]) ?: return null
        val high = asDouble(map["high"]) ?: return null
        val low = asDouble(map["low"]) ?: return null
        val close = asDouble(map["close"]) ?: return null
        if (!open.isFinite() || !high.isFinite() || !low.isFinite() || !close.isFinite()) return null
        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) return null
        if (high < low || high < maxOf(open, close) || low > minOf(open, close)) return null

        val openMs = TimeUtils.parseIsoUtcToMillis(openTime)
        if (openMs <= 0L) return null
        val tickVolume = asLong(map["tickVolume"]) ?: asLong(map["volume"]) ?: 0L
        val isOpen = asBoolean(map["isOpen"]) ?: false

        return Candle(
            openTimeIso = openTime,
            openTimeMs = openMs,
            open = open,
            high = high,
            low = low,
            close = close,
            tickVolume = tickVolume,
            isOpen = isOpen
        )
    }

    private fun mapToQuoteSnapshot(map: Map<*, *>, fallbackSymbol: String): QuoteSnapshot? {
        val sym = (map["symbol"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackSymbol
        val bid = asDouble(map["bid"])?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val ask = asDouble(map["ask"])?.takeIf { it.isFinite() && it > 0.0 } ?: bid
        val mid = asDouble(map["mid"])?.takeIf { it.isFinite() && it > 0.0 } ?: ((bid + ask) / 2.0)
        val tsIso = (map["timestamp"] as? String) ?: (map["lastQuoteAt"] as? String) ?: ""

        return QuoteSnapshot(
            symbol = sym,
            description = (map["description"] as? String) ?: sym,
            bid = bid,
            ask = ask,
            mid = mid,
            spread = asDouble(map["spread"])?.takeIf { it.isFinite() && it >= 0.0 } ?: (ask - bid).coerceAtLeast(0.0),
            high = asDouble(map["high"])?.takeIf { it.isFinite() && it > 0.0 } ?: mid,
            low = asDouble(map["low"])?.takeIf { it.isFinite() && it > 0.0 } ?: mid,
            direction = (map["direction"] as? String) ?: "FLAT",
            dayDiffPercent = asDouble(map["dayDiffPercent"])?.takeIf { it.isFinite() } ?: 0.0,
            timestampIso = tsIso,
            timestampMs = TimeUtils.parseIsoUtcToMillis(tsIso),
            source = (map["source"] as? String) ?: "Biquote Public API",
            marketState = (map["marketState"] as? String) ?: "unknown",
            stale = asBoolean(map["stale"]) ?: true,
            quoteAgeSeconds = asLong(map["quoteAgeSeconds"]) ?: 9999L
        )
    }

    private fun asDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun asLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun asBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }
}
