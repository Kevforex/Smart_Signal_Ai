package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.Timeframe
import com.example.ui.components.CandlestickChart
import com.example.ui.components.PriceMetricTile
import com.example.ui.components.WeltradeNoticeBanner
import com.example.ui.theme.LocalTradingColors
import com.example.ui.viewmodel.ChartUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartScreen(
    bundle: MarketAnalysisBundle,
    chartState: ChartUiState,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleEma50: () -> Unit,
    onToggleEma200: () -> Unit,
    onToggleSupportResistance: () -> Unit,
    onToggleSignalLevels: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val activeCandles = if (chartState.selectedTimeframe == Timeframe.M15) {
        bundle.m15AllCandles
    } else {
        bundle.h1AllCandles
    }
    val activeSnapshot = if (chartState.selectedTimeframe == Timeframe.M15) {
        bundle.m15Snapshot
    } else {
        bundle.h1Snapshot
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Graphique ${bundle.symbol}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${activeSnapshot.closedCandleCount} bougies clôturées analysées • Glisser/toucher pour inspecter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Timeframe.entries.forEachIndexed { index, tf ->
                    SegmentedButton(
                        selected = chartState.selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Timeframe.entries.size),
                        modifier = Modifier.testTag("chart_tf_${tf.label}")
                    ) {
                        val suffix = if (tf == Timeframe.M15) " (Principal)" else " (Tendance)"
                        Text("${tf.label}$suffix")
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = chartState.showEma50,
                    onClick = onToggleEma50,
                    label = { Text("EMA 50") }
                )
                FilterChip(
                    selected = chartState.showEma200,
                    onClick = onToggleEma200,
                    label = { Text("EMA 200") }
                )
                FilterChip(
                    selected = chartState.showSupportResistance,
                    onClick = onToggleSupportResistance,
                    label = { Text("Supports / Résistances") }
                )
                FilterChip(
                    selected = chartState.showSignalLevels,
                    onClick = onToggleSignalLevels,
                    label = { Text("Niveaux Signal") }
                )
            }
        }

        item {
            CandlestickChart(
                symbol = bundle.symbol,
                candles = activeCandles,
                snapshot = activeSnapshot,
                activeSignal = bundle.activeSignal,
                showEma50 = chartState.showEma50,
                showEma200 = chartState.showEma200,
                showSupportResistance = chartState.showSupportResistance,
                showSignalLevels = chartState.showSignalLevels
            )
        }

        // Indicator Legend & Quick Readout
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceMetricTile(
                    label = "EMA 50 (${chartState.selectedTimeframe.label})",
                    value = TimeUtils.formatPrice(bundle.symbol, activeSnapshot.ema50),
                    accent = tradingColors.ema50,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "EMA 200 (${chartState.selectedTimeframe.label})",
                    value = TimeUtils.formatPrice(bundle.symbol, activeSnapshot.ema200),
                    accent = tradingColors.ema200,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "RSI 14",
                    value = activeSnapshot.rsi14?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeSnapshot.structure.labelFr,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onNavigateToAnalysis,
                        modifier = Modifier.testTag("chart_open_analysis_button")
                    ) {
                        Text("Analyse complète")
                    }
                }
            }
        }

        item {
            WeltradeNoticeBanner()
        }
    }
}
