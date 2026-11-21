package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.H1TrendState
import com.example.domain.model.SignalDirection
import com.example.ui.components.ConnectionFreshnessBar
import com.example.ui.components.PriceMetricTile
import com.example.ui.components.SignalDetailCard
import com.example.ui.components.WeltradeNoticeBanner
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoLargeStyle
import com.example.ui.theme.MonoMediumStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    bundle: MarketAnalysisBundle,
    isRefreshing: Boolean,
    recordedSignalsCount: Int,
    onRefresh: () -> Unit,
    onNavigateToChart: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToSignals: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBacktest: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val quote = bundle.quote
    val pip = TimeUtils.pipSize(bundle.symbol)
    val spreadPips = quote?.let { it.spread / pip }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ConnectionFreshnessBar(
                symbol = bundle.symbol,
                quote = quote,
                freshness = bundle.freshnessStatus,
                isRefreshing = isRefreshing,
                lastFetchedMs = bundle.fetchedAtMs,
                onRefresh = onRefresh
            )
        }

        // Live Quote Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${bundle.symbol} • M15 / H1",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = quote?.description ?: "Euro / Dollar américain",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (quote != null) {
                            val diffColor = if (quote.dayDiffPercent >= 0) tradingColors.bull else tradingColors.bear
                            val diffBg = if (quote.dayDiffPercent >= 0) tradingColors.bullContainer else tradingColors.bearContainer
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = diffBg
                            ) {
                                val sign = if (quote.dayDiffPercent >= 0) "+" else ""
                                Text(
                                    text = "$sign${String.format(Locale.US, "%.2f", quote.dayDiffPercent)}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = diffColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (quote != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Prix Bid (Biquote)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = TimeUtils.formatPrice(bundle.symbol, quote.bid),
                                    style = MonoLargeStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Ask : ${TimeUtils.formatPrice(bundle.symbol, quote.ask)}",
                                    style = MonoMediumStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Spread : ${String.format(Locale.US, "%.1f", spreadPips ?: 0.0)} pips",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PriceMetricTile(
                                label = "Plus Haut 24h",
                                value = TimeUtils.formatPrice(bundle.symbol, quote.high),
                                accent = tradingColors.bull,
                                modifier = Modifier.weight(1f)
                            )
                            PriceMetricTile(
                                label = "Plus Bas 24h",
                                value = TimeUtils.formatPrice(bundle.symbol, quote.low),
                                accent = tradingColors.bear,
                                modifier = Modifier.weight(1f)
                            )
                            PriceMetricTile(
                                label = "Tendance H1",
                                value = when (bundle.activeSignal.h1TrendState) {
                                    H1TrendState.BULLISH -> "Haussière"
                                    H1TrendState.BEARISH -> "Baissière"
                                    H1TrendState.NEUTRAL -> "Neutre"
                                },
                                accent = when (bundle.activeSignal.h1TrendState) {
                                    H1TrendState.BULLISH -> tradingColors.bull
                                    H1TrendState.BEARISH -> tradingColors.bear
                                    H1TrendState.NEUTRAL -> tradingColors.pending
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            text = "Cotation en direct indisponible — aucune donnée inventée.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tradingColors.pending
                        )
                    }
                }
            }
        }

        // Active Signal Summary Card
        item {
            SignalDetailCard(
                signal = bundle.activeSignal,
                modifier = Modifier
                    .clickable(onClick = onNavigateToSignals)
                    .testTag("dashboard_signal_card")
            )
        }

        // Quick Technical Snapshot Row
        item {
            val m15 = bundle.m15Snapshot
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToAnalysis)
                    .testTag("dashboard_tech_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Synthèse technique M15 (bougies clôturées)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Ouvrir analyse technique",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PriceMetricTile(
                            label = "EMA 50",
                            value = TimeUtils.formatPrice(bundle.symbol, m15.ema50),
                            accent = tradingColors.ema50,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "EMA 200",
                            value = TimeUtils.formatPrice(bundle.symbol, m15.ema200),
                            accent = tradingColors.ema200,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "RSI 14",
                            value = m15.rsi14?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        PriceMetricTile(
                            label = "ATR 14",
                            value = m15.atr14Pips?.let { "${String.format(Locale.US, "%.1f", it)}p" } ?: "—",
                            accent = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Navigation Grid to all modules
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Accès rapide aux modules",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "Graphique ${bundle.symbol}",
                        subtitle = "M15 & H1 + EMA",
                        icon = Icons.Default.CandlestickChart,
                        onClick = onNavigateToChart,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_chart_tile")
                    )
                    QuickActionTile(
                        title = "Analyse technique",
                        subtitle = "8 critères détaillés",
                        icon = Icons.Default.Analytics,
                        onClick = onNavigateToAnalysis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_analysis_tile")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "Signaux & Risque",
                        subtitle = when (bundle.activeSignal.direction) {
                            SignalDirection.NO_TRADE -> "État NO TRADE"
                            else -> "${bundle.activeSignal.direction.labelFr} • ${bundle.activeSignal.technicalScore}/100"
                        },
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        onClick = onNavigateToSignals,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_signals_tile")
                    )
                    QuickActionTile(
                        title = "Historique",
                        subtitle = "$recordedSignalsCount signal(aux) archivé(s)",
                        icon = Icons.Default.History,
                        onClick = onNavigateToHistory,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_history_tile")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "Backtest réel",
                        subtitle = "${bundle.m15ClosedCandles.size} bougies M15",
                        icon = Icons.Default.Assessment,
                        onClick = onNavigateToBacktest,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_backtest_tile")
                    )
                    QuickActionTile(
                        title = "Paramètres",
                        subtitle = "Risque, coûts, API",
                        icon = Icons.Default.Settings,
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_settings_tile")
                    )
                }
            }
        }

        item {
            WeltradeNoticeBanner()
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
