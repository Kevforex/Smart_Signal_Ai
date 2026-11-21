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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.SignalHistoryEntity
import com.example.domain.analysis.TimeUtils
import com.example.domain.model.SignalOutcome
import com.example.domain.model.SignalStatus
import com.example.ui.components.PriceMetricTile
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.MonoSmallStyle
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("Tous"),
    CONFIRMED("Confirmés"),
    PENDING("En attente"),
    BUY("BUY"),
    SELL("SELL")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    signals: List<SignalHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filtered = remember(signals, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> signals
            HistoryFilter.CONFIRMED -> signals.filter { it.status == SignalStatus.CONFIRMED.name }
            HistoryFilter.PENDING -> signals.filter { it.status == SignalStatus.PENDING.name }
            HistoryFilter.BUY -> signals.filter { it.direction == "BUY" }
            HistoryFilter.SELL -> signals.filter { it.direction == "SELL" }
        }
    }

    val tpHits = signals.count {
        it.outcome == SignalOutcome.TP1_ATTEINT.name || it.outcome == SignalOutcome.TP2_ATTEINT.name
    }
    val slHits = signals.count { it.outcome == SignalOutcome.SL_ATTEINT.name }
    val openCount = signals.count { it.outcome == SignalOutcome.EN_COURS.name }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Historique immuable des signaux",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Chaque signal archivé reste strictement figé — seul son résultat observé sur le marché est suivi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceMetricTile(
                    label = "Total archivés",
                    value = "${signals.size}",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "TP atteints",
                    value = "$tpHits",
                    accent = tradingColors.bull,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "SL atteints",
                    value = "$slHits",
                    accent = tradingColors.bear,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "En cours",
                    value = "$openCount",
                    accent = tradingColors.pending,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, tradingColors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Aucun signal enregistré dans cette catégorie",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Les signaux confirmés ou en attente générés à partir des bougies clôturées Biquote sont automatiquement archivés ici sans aucune modification ultérieure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                HistorySignalCard(entity = item)
            }
        }
    }
}

@Composable
private fun HistorySignalCard(entity: SignalHistoryEntity) {
    val tradingColors = LocalTradingColors.current
    val dirColor = if (entity.direction == "BUY") tradingColors.bull else tradingColors.bear
    val statusLabel = if (entity.status == SignalStatus.CONFIRMED.name) "CONFIRMÉ" else "EN ATTENTE"
    val outcomeEnum = SignalOutcome.entries.firstOrNull { it.name == entity.outcome } ?: SignalOutcome.EN_COURS
    val outcomeColor = when (outcomeEnum) {
        SignalOutcome.TP1_ATTEINT, SignalOutcome.TP2_ATTEINT -> tradingColors.bull
        SignalOutcome.SL_ATTEINT -> tradingColors.bear
        SignalOutcome.EN_COURS -> tradingColors.pending
        SignalOutcome.EXPIRE -> tradingColors.mutedText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${entity.direction} ${entity.symbol}",
                        style = MaterialTheme.typography.titleMedium,
                        color = dirColor
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = outcomeColor.copy(alpha = 0.16f)
                ) {
                    val pipsStr = if (outcomeEnum != SignalOutcome.EN_COURS) {
                        val sign = if (entity.outcomePips >= 0) "+" else ""
                        " ($sign${String.format(Locale.US, "%.1f", entity.outcomePips)}p)"
                    } else {
                        ""
                    }
                    Text(
                        text = "${outcomeEnum.labelFr}$pipsStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = outcomeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Score : ${entity.technicalScore}/100 • H1 : ${entity.h1TrendState}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TimeUtils.formatShortTime(entity.signalTimestampMs),
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = tradingColors.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PriceMetricTile(
                    label = "Entrée",
                    value = TimeUtils.formatPrice(entity.symbol, entity.entryPrice),
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "SL",
                    value = TimeUtils.formatPrice(entity.symbol, entity.stopLoss),
                    accent = tradingColors.bear,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "TP1",
                    value = TimeUtils.formatPrice(entity.symbol, entity.takeProfit1),
                    accent = tradingColors.bull,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricTile(
                    label = "TP2",
                    value = TimeUtils.formatPrice(entity.symbol, entity.takeProfit2),
                    accent = tradingColors.bull,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
