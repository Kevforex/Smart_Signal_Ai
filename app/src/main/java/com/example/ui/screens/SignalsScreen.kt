package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.repository.MarketAnalysisBundle
import com.example.domain.model.RiskCalculation
import com.example.domain.model.SignalStatus
import com.example.domain.model.UserSettings
import com.example.ui.components.ConnectionFreshnessBar
import com.example.ui.components.RiskCalculatorCard
import com.example.ui.components.SignalDetailCard
import com.example.ui.components.WeltradeNoticeBanner
import com.example.ui.theme.LocalTradingColors

@Composable
fun SignalsScreen(
    bundle: MarketAnalysisBundle,
    isRefreshing: Boolean,
    settings: UserSettings,
    entryInput: String,
    slInput: String,
    riskCalculation: RiskCalculation?,
    onRefresh: () -> Unit,
    onUpdateRisk: (Double, Double) -> Unit,
    onUpdateEntrySl: (String, String) -> Unit,
    onSyncSignalLevels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tradingColors = LocalTradingColors.current
    val signal = bundle.activeSignal

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Signaux BUY et SELL",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Priorité à la fiabilité : seuls les setups H1 + M15 clôturés avec score ≥ 80/100 sont confirmés (70–79 = en attente, < 70 = NO TRADE).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ConnectionFreshnessBar(
                symbol = bundle.symbol,
                quote = bundle.quote,
                freshness = bundle.freshnessStatus,
                isRefreshing = isRefreshing,
                lastFetchedMs = bundle.fetchedAtMs,
                onRefresh = onRefresh
            )
        }

        // Status distinction banner: Confirmed vs Pending vs NO TRADE
        item {
            val (statusTitle, statusDesc, badgeColor) = when (signal.status) {
                SignalStatus.CONFIRMED -> Triple(
                    "Statut : Signal Confirmé (Score ≥ 80/100)",
                    "Généré sur bougie M15 clôturée avec tendance H1 alignée et données fraîches.",
                    tradingColors.bull
                )
                SignalStatus.PENDING -> Triple(
                    "Statut : Signal En Attente (Score 70–79/100)",
                    "Configuration moyenne détectée. Attendez une confirmation supplémentaire ou une clôture plus nette avant toute décision.",
                    tradingColors.pending
                )
                SignalStatus.NO_TRADE -> Triple(
                    "Statut : NO TRADE",
                    "Les critères stricts ne sont pas réunis (marché sans direction, contradiction H1/M15, score < 70 ou données périmées).",
                    tradingColors.mutedText
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = badgeColor
                    )
                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SignalDetailCard(signal = signal)
        }

        item {
            RiskCalculatorCard(
                symbol = bundle.symbol,
                capital = settings.accountCapital,
                riskPercent = settings.riskPercent,
                spreadPips = settings.spreadPips,
                entryInput = entryInput,
                slInput = slInput,
                onCapitalChange = { cap -> onUpdateRisk(cap, settings.riskPercent) },
                onRiskChange = { pct -> onUpdateRisk(settings.accountCapital, pct) },
                onEntrySlChange = onUpdateEntrySl,
                calculation = riskCalculation,
                onSyncSignalLevels = if (signal.entryPrice != null && signal.stopLoss != null) onSyncSignalLevels else null
            )
        }

        // Scoring Legend
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, tradingColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Barème du score technique (/100)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ScoreLegendRow("90 – 100", "Excellente configuration (Confirmé)", tradingColors.bull)
                    ScoreLegendRow("80 – 89", "Bonne configuration (Confirmé)", MaterialTheme.colorScheme.primary)
                    ScoreLegendRow("70 – 79", "Configuration moyenne (En attente)", tradingColors.pending)
                    ScoreLegendRow("0 – 69", "Aucun signal (NO TRADE)", tradingColors.mutedText)
                }
            }
        }

        item {
            WeltradeNoticeBanner()
        }
    }
}

@Composable
private fun ScoreLegendRow(range: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = range, style = MaterialTheme.typography.labelLarge, color = color)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
