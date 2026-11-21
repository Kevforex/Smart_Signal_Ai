package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.BacktestConfig
import com.example.domain.model.BacktestResult
import com.example.domain.model.BacktestTrade
import com.example.domain.model.SignalDirection
import com.example.ui.components.DecimalNumberField
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.PriceMetricTile
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoSmallStyle
import java.util.Locale

@Composable
fun BacktestScreen(
    bundle: MarketAnalysisBundle,
    config: BacktestConfig,
    result: BacktestResult?,
    isRunning: Boolean,
    onUpdateConfig: (
        minScore: Int,
        spreadPips: Double,
        commissionPips: Double,
        initialCapital: Double,
        riskPercent: Double,
        targetTp: BacktestConfig.BacktestTarget
    ) -> Unit,
    onRunBacktest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Backtest historique ${bundle.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Simulation réelle pas à pas sur ${bundle.m15ClosedCandles.size} bougies M15 et ${bundle.h1ClosedCandles.size} bougies H1 clôturées Biquote.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Configuration Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Paramètres du backtest",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DecimalNumberField(
                            value = config.initialCapital,
                            onValueCommitted = { cap ->
                                onUpdateConfig(config.minScore, config.spreadPips, config.commissionPips, cap.coerceAtLeast(10.0), config.riskPercent, config.targetTp)
                            },
                            label = "Capital initial",
                            isIntegerHint = true,
                            modifier = Modifier.weight(1f)
                        )
                        DecimalNumberField(
                            value = config.riskPercent,
                            onValueCommitted = { r ->
                                onUpdateConfig(config.minScore, config.spreadPips, config.commissionPips, config.initialCapital, r.coerceIn(0.1, 10.0), config.targetTp)
                            },
                            label = "Risque (%)",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DecimalNumberField(
                            value = config.spreadPips,
                            onValueCommitted = { sp ->
                                onUpdateConfig(config.minScore, sp.coerceIn(0.0, 20.0), config.commissionPips, config.initialCapital, config.riskPercent, config.targetTp)
                            },
                            label = "Spread (pips)",
                            modifier = Modifier.weight(1f)
                        )
                        DecimalNumberField(
                            value = config.commissionPips,
                            onValueCommitted = { com ->
                                onUpdateConfig(config.minScore, config.spreadPips, com.coerceIn(0.0, 20.0), config.initialCapital, config.riskPercent, config.targetTp)
                            },
                            label = "Comm. (pips)",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Score minimum requis",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(70, 80, 90).forEach { scoreOpt ->
                            FilterChip(
                                selected = config.minScore == scoreOpt,
                                onClick = {
                                    onUpdateConfig(scoreOpt, config.spreadPips, config.commissionPips, config.initialCapital, config.riskPercent, config.targetTp)
                                },
                                label = { Text("≥ $scoreOpt/100") }
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        BacktestConfig.BacktestTarget.entries.forEachIndexed { idx, tgt ->
                            SegmentedButton(
                                selected = config.targetTp == tgt,
                                onClick = {
                                    onUpdateConfig(config.minScore, config.spreadPips, config.commissionPips, config.initialCapital, config.riskPercent, tgt)
                                },
                                shape = SegmentedButtonDefaults.itemShape(idx, BacktestConfig.BacktestTarget.entries.size)
                            ) {
                                Text(tgt.name)
                            }
                        }
                    }

                    Button(
                        onClick = onRunBacktest,
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_backtest_button")
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calcul historique en cours...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exécuter le backtest sur données réelles")
                        }
                    }
                }
            }
        }

        if (result != null) {
            if (result.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tradingColors.bearContainer,
                        border = BorderStroke(1.dp, tradingColors.bear)
                    ) {
                        Text(
                            text = result.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                item {
                    EquityCurveChart(
                        equityCurve = result.equityCurve,
                        initialCapital = result.config.initialCapital
                    )
                }

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
                                text = "Résultats du backtest (après coûts estimés)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Période : ${TimeUtils.formatShortTime(TimeUtils.parseIsoUtcToMillis(result.startDateIso))} → ${TimeUtils.formatShortTime(TimeUtils.parseIsoUtcToMillis(result.endDateIso))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PriceMetricTile(
                                    label = "Total transactions",
                                    value = "${result.totalTrades}",
                                    accent = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backtest_total_trades")
                                )
                                PriceMetricTile(
                                    label = "Taux de réussite",
                                    value = "${String.format(Locale.FRANCE, "%.1f", result.winRatePercent)} %",
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backtest_win_rate")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PriceMetricTile(
                                    label = "Gains",
                                    value = "${result.winningTrades} gagnante(s)",
                                    accent = tradingColors.bull,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backtest_wins_count")
                                )
                                PriceMetricTile(
                                    label = "Pertes",
                                    value = "${result.losingTrades} perdante(s)",
                                    accent = tradingColors.bear,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backtest_losses_count")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val pfText = if (result.profitFactor.isInfinite()) {
                                    "∞"
                                } else {
                                    String.format(Locale.FRANCE, "%.2f", result.profitFactor)
                                }
                                PriceMetricTile(
                                    label = "Profit Factor",
                                    value = pfText,
                                    accent = if (result.profitFactor.isInfinite() || result.profitFactor >= 1.0) tradingColors.bull else tradingColors.bear,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backtest_profit_factor")
                                )
                                PriceMetricTile(
                                    label = "Drawdown max",
                                    value = "${String.format(Locale.FRANCE, "%.2f", result.maxDrawdownPercent)} % (-${String.format(Locale.FRANCE, "%.2f", result.maxDrawdownCurrency)})",
                                    accent = tradingColors.bear,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PriceMetricTile(
                                    label = "Gains bruts",
                                    value = "+${String.format(Locale.FRANCE, "%.2f", result.grossProfitCurrency)}",
                                    accent = tradingColors.bull,
                                    modifier = Modifier.weight(1f)
                                )
                                PriceMetricTile(
                                    label = "Pertes brutes",
                                    value = "-${String.format(Locale.FRANCE, "%.2f", result.grossLossCurrency)}",
                                    accent = tradingColors.bear,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PriceMetricTile(
                                    label = "Coûts estimés",
                                    value = "-${String.format(Locale.FRANCE, "%.2f", result.totalEstimatedCostsCurrency)}",
                                    accent = tradingColors.pending,
                                    modifier = Modifier.weight(1f)
                                )
                                val netSign = if (result.netProfitCurrency >= 0) "+" else ""
                                PriceMetricTile(
                                    label = "Résultat net final",
                                    value = "$netSign${String.format(Locale.FRANCE, "%.2f", result.netProfitCurrency)} ($netSign${String.format(Locale.FRANCE, "%.2f", result.returnPercent)} %)",
                                    accent = if (result.netProfitCurrency >= 0) tradingColors.bull else tradingColors.bear,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "Les performances passées simulées sur historique Biquote ne garantissent aucun résultat futur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (result.trades.isNotEmpty()) {
                    item {
                        Text(
                            text = "Journal des transactions simulées (${result.trades.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(result.trades) { trade ->
                        BacktestTradeCard(symbol = bundle.symbol, trade = trade)
                    }
                }
            }
        }
    }
}

@Composable
private fun BacktestTradeCard(symbol: String, trade: BacktestTrade) {
    val tradingColors = LocalTradingColors.current
    val pnlColor = if (trade.isWin) tradingColors.bull else tradingColors.bear
    val dirColor = if (trade.direction == SignalDirection.BUY) tradingColors.bull else tradingColors.bear

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tradingColors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${trade.direction.labelFr} • Score ${trade.score}/100 • ${trade.exitReason}",
                    style = MaterialTheme.typography.labelLarge,
                    color = dirColor
                )
                val sign = if (trade.netPnLCurrency >= 0) "+" else ""
                Text(
                    text = "$sign${String.format(Locale.FRANCE, "%.2f", trade.netPnLCurrency)} ($sign${String.format(Locale.FRANCE, "%.1f", trade.netPips)}p)",
                    style = MonoSmallStyle,
                    color = pnlColor
                )
            }
            HorizontalDivider(color = tradingColors.border)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entrée ${TimeUtils.formatPrice(symbol, trade.entryPrice)} → Sortie ${TimeUtils.formatPrice(symbol, trade.exitPrice)}",
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TimeUtils.formatShortTime(TimeUtils.parseIsoUtcToMillis(trade.entryTimeIso)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
