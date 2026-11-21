package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.H1TrendState
import com.example.domain.model.MarketStructure
import com.example.ui.components.PriceMetricTile
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoMediumStyle
import com.example.ui.theme.MonoSmallStyle
import java.util.Locale

@Composable
fun TechnicalAnalysisScreen(
    bundle: MarketAnalysisBundle,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val m15 = bundle.m15Snapshot
    val h1 = bundle.h1Snapshot
    val signal = bundle.activeSignal

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Analyse technique ${bundle.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Calculée exclusivement sur les bougies clôturées (M15 : ${m15.closedCandleCount}, H1 : ${h1.closedCandleCount}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1 & 2. EMA 50 & EMA 200 (M15 + H1)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1 & 2. Moyennes Mobiles EMA 50 & EMA 200",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceMetricTile(
                            label = "EMA 50 (M15)",
                            value = TimeUtils.formatPrice(bundle.symbol, m15.ema50),
                            accent = tradingColors.ema50,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "EMA 200 (M15)",
                            value = TimeUtils.formatPrice(bundle.symbol, m15.ema200),
                            accent = tradingColors.ema200,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceMetricTile(
                            label = "EMA 50 (H1)",
                            value = TimeUtils.formatPrice(bundle.symbol, h1.ema50),
                            accent = tradingColors.ema50,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "EMA 200 (H1)",
                            value = TimeUtils.formatPrice(bundle.symbol, h1.ema200),
                            accent = tradingColors.ema200,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3 & 4. RSI 14 & ATR 14
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "3 & 4. Momentum RSI 14 & Volatilité ATR 14",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val rsiVal = m15.rsi14 ?: 50.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RSI 14 (M15)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = m15.rsi14?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                            style = MonoMediumStyle,
                            color = when {
                                rsiVal > 70 -> tradingColors.bear
                                rsiVal < 30 -> tradingColors.bull
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (rsiVal / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Survente (<30)", style = MaterialTheme.typography.labelSmall, color = tradingColors.bull)
                        Text("Neutre (50)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Surachat (>70)", style = MaterialTheme.typography.labelSmall, color = tradingColors.bear)
                    }

                    HorizontalDivider(color = tradingColors.border)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceMetricTile(
                            label = "ATR 14 (M15)",
                            value = m15.atr14Pips?.let { "${String.format(Locale.US, "%.1f", it)} pips" } ?: "—",
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "ATR 14 (H1)",
                            value = h1.atr14Pips?.let { "${String.format(Locale.US, "%.1f", it)} pips" } ?: "—",
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Supports & Résistances
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "5. Supports et Résistances clés (M15)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (m15.resistances.isEmpty() && m15.supports.isEmpty()) {
                        Text(
                            text = "Aucune zone S/R détectée pour le moment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        m15.resistances.forEach { zone ->
                            ZoneRow(
                                label = "Résistance (${zone.touches}x)",
                                price = TimeUtils.formatPrice(bundle.symbol, zone.price),
                                distance = "+${String.format(Locale.US, "%.1f", zone.distancePips)} pips",
                                isSupport = false
                            )
                        }
                        m15.supports.forEach { zone ->
                            ZoneRow(
                                label = "Support (${zone.touches}x)",
                                price = TimeUtils.formatPrice(bundle.symbol, zone.price),
                                distance = "-${String.format(Locale.US, "%.1f", zone.distancePips)} pips",
                                isSupport = true
                            )
                        }
                    }
                }
            }
        }

        // 6, 7 & 8. Structure du marché, Confirmation H1, Scoring M15
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "6, 7 & 8. Structure & Confirmation H1 / M15",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceMetricTile(
                            label = "Structure M15",
                            value = when (m15.structure) {
                                MarketStructure.BULLISH -> "Haussière (HH/HL)"
                                MarketStructure.BEARISH -> "Baissière (LH/LL)"
                                MarketStructure.RANGING -> "Sans direction"
                            },
                            accent = when (m15.structure) {
                                MarketStructure.BULLISH -> tradingColors.bull
                                MarketStructure.BEARISH -> tradingColors.bear
                                MarketStructure.RANGING -> tradingColors.pending
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "Confirmation H1",
                            value = when (signal.h1TrendState) {
                                H1TrendState.BULLISH -> "Haussière"
                                H1TrendState.BEARISH -> "Baissière"
                                H1TrendState.NEUTRAL -> "Neutre"
                            },
                            accent = when (signal.h1TrendState) {
                                H1TrendState.BULLISH -> tradingColors.bull
                                H1TrendState.BEARISH -> tradingColors.bear
                                H1TrendState.NEUTRAL -> tradingColors.pending
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (signal.criteriaBreakdown.isNotEmpty()) {
                        HorizontalDivider(color = tradingColors.border)
                        Text(
                            text = "Détail du score technique (${signal.technicalScore}/100)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        signal.criteriaBreakdown.forEach { criterion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (criterion.passed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (criterion.passed) tradingColors.bull else tradingColors.pending,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = criterion.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${criterion.earnedPoints}/${criterion.maxPoints}",
                                            style = MonoSmallStyle,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = criterion.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(
    label: String,
    price: String,
    distance: String,
    isSupport: Boolean
) {
    val tradingColors = LocalTradingColors.current
    val color = if (isSupport) tradingColors.bull else tradingColors.bear
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(text = price, style = MonoSmallStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(text = distance, style = MonoSmallStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
