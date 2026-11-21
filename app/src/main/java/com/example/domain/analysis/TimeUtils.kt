package com.example.domain.analysis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeUtils {
    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun parseIsoUtcToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val trimmed = iso.trim()
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return 0L
    }

    fun formatLocalDisplay(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMs))
    }

    fun formatShortTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMs))
    }

    fun formatUtcIso(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    fun formatPrice(symbol: String, price: Double?): String {
        if (price == null) return "—"
        val decimals = when {
            symbol.contains("JPY", ignoreCase = true) -> 3
            symbol.contains("XAU", ignoreCase = true) -> 2
            else -> 5
        }
        return String.format(Locale.US, "%.${decimals}f", price)
    }

    fun pipSize(symbol: String): Double {
        return when {
            symbol.contains("JPY", ignoreCase = true) -> 0.01
            symbol.contains("XAU", ignoreCase = true) -> 0.10
            else -> 0.0001
        }
    }

    fun pipValuePerStandardLotUsd(symbol: String, currentPrice: Double): Double {
        val validPrice = if (currentPrice.isFinite() && currentPrice > 0.0) currentPrice else 0.0
        return when {
            symbol.equals("EURUSD", ignoreCase = true) ||
                symbol.equals("GBPUSD", ignoreCase = true) ||
                symbol.equals("AUDUSD", ignoreCase = true) ||
                symbol.equals("NZDUSD", ignoreCase = true) -> 10.0
            symbol.contains("JPY", ignoreCase = true) && validPrice > 0.0 -> (1000.0 / validPrice)
            symbol.startsWith("USD", ignoreCase = true) && validPrice > 0.0 -> (10.0 / validPrice)
            symbol.contains("XAU", ignoreCase = true) -> 10.0
            else -> 10.0
        }
    }
}
